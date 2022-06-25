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
        this.channel.ifPresent(ManagedChannel::shutdown);
        this.channel = Optional.empty();
    }

    /**
     * used to inform the remote taxi that a new one (`taxi`) has entered in the network.
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

        TaxiServiceGrpc.newBlockingStub(channel.get()).changeRemoteTaxiDistrict(request);
        this.openChannelBasedOnDistrict();
    }

    public void sendRemoveTaxi() {
        this.openChannelIfNecessary();

        TaxiServiceOuterClass.TaxiRemoveRequest request = TaxiServiceOuterClass.TaxiRemoveRequest.newBuilder()
                .setId(this.taxi.getId())
                .build();
        TaxiServiceGrpc.newStub(this.channel.get()).removeTaxi(request, new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {
            }

            @Override
            public void onError(Throwable t) {
                closeChannelIfNecessary();
            }

            @Override
            public void onCompleted() {
                closeChannelIfNecessary();
            }
        });
    }

    public void sendForwardElectionIdOrTakeRide(RideRequestDto rideRequest,
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

        TaxiServiceGrpc.newStub(this.channel.get()).forwardElectionIdOrTakeRide(request,
                new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty value) {
                        logger.info("Taxi {} sent ELECTION info for ride {} to taxi {}",
                                taxi.getId(), rideRequest.getId(), remoteTaxiInfo.getId());
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.error(
                                String.format("ERROR: taxi %d is sending ELECTION info for ride %d to taxi %d",
                                    taxi.getId(), rideRequest.getId(), remoteTaxiInfo.getId()), t);
                    }

                    @Override
                    public void onCompleted() {

                    }
                });
    }

    public void sendMarkElectionConfirmed(int rideRequestId) {
        assert this.channel.isPresent();

        TaxiServiceOuterClass.RideElectionConfirmRequest request = TaxiServiceOuterClass.RideElectionConfirmRequest
                .newBuilder().setRideRequestId(rideRequestId).build();

        TaxiServiceGrpc.newStub(this.channel.get()).markElectionConfirmed(request, new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty value) {
                logger.info("Taxi {} sent ELECTED for ride {} to taxi {}",
                        taxi.getId(), rideRequestId, remoteTaxiInfo.getId());
            }

            @Override
            public void onError(Throwable t) {
                logger.error(
                        String.format("ERROR: taxi %d is sending ELECTED info for ride %d to taxi %d",
                                taxi.getId(), rideRequestId, remoteTaxiInfo.getId()), t);
            }

            @Override
            public void onCompleted() {
            }
        });
    }

//    public void sendAskRideRequestApproval(RideRequestDto rideRequest) {
//        // I am sending a ride request approval from my district
//        assert this.channel.isPresent();
//
//        TaxiServiceOuterClass.ElectionInfoRequest request = TaxiServiceOuterClass.ElectionInfoRequest.newBuilder()
//                .setRideRequestId(rideRequest.getId())
//                .setTaxiId(this.taxi.getId())
//                .setDistanceFromSP(this.taxi.getDistanceFromRideStart(rideRequest))
//                .setBatteryLevel(this.taxi.getBatteryLevel())
//                .setRideRequestTimestamp(rideRequest.getTimestamp())
//                .build();
//        // TODO: a Blocking stub is probably better, to avoid some concurrency problems, you could wrap each of
//        // these executions in a thread and then join eventually
//        TaxiServiceGrpc.newStub(this.channel.get()).askRideRequestApproval(request,
//                new StreamObserver<TaxiServiceOuterClass.RideRequestApprovalResponse>() {
//                    @Override
//                    public void onNext(TaxiServiceOuterClass.RideRequestApprovalResponse value) {
//                        Map<RideRequestDto, Map<Integer, Boolean>> rideRequestsMap = taxi.getRideRequestElectionsMap();
//                        // I have to synchronize on the whole map because in the meantime the taxi could exit
//                        // and the ride request key might be removed
//                        synchronized (rideRequestsMap) {
//                            Optional<RideRequestDto> optRideRequest = rideRequestsMap.keySet()
//                                    .stream().filter(rr -> rr.getId() == request.getRideRequestId())
//                                    .findAny();
//
//                            optRideRequest.ifPresent(rr -> {
//                                rideRequestsMap.get(rr).put(remoteTaxiInfo.getId(), value.getIsApproved());
//                                if (value.getIsApproved())
//                                    taxi.takeRideIfPossible(rr);
//                            });
//                        }
//                    }
//
//                    @Override
//                    public void onError(Throwable t) {
//                        logger.error(
//                                String.format("Error while taxi %s is sending ride request approval to taxi %s",
//                                    taxi.getId(), remoteTaxiInfo.getId()), t);
//                    }
//
//                    @Override
//                    public void onCompleted() {}
//                }
//        );
//    }
//
//    //
//    public void sendUpdateRideRequestApproval(RideRequestDto rideRequest, boolean isAlreadyConfirmed) {
//        TaxiServiceOuterClass.RideRequestUpdateRequest request = TaxiServiceOuterClass.RideRequestUpdateRequest
//                .newBuilder()
//                .setRideRequestId(rideRequest.getId())
//                .setTaxiId(this.taxi.getId())
//                .setIsAlreadyConfirmed(isAlreadyConfirmed)
//                .build();
//        TaxiServiceGrpc.newStub(this.channel.get()).updateRideRequestApproval(request,
//                new StreamObserver<Empty>() {
//                    @Override
//                    public void onNext(Empty value) {
//                    }
//
//                    @Override
//                    public void onError(Throwable t) {
//                        logger.error("Error while updating ride request approval", t);
//                    }
//
//                    @Override
//                    public void onCompleted() {}
//                });
//    }
}
