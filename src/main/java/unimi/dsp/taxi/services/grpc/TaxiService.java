package unimi.dsp.taxi.services.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.types.election.RideElectionInfo;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.election.RideRequestMessage;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.taxi.NetworkTaxiConnection;
import unimi.dsp.taxi.Taxi;
import unimi.dsp.taxi.TaxiServiceGrpc;
import unimi.dsp.taxi.TaxiServiceOuterClass;

import java.util.Optional;

public class TaxiService extends TaxiServiceGrpc.TaxiServiceImplBase {
//    private static final Logger logger = LogManager.getLogger(TaxiService.class.getName());

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
        boolean isThisPreviousTaxi = false;
        synchronized (this.taxi.getNetworkTaxiConnections()) {
            if (this.taxi.getNetworkTaxiConnections().containsKey(request.getId())) {
                // set if the next taxi in the ring is the node that sent me this message (notifying that
                // it will exit)
                // FIXED: getNextDistrictTaxiConnection() must be retrieved first because the district could
                // change in the meantime
                Optional<NetworkTaxiConnection> optNextDistrictConn = this.taxi.getNextDistrictTaxiConnection();
                isThisPreviousTaxi = optNextDistrictConn.isPresent() &&
                        optNextDistrictConn.get().getRemoteTaxiId() == request.getId();

                this.taxi.getNetworkTaxiConnections().get(request.getId()).close();
                this.taxi.getNetworkTaxiConnections().remove(request.getId());
            }
        }

        // I cannot put it inside the above synchronized block because I risk a deadlock because the
        // lock order is the opposite as the one it should be (this -> network connections object)
        // I resend all the messages because they are discarded once they arrive in a new district
        if (isThisPreviousTaxi)
            this.taxi.resendElectionMessagesToDistrictNextConnection();

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void changeRemoteTaxiDistrict(TaxiServiceOuterClass.TaxiNewDistrictRequest request,
                                         StreamObserver<Empty> responseObserver) {
        District remoteDistrict = District.fromPosition(
                new SmartCityPosition(request.getNewX(), request.getNewY()));
        boolean isThisPreviousTaxi = false;
        synchronized (this.taxi.getNetworkTaxiConnections()) {
            if (this.taxi.getNetworkTaxiConnections().containsKey(request.getId())) {
                // set if the next taxi in the ring is the node that sent me this message (notifying that
                // it will change district)
                // FIXED: getNextDistrictTaxiConnection() must be retrieved first because the district could
                // change in the meantime
                Optional<NetworkTaxiConnection> optNextDistrictConn = this.taxi.getNextDistrictTaxiConnection();
                isThisPreviousTaxi = optNextDistrictConn.isPresent() &&
                        optNextDistrictConn.get().getRemoteTaxiId() == request.getId();

                this.taxi.getNetworkTaxiConnections().get(request.getId())
                        .setRemoteTaxiDistrict(remoteDistrict);
            }
        }

        if (isThisPreviousTaxi)
            this.taxi.resendElectionMessagesToDistrictNextConnection();

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void forwardElectionIdOrTakeRide(TaxiServiceOuterClass.RideElectionIdRequest request,
                                 StreamObserver<Empty> responseObserver) {
        RideRequestDto rideRequest = new RideRequestDto(request.getRideRequestId(),
                new SmartCityPosition(request.getStartX(), request.getStartY()),
                new SmartCityPosition(request.getEndX(), request.getEndY()));
        RideElectionInfo.RideElectionId receivedElectionId = new RideElectionInfo.RideElectionId(
                request.getTaxiId(), request.getDistanceFromSP(), request.getBatteryLevel());

        this.taxi.getRideRequestMessagesQueue().put(new RideRequestMessage(
                new RideElectionInfo(receivedElectionId, RideElectionInfo.RideElectionState.ELECTION),
                rideRequest));

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void markElectionConfirmed(TaxiServiceOuterClass.RideElectionConfirmRequest request,
                                      StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();

        int rideRequestId = request.getRideRequestId();
        // I create a fake ride request containing the ride request only because I just need the id
        // in order to avoid re-election of already confirmed ride requests
        this.taxi.getRideRequestMessagesQueue().put(new RideRequestMessage(
                new RideElectionInfo(
                        new RideElectionInfo.RideElectionId(request.getTaxiId(), 0, 0),
                        RideElectionInfo.RideElectionState.ELECTED),
                new RideRequestDto(rideRequestId, new SmartCityPosition(0, 0),
                        new SmartCityPosition(0, 0))));
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
