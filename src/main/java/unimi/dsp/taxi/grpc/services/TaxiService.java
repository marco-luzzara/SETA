package unimi.dsp.taxi.grpc.services;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.dto.TaxiInfoDto;
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
                new TaxiInfoDto(this.taxi.getId(), this.taxi.getHost(), this.taxi.getPort())
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
        this.taxi.getNetworkTaxiConnections().remove(request.getId());

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void changeRemoteTaxiDistrict(TaxiServiceOuterClass.TaxiNewDistrictRequest request,
                                         StreamObserver<Empty> responseObserver) {
        District remoteDistrict = District.fromPosition(
                new SmartCityPosition(request.getNewX(), request.getNewY()));
        this.taxi.getNetworkTaxiConnections().get(request.getId())
                .setRemoteTaxiDistrict(remoteDistrict);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void askRideRequestApproval(TaxiServiceOuterClass.ElectionInfoRequest request,
                                       StreamObserver<TaxiServiceOuterClass.RideRequestApprovalResponse> responseObserver) {
        if (request.getRideRequestTimestamp() < taxi.getSubscriptionTs() ||
                !this.taxi.getStatus().equals(Taxi.TaxiStatus.AVAILABLE)) {
            responseObserver.onNext(TaxiServiceOuterClass.RideRequestApprovalResponse
                    .newBuilder().setIsApproved(true).build());
            responseObserver.onCompleted();
            return;
        }

        RideRequestDto rideRequest = null;
        Map<RideRequestDto, Map<Integer, Boolean>> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
        // before responding I wait until I receive the published ride request too
        synchronized (rideRequestsMap) {
            while (rideRequestsMap.keySet()
                    .stream().noneMatch(rr -> rr.getId() == request.getRideRequestId())) {
                try {
                    rideRequestsMap.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            rideRequest = taxi.getRideRequestElectionsMap().keySet()
                    .stream().filter(rr -> rr.getId() == request.getRideRequestId())
                    .findAny().get();
        }

        responseObserver.onNext(TaxiServiceOuterClass.RideRequestApprovalResponse
                .newBuilder()
                .setIsApproved(isTheRemoteTaxiBetterSuitedForTheRide(rideRequest, request))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void updateRideRequestApproval(TaxiServiceOuterClass.RideRequestUpdateRequest request,
                                          StreamObserver<Empty> responseObserver) {
        Map<RideRequestDto, Map<Integer, Boolean>> rideRequestsMap = this.taxi.getRideRequestElectionsMap();
        // I have to synchronize on the whole map because in the meantime the ride id key could be deleted
        synchronized (rideRequestsMap) {
            Optional<RideRequestDto> optRideRequest = rideRequestsMap.keySet()
                    .stream().filter(rr -> rr.getId() == request.getRideRequestId())
                    .findAny();

            optRideRequest.ifPresent(rr -> {
                if (request.getIsAlreadyConfirmed())
                    rideRequestsMap.remove(rr);
                else {
                    rideRequestsMap.get(rr).put(request.getTaxiId(), true);
                    this.taxi.takeRideIfPossible(rr);
                }
            });
        }
    }

    private boolean isTheRemoteTaxiBetterSuitedForTheRide(
            RideRequestDto rideRequest,
            TaxiServiceOuterClass.ElectionInfoRequest remoteElectionInfo) {
        if (remoteElectionInfo.getDistanceFromSP() < this.taxi.getDistanceFromRideStart(rideRequest))
            return true;
        else if (remoteElectionInfo.getDistanceFromSP() > this.taxi.getDistanceFromRideStart(rideRequest))
            return false;

        if (remoteElectionInfo.getBatteryLevel() > this.taxi.getBatteryLevel())
            return true;
        else if (remoteElectionInfo.getBatteryLevel() < this.taxi.getBatteryLevel())
            return false;

        return remoteElectionInfo.getTaxiId() > this.taxi.getId();
    }
}
