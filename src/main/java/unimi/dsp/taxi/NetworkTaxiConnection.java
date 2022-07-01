package unimi.dsp.taxi;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.RideElectionInfo;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;

import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class NetworkTaxiConnection implements Closeable {
    private static final Logger logger = LogManager.getLogger(NetworkTaxiConnection.class.getName());

    private final Taxi taxi;
    private final TaxiInfoDto remoteTaxiInfo;
    private District remoteTaxiDistrict;
    private ManagedChannel channel;

    public NetworkTaxiConnection(Taxi taxi, TaxiInfoDto remoteTaxiInfo) {
        this.taxi = taxi;
        this.remoteTaxiInfo = remoteTaxiInfo;
        this.channel = ManagedChannelBuilder
                .forAddress(this.remoteTaxiInfo.getIpAddress(), this.remoteTaxiInfo.getPort())
                .usePlaintext().build();
    }

    public District getRemoteTaxiDistrict() {
        return remoteTaxiDistrict;
    }

    public int getRemoteTaxiId() {
        return remoteTaxiInfo.getId();
    }

    public void setRemoteTaxiDistrict(District remoteTaxiDistrict) {
        this.remoteTaxiDistrict = remoteTaxiDistrict;
    }

    public void close() {
        try {
            channel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * used to inform the remote taxi that a new one (`taxi`) has entered the network.
     * the channel stays open only for those taxis that are in the same district.
     */
    public void sendAddTaxi() {
        TaxiServiceOuterClass.TaxiAddRequest request = TaxiServiceOuterClass.TaxiAddRequest.newBuilder()
                .setId(this.taxi.getId())
                .setIpAddress(this.taxi.getHost())
                .setPort(this.taxi.getPort())
                .setX(this.taxi.getX())
                .setY(this.taxi.getY())
                .build();
        TaxiServiceOuterClass.TaxiAddResponse response = TaxiServiceGrpc
                .newBlockingStub(channel).addTaxi(request);

        District remoteDistrict = District.fromPosition(
                new SmartCityPosition(response.getX(), response.getY()));
        this.setRemoteTaxiDistrict(remoteDistrict);
    }

    public void sendChangeRemoteTaxiDistrict() {
        TaxiServiceOuterClass.TaxiNewDistrictRequest request = TaxiServiceOuterClass.TaxiNewDistrictRequest
                .newBuilder()
                .setId(this.taxi.getId())
                .setNewX(this.taxi.getX()).setNewY(this.taxi.getY())
                .build();

        TaxiServiceGrpc.newBlockingStub(this.channel).changeRemoteTaxiDistrict(request);
    }

    public void sendRemoveTaxi() {
        TaxiServiceOuterClass.TaxiRemoveRequest request = TaxiServiceOuterClass.TaxiRemoveRequest.newBuilder()
                .setId(this.taxi.getId())
                .build();
        TaxiServiceGrpc.newBlockingStub(this.channel).removeTaxi(request);
    }

    public boolean sendForwardElectionIdOrTakeRide(RideRequestDto rideRequest,
                                                 RideElectionInfo.RideElectionId rideRequestElectionId) {
        TaxiServiceOuterClass.RideElectionIdRequest request = TaxiServiceOuterClass.RideElectionIdRequest
                .newBuilder()
                .setRideRequestId(rideRequest.getId())
                .setStartX(rideRequest.getStart().x)
                .setStartY(rideRequest.getStart().y)
                .setEndX(rideRequest.getEnd().x)
                .setEndY(rideRequest.getEnd().y)
                .setTaxiId(rideRequestElectionId.getTaxiId())
                .setDistanceFromSP(rideRequestElectionId.getDistanceFromSP())
                .setBatteryLevel(rideRequestElectionId.getBatteryLevel())
                .build();

        boolean retry = TaxiServiceGrpc.newBlockingStub(this.channel)
                .forwardElectionIdOrTakeRide(request).getRetry();

        if (retry)
            logger.info("Taxi {} cannot send ELECTION info for ride {} to taxi {} because in a new district",
                    taxi.getId(), rideRequest.getId(), remoteTaxiInfo.getId());
        else
            logger.info("Taxi {} sent ELECTION info for ride {} to taxi {}, winning: {}",
                    taxi.getId(), rideRequest.getId(), remoteTaxiInfo.getId(), rideRequestElectionId.getTaxiId());

        return retry;
    }

    public void sendMarkElectionConfirmed(int rideRequestId, int taxiId) {
        TaxiServiceOuterClass.RideElectionConfirmRequest request = TaxiServiceOuterClass.RideElectionConfirmRequest
                .newBuilder().setRideRequestId(rideRequestId).setTaxiId(taxiId).build();

        logger.info("Taxi {} sent ELECTED for ride {} to taxi {}",
                taxi.getId(), rideRequestId, remoteTaxiInfo.getId());
        TaxiServiceGrpc.newBlockingStub(this.channel).markElectionConfirmed(request);
    }

    public boolean sendAskRechargeRequestApproval() {
        TaxiServiceOuterClass.RechargeInfoRequest request = TaxiServiceOuterClass.RechargeInfoRequest
                .newBuilder().setTaxiId(this.taxi.getId()).setRechargeTs(this.taxi.getLocalRechargeRequestTs())
                .build();

        TaxiServiceOuterClass.RechargeInfoResponse response = TaxiServiceGrpc.newBlockingStub(this.channel)
                .askRechargeRequestApproval(request);

        return response.getOk();
    }

    public void sendUpdateRechargeRequestApproval() {
        TaxiServiceOuterClass.RechargeApprovalRequest request = TaxiServiceOuterClass.RechargeApprovalRequest
                .newBuilder().setTaxiId(this.taxi.getId()).build();

        logger.info("Taxi {} sent RECHARGE-FREE update to taxi {}",
                taxi.getId(), remoteTaxiInfo.getId());
        TaxiServiceGrpc.newBlockingStub(this.channel).updateRechargeRequestApproval(request);
    }
}
