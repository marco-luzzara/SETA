package unimi.dsp.taxi;

import com.google.protobuf.Empty;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;

import java.util.Objects;
import java.util.Optional;

public class NetworkTaxiConnection {
    private Taxi taxi;
    private TaxiInfoDto remoteTaxiInfo;
    private District remoteTaxiDistrict;
    private Optional<ManagedChannel> channel;

    public NetworkTaxiConnection(Taxi taxi, TaxiInfoDto remoteTaxiInfo) {
        this.taxi = taxi;
        this.remoteTaxiInfo = remoteTaxiInfo;
        this.channel = Optional.empty();
    }

//    public int getId() {
//        return id;
//    }
//    public void setId(int id) {
//        this.id = id;
//    }
//
//    public String getIpAddress() {
//        return ipAddress;
//    }
//    public void setIpAddress(String ipAddress) {
//        this.ipAddress = ipAddress;
//    }
//
//    public int getPort() {
//        return port;
//    }
//    public void setPort(int port) {
//        this.port = port;
//    }


    public void setRemoteTaxiDistrict(District remoteTaxiDistrict) {
        this.remoteTaxiDistrict = remoteTaxiDistrict;

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
}
