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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class NetworkTaxiConnection {
    private static final Logger logger = LogManager.getLogger(NetworkTaxiConnection.class.getName());

    private final Taxi taxi;
    private final TaxiInfoDto remoteTaxiInfo;
    private District remoteTaxiDistrict;
    private Optional<ManagedChannel> channel;

    public NetworkTaxiConnection(Taxi taxi, TaxiInfoDto remoteTaxiInfo) {
        this.taxi = taxi;
        this.remoteTaxiInfo = remoteTaxiInfo;
        this.channel = Optional.empty();
    }

    public District getRemoteTaxiDistrict() {
        return remoteTaxiDistrict;
    }

    public int getRemoteTaxiId() {
        return remoteTaxiInfo.getId();
    }

    public void setRemoteTaxiDistrict(District remoteTaxiDistrict) {
        this.remoteTaxiDistrict = remoteTaxiDistrict;
        this.openChannelBasedOnDistrict();
    }

    private void openChannelBasedOnDistrict() {
        if (remoteTaxiDistrict.equals(this.taxi.getDistrict()))
            this.openChannelIfNecessary();
        else
            this.closeChannelIfNecessary();
    }

    private void openChannelIfNecessary() {
        if (!this.channel.isPresent())
            this.channel = Optional.of(ManagedChannelBuilder
                    .forAddress(this.remoteTaxiInfo.getIpAddress(), this.remoteTaxiInfo.getPort())
                    .usePlaintext().build());
    }

    private void closeChannelIfNecessary() {
        this.channel.ifPresent(c -> {
            try {
                c.shutdown().awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        this.channel = Optional.empty();
    }

    /**
     * used to inform the remote taxi that a new one (`taxi`) has entered the network.
     * the channel stays open only for those taxis that are in the same district.
     */
    public void sendAddTaxi() {
        assert !this.channel.isPresent();

        this.openChannelIfNecessary();

        TaxiServiceOuterClass.TaxiAddRequest request = TaxiServiceOuterClass.TaxiAddRequest.newBuilder()
                .setId(this.taxi.getId())
                .setIpAddress(this.taxi.getHost())
                .setPort(this.taxi.getPort())
                .setX(this.taxi.getX())
                .setY(this.taxi.getY())
                .build();
        TaxiServiceOuterClass.TaxiAddResponse response = TaxiServiceGrpc
                .newBlockingStub(channel.get()).addTaxi(request);

        District remoteDistrict = District.fromPosition(
                new SmartCityPosition(response.getX(), response.getY()));
        this.setRemoteTaxiDistrict(remoteDistrict);
    }

    public void sendChangeRemoteTaxiDistrict() {
        this.openChannelIfNecessary();

        TaxiServiceOuterClass.TaxiNewDistrictRequest request = TaxiServiceOuterClass.TaxiNewDistrictRequest
                .newBuilder()
                .setId(this.taxi.getId())
                .setNewX(this.taxi.getX()).setNewY(this.taxi.getY())
                .build();

        TaxiServiceGrpc.newBlockingStub(this.channel.get()).changeRemoteTaxiDistrict(request);
        this.openChannelBasedOnDistrict();
    }

    public void sendRemoveTaxi() {
        this.openChannelIfNecessary();

        TaxiServiceOuterClass.TaxiRemoveRequest request = TaxiServiceOuterClass.TaxiRemoveRequest.newBuilder()
                .setId(this.taxi.getId())
                .build();
        TaxiServiceGrpc.newBlockingStub(this.channel.get()).removeTaxi(request);
        this.closeChannelIfNecessary();
    }

    public boolean sendForwardElectionIdOrTakeRide(RideRequestDto rideRequest,
                                                 RideElectionInfo.RideElectionId rideRequestElectionId) {
        assert this.channel.isPresent();

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

        boolean retry = TaxiServiceGrpc.newBlockingStub(this.channel.get())
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
        assert this.channel.isPresent();

        TaxiServiceOuterClass.RideElectionConfirmRequest request = TaxiServiceOuterClass.RideElectionConfirmRequest
                .newBuilder().setRideRequestId(rideRequestId).setTaxiId(taxiId).build();

        TaxiServiceGrpc.newStub(this.channel.get()).markElectionConfirmed(request, new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {
                logger.info("Taxi {} sent ELECTED for ride {} to taxi {}",
                        taxi.getId(), rideRequestId, remoteTaxiInfo.getId());
            }

            @Override
            public void onError(Throwable t) {
                logger.error(
                        String.format("ERROR: taxi %d cannot send ELECTED info for ride %d to taxi %d",
                                taxi.getId(), rideRequestId, remoteTaxiInfo.getId()), t);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    public boolean sendAskRechargeRequestApproval() {
        assert this.channel.isPresent();

        TaxiServiceOuterClass.RechargeInfoRequest request = TaxiServiceOuterClass.RechargeInfoRequest
                .newBuilder().setTaxiId(this.taxi.getId()).setRechargeTs(this.taxi.getLocalRechargeRequestTs())
                .build();

        TaxiServiceOuterClass.RechargeInfoResponse response = TaxiServiceGrpc.newBlockingStub(this.channel.get())
                .askRechargeRequestApproval(request);

        return response.getOk();
    }

    public void sendUpdateRechargeRequestApproval() {
        assert this.channel.isPresent();

        TaxiServiceOuterClass.RechargeApprovalRequest request = TaxiServiceOuterClass.RechargeApprovalRequest
                .newBuilder().setTaxiId(this.taxi.getId()).build();

        TaxiServiceGrpc.newStub(this.channel.get()).updateRechargeRequestApproval(request,
                new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {
                        logger.info("Taxi {} sent RECHARGE-FREE update to taxi {}",
                                taxi.getId(), remoteTaxiInfo.getId());
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.error(
                                String.format("ERROR: taxi %d cannot send RECHARGE-FREE update to taxi %d",
                                        taxi.getId(), remoteTaxiInfo.getId()), t);
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
    }
}
