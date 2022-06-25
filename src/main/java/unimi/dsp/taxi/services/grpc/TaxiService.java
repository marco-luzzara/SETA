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
import java.util.Optional;

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

        this.taxi.getNetworkTaxiConnections().put(request.getId(), taxiConnection);

        responseObserver.onNext(
                TaxiServiceOuterClass.TaxiAddResponse.newBuilder()
                        .setX(this.taxi.getX())
                        .setY(this.taxi.getY())
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void removeTaxi(TaxiServiceOuterClass.TaxiRemoveRequest request, StreamObserver<Empty> responseObserver) {
        // I cannot send an async grpc message because that thread is deleted once i send the response
        // https://stackoverflow.com/questions/57110811/grpc-random-cancelled-exception-on-rpc-calls
        Context ctx = Context.current().fork();
        ctx.run(() -> {
            this.taxi.getNetworkTaxiConnections().remove(request.getId());
            this.restartElectionsIfNecessary(request.getId());
        });

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public synchronized void changeRemoteTaxiDistrict(TaxiServiceOuterClass.TaxiNewDistrictRequest request,
                                         StreamObserver<Empty> responseObserver) {
        // I cannot send an async grpc message because that thread is deleted once i send the response
        // https://stackoverflow.com/questions/57110811/grpc-random-cancelled-exception-on-rpc-calls
        Context ctx = Context.current().fork();
        ctx.run(() -> {
            District remoteDistrict = District.fromPosition(
                    new SmartCityPosition(request.getNewX(), request.getNewY()));
            this.taxi.getNetworkTaxiConnections().get(request.getId())
                    .setRemoteTaxiDistrict(remoteDistrict);
            restartElectionsIfNecessary(request.getId());
        });

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private void restartElectionsIfNecessary(int removedTaxiId) {
        Map<RideRequestDto, RideElectionInfo> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
        synchronized (rideRequestsMap) {
            for (Map.Entry<RideRequestDto, RideElectionInfo> entry : rideRequestsMap.entrySet()) {
                if (entry.getValue().getRideElectionId().getTaxiId() == removedTaxiId)
                    this.taxi.forwardRideElectionIdIfNecessary(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void forwardElectionIdOrTakeRide(TaxiServiceOuterClass.RideElectionIdRequest request,
                                 StreamObserver<Empty> responseObserver) {
        // I cannot send an async grpc message because that thread is deleted once i send the response
        // https://stackoverflow.com/questions/57110811/grpc-random-cancelled-exception-on-rpc-calls
        Context ctx = Context.current().fork();
        ctx.run(() -> {
            RideRequestDto rideRequest = new RideRequestDto(request.getRideRequestId(),
                    new SmartCityPosition(request.getStartX(), request.getStartY()),
                    new SmartCityPosition(request.getEndX(), request.getEndY()));
            RideElectionInfo.RideElectionId receivedElectionId = new RideElectionInfo.RideElectionId(
                    request.getTaxiId(), request.getDistanceFromSP(), request.getBatteryLevel());

            Map<RideRequestDto, RideElectionInfo> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
            synchronized (rideRequestsMap) {
                if (rideRequestsMap.containsKey(rideRequest) &&
                        rideRequestsMap.get(rideRequest).getRideElectionState()
                                .equals(RideElectionInfo.RideElectionState.ELECTED))
                    return;

                synchronized (this.taxi) {
                    if (this.taxi.getStatus().equals(Taxi.TaxiStatus.AVAILABLE)) {
                        if (rideRequestsMap.containsKey(rideRequest)) {
                            electionLogicWhenAlreadyParticipant(rideRequest, receivedElectionId, rideRequestsMap);
                        } else {
                            electionLogicWhenNotParticipant(rideRequest, receivedElectionId);
                        }
                    } else {
                        electionLogicWhenUnavailable(request, rideRequest, receivedElectionId);
                    }
                }
            }
        });

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private void electionLogicWhenNotParticipant(RideRequestDto rideRequest,
                                                 RideElectionInfo.RideElectionId receivedElectionId) {
        // not a participant yet, forward the received request or the current election info
        // based on the greater id
        RideElectionInfo.RideElectionId thisRideElectionId = new RideElectionInfo.RideElectionId(
                this.taxi.getId(),
                this.taxi.getDistanceFromRideStart(rideRequest),
                this.taxi.getBatteryLevel());
        // it cannot be equal, otherwise there entry would exist already
        RideElectionInfo.RideElectionId winningRideElectionId =
                thisRideElectionId.isGreaterThan(receivedElectionId) ?
                        thisRideElectionId : receivedElectionId;

        this.taxi.getRideRequestElectionsMap().put(rideRequest, new RideElectionInfo(
                winningRideElectionId, RideElectionInfo.RideElectionState.ELECTION
        ));
        this.taxi.getNextDistrictTaxiConnection().ifPresent(conn ->
                conn.sendForwardElectionIdOrTakeRide(rideRequest, winningRideElectionId));
    }

    private void electionLogicWhenAlreadyParticipant(RideRequestDto rideRequest,
                                                     RideElectionInfo.RideElectionId receivedElectionId,
                                                     Map<RideRequestDto, RideElectionInfo> rideRequestsMap) {
        RideElectionInfo rideElectionInfo = rideRequestsMap.get(rideRequest);
        // so forward if the election info id is greater, or take the ride if the token completed the ring
        if (receivedElectionId.isGreaterThan(rideElectionInfo.getRideElectionId())) {
            rideElectionInfo.setRideElectionId(receivedElectionId);
            this.taxi.getNextDistrictTaxiConnection().ifPresent(conn ->
                    conn.sendForwardElectionIdOrTakeRide(rideRequest, receivedElectionId));
        } else if (receivedElectionId.equals(rideElectionInfo.getRideElectionId())) {
            this.taxi.takeRideIfAvailable(rideRequest);
        }
    }

    private void electionLogicWhenUnavailable(TaxiServiceOuterClass.RideElectionIdRequest request,
                                              RideRequestDto rideRequest,
                                              RideElectionInfo.RideElectionId receivedElectionId) {
        // i can forward the request only if the taxi id that started it is present in my
        // taxi network connections and it is not myself the owner of the ride request
        if (this.taxi.getNetworkTaxiConnections().containsKey(request.getTaxiId()) &&
                request.getTaxiId() != this.taxi.getId()) {
            this.taxi.getNextDistrictTaxiConnection().ifPresent(conn ->
                    conn.sendForwardElectionIdOrTakeRide(rideRequest, receivedElectionId));
        }
    }

    @Override
    public void markElectionConfirmed(TaxiServiceOuterClass.RideElectionConfirmRequest request,
                                      StreamObserver<Empty> responseObserver) {
//        // I cannot send an async grpc message because that thread is deleted once i send the response
//        // https://stackoverflow.com/questions/57110811/grpc-random-cancelled-exception-on-rpc-calls
//        Context ctx = Context.current().fork();
//        ctx.run(() -> {
            int rideRequestId = request.getRideRequestId();
            Map<RideRequestDto, RideElectionInfo> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
            synchronized (rideRequestsMap) {
                // I create a fake ride request containing the ride request only because i just need that
                // in order to avoid re-election of already confirmed ride requests
                rideRequestsMap.put(new RideRequestDto(rideRequestId, new SmartCityPosition(0, 0),
                                new SmartCityPosition(0, 0)),
                        new RideElectionInfo(null, RideElectionInfo.RideElectionState.ELECTED));
//                Optional<RideRequestDto> optRideRequest = rideRequestsMap.keySet().stream()
//                        .filter(rr -> rr.getId() == rideRequestId)
//                        .findAny();
//                optRideRequest.ifPresent(rr -> {
//                    if (rideRequestsMap.get(rr).getRideElectionState()
//                            .equals(RideElectionInfo.RideElectionState.ELECTION)) {
//                        rideRequestsMap.get(rr).setRideElectionState(RideElectionInfo.RideElectionState.ELECTED);
//                        taxi.getNextDistrictTaxiConnection()
//                                .ifPresent(conn -> conn.sendMarkElectionConfirmed(rideRequestId));
//                    }
//                });
            }
//        });

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    //    @Override
//    public void askRideRequestApproval(TaxiServiceOuterClass.ElectionInfoRequest request,
//                                       StreamObserver<TaxiServiceOuterClass.RideRequestApprovalResponse> responseObserver) {
//        if (request.getRideRequestTimestamp() < taxi.getSubscriptionTs() ||
//                !this.taxi.getStatus().equals(Taxi.TaxiStatus.AVAILABLE)) {
//            responseObserver.onNext(TaxiServiceOuterClass.RideRequestApprovalResponse
//                    .newBuilder().setIsApproved(true).build());
//            responseObserver.onCompleted();
//            return;
//        }
//
//        RideRequestDto rideRequest = null;
//        Map<RideRequestDto, Map<Integer, Boolean>> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
//        // before responding I wait until I receive the published ride request too
//        synchronized (rideRequestsMap) {
//            while (rideRequestsMap.keySet()
//                    .stream().noneMatch(rr -> rr.getId() == request.getRideRequestId())) {
//                try {
//                    rideRequestsMap.wait();
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//
//            rideRequest = taxi.getRideRequestElectionsMap().keySet()
//                    .stream().filter(rr -> rr.getId() == request.getRideRequestId())
//                    .findAny().get();
//        }
//
//        responseObserver.onNext(TaxiServiceOuterClass.RideRequestApprovalResponse
//                .newBuilder()
//                .setIsApproved(isTheRemoteTaxiBetterSuitedForTheRide(rideRequest, request))
//                .build());
//        responseObserver.onCompleted();
//    }
//
//    @Override
//    public void updateRideRequestApproval(TaxiServiceOuterClass.RideRequestUpdateRequest request,
//                                          StreamObserver<Empty> responseObserver) {
//        Map<RideRequestDto, Map<Integer, Boolean>> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
//        // I have to synchronize on the whole map because in the meantime the ride id key could be deleted
//        synchronized (rideRequestsMap) {
//            Optional<RideRequestDto> optRideRequest = rideRequestsMap.keySet()
//                    .stream().filter(rr -> rr.getId() == request.getRideRequestId())
//                    .findAny();
//
//            optRideRequest.ifPresent(rr -> {
//                if (request.getIsAlreadyConfirmed())
//                    rideRequestsMap.remove(rr);
//                else {
//                    rideRequestsMap.get(rr).put(request.getTaxiId(), true);
//                    this.taxi.takeRideIfPossible(rr);
//                }
//            });
//        }
//    }

//    private boolean isTheRemoteTaxiBetterSuitedForTheRide(
//            RideRequestDto rideRequest,
//            TaxiServiceOuterClass.ElectionInfoRequest remoteElectionInfo) {
//        if (remoteElectionInfo.getDistanceFromSP() < this.taxi.getDistanceFromRideStart(rideRequest))
//            return true;
//        else if (remoteElectionInfo.getDistanceFromSP() > this.taxi.getDistanceFromRideStart(rideRequest))
//            return false;
//
//        if (remoteElectionInfo.getBatteryLevel() > this.taxi.getBatteryLevel())
//            return true;
//        else if (remoteElectionInfo.getBatteryLevel() < this.taxi.getBatteryLevel())
//            return false;
//
//        return remoteElectionInfo.getTaxiId() > this.taxi.getId();
//    }
}
