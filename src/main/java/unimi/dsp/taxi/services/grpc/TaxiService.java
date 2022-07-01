package unimi.dsp.taxi.services.grpc;

import com.google.protobuf.Empty;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.RideElectionInfo;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.taxi.NetworkTaxiConnection;
import unimi.dsp.taxi.Taxi;
import unimi.dsp.taxi.TaxiServiceGrpc;
import unimi.dsp.taxi.TaxiServiceOuterClass;

import java.util.Map;

public class TaxiService extends TaxiServiceGrpc.TaxiServiceImplBase {
    private static final Logger logger = LogManager.getLogger(TaxiService.class.getName());

    private final Taxi taxi;

    public TaxiService(Taxi taxi) {
        this.taxi = taxi;
    }

    // the remote taxi presents to this taxi, and receives the coordinate of this taxi
    @Override
    public void addTaxi(TaxiServiceOuterClass.TaxiAddRequest request,
                        StreamObserver<TaxiServiceOuterClass.TaxiAddResponse> responseObserver) {
        NetworkTaxiConnection taxiConnection = new NetworkTaxiConnection(this.taxi,
                new TaxiInfoDto(request.getId(), request.getIpAddress(), request.getPort())
        );
        taxiConnection.setRemoteTaxiDistrict(
                District.fromPosition(new SmartCityPosition(
                        request.getX(), request.getY()
                )));

        synchronized (this.taxi.getNetworkTaxiConnections()) {
            this.taxi.getNetworkTaxiConnections().put(request.getId(), taxiConnection);
        }

        responseObserver.onNext(
                TaxiServiceOuterClass.TaxiAddResponse.newBuilder()
                        .setX(this.taxi.getX())
                        .setY(this.taxi.getY())
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void removeTaxi(TaxiServiceOuterClass.TaxiRemoveRequest request, StreamObserver<Empty> responseObserver) {
        synchronized (this.taxi.getNetworkTaxiConnections()) {
            this.taxi.getNetworkTaxiConnections().remove(request.getId());
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void changeRemoteTaxiDistrict(TaxiServiceOuterClass.TaxiNewDistrictRequest request,
                                         StreamObserver<Empty> responseObserver) {
        District remoteDistrict = District.fromPosition(
                new SmartCityPosition(request.getNewX(), request.getNewY()));
        synchronized (this.taxi.getNetworkTaxiConnections()) {
            if (this.taxi.getNetworkTaxiConnections().containsKey(request.getId()))
                this.taxi.getNetworkTaxiConnections().get(request.getId())
                        .setRemoteTaxiDistrict(remoteDistrict);
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

//    private void restartElectionsIfNecessary(int removedTaxiId) {
//        Map<RideRequestDto, RideElectionInfo> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
//        synchronized (rideRequestsMap) {
//            for (Map.Entry<RideRequestDto, RideElectionInfo> entry : rideRequestsMap.entrySet()) {
//                if (entry.getValue().getRideElectionId().getTaxiId() == removedTaxiId)
//                    this.taxi.forwardRideElectionIdIfNecessary(
//                            entry.getKey(), entry.getValue().getRideElectionId());
//            }
//        }
//    }

    @Override
    public void forwardElectionIdOrTakeRide(TaxiServiceOuterClass.RideElectionIdRequest request,
                                 StreamObserver<TaxiServiceOuterClass.ForwardElectionIdResponse> responseObserver) {
        RideRequestDto rideRequest = new RideRequestDto(request.getRideRequestId(),
                new SmartCityPosition(request.getStartX(), request.getStartY()),
                new SmartCityPosition(request.getEndX(), request.getEndY()));
        RideElectionInfo.RideElectionId receivedElectionId = new RideElectionInfo.RideElectionId(
                request.getTaxiId(), request.getDistanceFromSP(), request.getBatteryLevel());

        // if the district is different from the position of the ride request, I do not want to involve
        // other taxis in an election of another district
        boolean retry = !District.fromPosition(rideRequest.getStart()).equals(this.taxi.getDistrict());
        responseObserver.onNext(TaxiServiceOuterClass.ForwardElectionIdResponse.newBuilder()
                .setRetry(retry)
                .build());
        responseObserver.onCompleted();

        if (retry) return;
        // I cannot send an async grpc message because that thread is deleted once i send the response
        // https://stackoverflow.com/questions/57110811/grpc-random-cancelled-exception-on-rpc-calls
        Context ctx = Context.current().fork();
        ctx.run(() -> {
            Map<RideRequestDto, RideElectionInfo> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
            // TODO: check synchronization if necessary this big
            synchronized (rideRequestsMap) {
                if (rideRequestsMap.containsKey(rideRequest) &&
                        rideRequestsMap.get(rideRequest).getRideElectionState()
                                .equals(RideElectionInfo.RideElectionState.ELECTED))
                    return;

                synchronized (this.taxi) {
                    if (this.taxi.getStatus().equals(Taxi.TaxiStatus.AVAILABLE)) {
                        synchronized (this.taxi.getRideRequestElectionsMap()) {
                            if (rideRequestsMap.containsKey(rideRequest)) {
                                electionLogicWhenAlreadyParticipant(rideRequest, receivedElectionId);
                            } else {
                                electionLogicWhenNotParticipant(rideRequest, receivedElectionId);
                            }
                        }
                    } else {
                        electionLogicWhenUnavailable(rideRequest, receivedElectionId);
                    }
                }
            }
        });
    }

    private void electionLogicWhenNotParticipant(RideRequestDto rideRequest,
                                                 RideElectionInfo.RideElectionId receivedElectionId) {
        assert Thread.holdsLock(this.taxi.getRideRequestElectionsMap()) && Thread.holdsLock(this.taxi);
        // not a participant yet, forward the received request or the current election info
        // based on the greater id
        RideElectionInfo.RideElectionId thisRideElectionId = this.createElectionIdFromRideRequest(rideRequest);
        // it cannot be equal, otherwise there entry would exist already
        RideElectionInfo.RideElectionId winningRideElectionId =
                thisRideElectionId.isGreaterThan(receivedElectionId) ?
                        thisRideElectionId : receivedElectionId;

        this.taxi.getRideRequestElectionsMap().put(rideRequest, new RideElectionInfo(
                winningRideElectionId, RideElectionInfo.RideElectionState.ELECTION
        ));
        this.taxi.handleRideElectionId(rideRequest, winningRideElectionId, true);
    }

    private void electionLogicWhenAlreadyParticipant(RideRequestDto rideRequest,
                                                     RideElectionInfo.RideElectionId receivedElectionId) {
        assert Thread.holdsLock(this.taxi.getRideRequestElectionsMap()) && Thread.holdsLock(this.taxi);

        RideElectionInfo rideElectionInfo = this.taxi.getRideRequestElectionsMap().get(rideRequest);
        // forward if the election info id is greater
        if (receivedElectionId.isGreaterThan(rideElectionInfo.getRideElectionId())) {
            rideElectionInfo.setRideElectionId(receivedElectionId);
            this.taxi.handleRideElectionId(rideRequest, receivedElectionId, false);
        } else if (receivedElectionId.equals(rideElectionInfo.getRideElectionId())) {
            // take the ride if the received election id is the same as the stored on and the taxi id
            // is the same too
            if (rideElectionInfo.getRideElectionId().getTaxiId() == this.taxi.getId())
                this.taxi.takeRideIfPossible(rideRequest);
            else
                // otherwise restart the election because the token already completed the ring
                // but no taxi started the elected phase
                this.taxi.handleRideElectionId(rideRequest,
                        createElectionIdFromRideRequest(rideRequest), true);
        }
    }

    private RideElectionInfo.RideElectionId createElectionIdFromRideRequest(RideRequestDto rideRequest) {
        return new RideElectionInfo.RideElectionId(
                this.taxi.getId(),
                this.taxi.getDistanceFromPosition(rideRequest.getStart()),
                this.taxi.getBatteryLevel());
    }

    private void electionLogicWhenUnavailable(RideRequestDto rideRequest,
                                              RideElectionInfo.RideElectionId receivedElectionId) {
        // when unavailable, I just forward the request to the next taxi
        this.taxi.handleRideElectionId(rideRequest, receivedElectionId, false);
    }

    @Override
    public void markElectionConfirmed(TaxiServiceOuterClass.RideElectionConfirmRequest request,
                                      StreamObserver<Empty> responseObserver) {
        int rideRequestId = request.getRideRequestId();
        Map<RideRequestDto, RideElectionInfo> rideRequestsMap = this.taxi.getRideRequestElectionsMap();

        // TODO: big sync block
        synchronized (rideRequestsMap) {
            // I create a fake ride request containing the ride request only because I just need the id
            // in order to avoid re-election of already confirmed ride requests
            rideRequestsMap.put(new RideRequestDto(rideRequestId, new SmartCityPosition(0, 0),
                            new SmartCityPosition(0, 0)),
                    new RideElectionInfo(null, RideElectionInfo.RideElectionState.ELECTED));
            // for all the other elections, if the taxi winning the election is the greater id in a current
            // election, then that election is restarted
            for (Map.Entry<RideRequestDto, RideElectionInfo> rideElectionEntry :
                    rideRequestsMap.entrySet()) {
                if (!rideElectionEntry.getValue().getRideElectionState()
                        .equals(RideElectionInfo.RideElectionState.ELECTED) &&
                        rideElectionEntry.getValue().getRideElectionId().getTaxiId() == request.getTaxiId()) {
                    RideElectionInfo.RideElectionId newRideElectionId = createElectionIdFromRideRequest(
                            rideElectionEntry.getKey());
                    rideElectionEntry.getValue().setRideElectionId(newRideElectionId);
                    this.taxi.handleRideElectionId(rideElectionEntry.getKey(), newRideElectionId, true);
                    logger.info("Election for ride {} is restarted by taxi {}",
                            rideRequestId, this.taxi.getId());
                }
            }
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void askRechargeRequestApproval(TaxiServiceOuterClass.RechargeInfoRequest request,
                                           StreamObserver<TaxiServiceOuterClass.RechargeInfoResponse> responseObserver) {
        synchronized (this.taxi) {
            Taxi.TaxiStatus status = this.taxi.getStatus();
            if (status.equals(Taxi.TaxiStatus.RECHARGING)) {
                responseObserver.onNext(TaxiServiceOuterClass.RechargeInfoResponse.newBuilder()
                        .setOk(false).build());
                responseObserver.onCompleted();
                return;
            }

            if (status.equals(Taxi.TaxiStatus.WAITING_TO_RECHARGE)) {
                long rechargeTs = this.taxi.getLocalRechargeRequestTs();
                // if the ts are equal, the smaller taxi id wins, otherwise the winner is the
                // request with the lower ts
                boolean isRequestConfirmed = request.getRechargeTs() == rechargeTs ?
                        this.taxi.getId() > request.getTaxiId() :
                        rechargeTs > request.getRechargeTs();

                responseObserver.onNext(TaxiServiceOuterClass.RechargeInfoResponse.newBuilder()
                        .setOk(isRequestConfirmed).build());
                responseObserver.onCompleted();
                return;
            }


            // if not recharging or waiting to recharge it is not interested and sends back true
            responseObserver.onNext(TaxiServiceOuterClass.RechargeInfoResponse.newBuilder()
                    .setOk(true).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateRechargeRequestApproval(TaxiServiceOuterClass.RechargeApprovalRequest request,
                                              StreamObserver<Empty> responseObserver) {
        synchronized (this.taxi) {
            if (!this.taxi.getStatus().equals(Taxi.TaxiStatus.WAITING_TO_RECHARGE)) {
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
                return;
            }
        }

        synchronized (this.taxi.getRechargeAwaitingTaxiIds()) {
            this.taxi.getRechargeAwaitingTaxiIds().remove(request.getTaxiId());
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();

        this.taxi.accessTheRechargeStationIfPossible();
    }
}
