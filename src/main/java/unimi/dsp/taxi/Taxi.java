package unimi.dsp.taxi;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.taxi.grpc.services.TaxiService;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.RestUtils;

import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public class Taxi implements Closeable  {
    private int id;
    private String host;
    private int port;
    private int batteryLevel;
    private int x;
    private int y;
    private List<TaxiInfoDto> registeredTaxis;
    private TaxiStatus status;

    // for grpc communication
    private Server grpcServer;
    private Channel grpcChannel;

    // for admin server communication
    private Client client;
    private String serverEndpoint = ConfigurationManager.getInstance().getAdminServerEndpoint();

    public Taxi(int id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.batteryLevel = 100;
        this.status = TaxiStatus.GRPC_UNSTARTED;

        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        client = Client.create(config);

        this.grpcChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    List<TaxiInfoDto> getRegisteredTaxis() {
        return registeredTaxis;
    }

    public void enterInSETANetwork() {
        this.startGRPCServer();
        this.status = TaxiStatus.GRPC_STARTED;
        this.registerToServer();
        this.status = TaxiStatus.TAXI_REGISTERED;
    }

    void startGRPCServer()  {
        this.grpcServer = ServerBuilder.forPort(this.port)
                .addService(new TaxiService(this)).build();

        try {
            this.grpcServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void registerToServer() {
        ClientResponse response = RestUtils.postRequest(client, serverEndpoint + "/taxis",
                new TaxiInfoDto(this.id, this.host, this.port));
        if (response.getStatus() == Response.Status.CONFLICT.getStatusCode())
            throw new IllegalStateException("Taxi cannot register because another taxi has the same id");
        NewTaxiDto newTaxi = response.getEntity(new GenericType<NewTaxiDto>() {
        });
        this.x = newTaxi.getX();
        this.y = newTaxi.getY();
        this.registeredTaxis = newTaxi.getTaxiInfos();
    }

    void informOtherTaxis() {
        //TaxiServiceGrpc.TaxiServiceStub stub = TaxiServiceGrpc.newStub(this.grpcChannel);


    }

    public void addNewTaxi(TaxiServiceOuterClass.TaxiAddRequest taxiAddInfo) {
        this.registeredTaxis.add(
                new TaxiInfoDto(
                        taxiAddInfo.getId(),
                        taxiAddInfo.getIpAddress(),
                        taxiAddInfo.getPort()
                )
        );
    }

    public void removeTaxi(TaxiServiceOuterClass.TaxiRemoveRequest taxiRemoveInfo) {
        this.registeredTaxis.stream().filter(ti -> ti.getId() == taxiRemoveInfo.getId())
                .findAny()
                .ifPresent(this.registeredTaxis::remove);
    }

    private void unregisterFromServer() {
        String serverEndpoint = ConfigurationManager.getInstance().getAdminServerEndpoint();
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        Client client = Client.create(config);

        ClientResponse response = RestUtils.sendDeleteRequest(client,
                serverEndpoint + "/taxis/" + this.id);
        int statusCode = response.getStatus();
        if (statusCode != Response.Status.OK.getStatusCode())
            throw new IllegalStateException("The taxi did not register correctly, status code: " + statusCode);
    }

    private void stopGRPCServer() {
        this.grpcServer.shutdown();
    }

    @Override
    public void close() {
        if (this.status.isInOrAfter(TaxiStatus.GRPC_STARTED))
            this.stopGRPCServer();
        if (this.status.isInOrAfter(TaxiStatus.TAXI_REGISTERED))
            this.unregisterFromServer();
    }

    private enum TaxiStatus {
        GRPC_UNSTARTED(0),
        GRPC_STARTED(1),
        TAXI_REGISTERED(2);

        private int value;

        TaxiStatus(int value) {
            this.value = value;
        }

        public boolean isInOrAfter(TaxiStatus status) {
            return this.value >= status.value;
        }
    }
}
