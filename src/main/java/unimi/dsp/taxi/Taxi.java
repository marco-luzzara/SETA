package unimi.dsp.taxi;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.taxi.grpc.services.TaxiService;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.RestUtil;

import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;

public class Taxi implements Closeable  {
    private int id;
    private String host;
    private int port;
    private int batteryLevel;
    private int x;
    private int y;
    private HashMap<Integer, NetworkTaxiConnection> networkTaxis;
    private TaxiStatus status;

    // for grpc communication
    private Server grpcServer;

    // for admin server communication
    private Client client;
    private String serverEndpoint = ConfigurationManager.getInstance().getAdminServerEndpoint();

    public Taxi(int id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.batteryLevel = 100;
        this.status = TaxiStatus.GRPC_UNSTARTED;
        this.networkTaxis = new HashMap<>();

        // for rest register and unregister
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        client = Client.create(config);
    }

    public int getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public District getDistrict() {
        return District.fromPosition(new SmartCityPosition(this.x, this.y));
    }

    HashMap<Integer, NetworkTaxiConnection> getNetworkTaxiConnections() {
        return networkTaxis;
    }

    public void enterInSETANetwork() {
        this.startGRPCServer();
        this.status = TaxiStatus.GRPC_STARTED;
        this.registerToServer();
        this.status = TaxiStatus.TAXI_REGISTERED;
        this.informOtherTaxisAboutEnteringTheNetwork();
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
        ClientResponse response = RestUtil.postRequest(client, serverEndpoint + "/taxis",
                new TaxiInfoDto(this.id, this.host, this.port));
        if (response.getStatus() == Response.Status.CONFLICT.getStatusCode())
            throw new IllegalStateException("Taxi cannot register because another taxi has the same id");
        NewTaxiDto newTaxi = response.getEntity(new GenericType<NewTaxiDto>() {
        });
        this.x = newTaxi.getX();
        this.y = newTaxi.getY();
        for (TaxiInfoDto taxiInfoDto : newTaxi.getTaxiInfos()) {
            this.networkTaxis.put(taxiInfoDto.getId(), new NetworkTaxiConnection(this, taxiInfoDto));
        }
    }

    void informOtherTaxisAboutEnteringTheNetwork() {
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.sendAddTaxi();
        }
    }

    void informOtherTaxisAboutExitingFromTheNetwork() {
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.sendRemoveTaxi();
        }

        this.networkTaxis = new HashMap<>();
    }

    // server callback after a new remote taxi informs this about its entrance in the network
    public void addRemoteTaxi(TaxiServiceOuterClass.TaxiAddRequest taxiAddInfo) {
        NetworkTaxiConnection taxiConnection = new NetworkTaxiConnection(this,
                new TaxiInfoDto(this.getId(), this.getHost(), this.getPort())
            );
        taxiConnection.setRemoteTaxiDistrict(
                District.fromPosition(new SmartCityPosition(
                    taxiAddInfo.getX(), taxiAddInfo.getY()
                )));

        this.networkTaxis.put(taxiAddInfo.getId(), taxiConnection);
    }

    // server callback after a remote taxi informs this about its exit from the network
    public void removeRemoteTaxi(int id) {
        this.networkTaxis.remove(id);
    }

    private void unregisterFromServer() {
        String serverEndpoint = ConfigurationManager.getInstance().getAdminServerEndpoint();
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        Client client = Client.create(config);

        ClientResponse response = RestUtil.sendDeleteRequest(client,
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
        if (this.status.isInOrAfter(TaxiStatus.TAXI_REGISTERED)) {
            this.unregisterFromServer();
            this.informOtherTaxisAboutExitingFromTheNetwork();
        }

        this.status = TaxiStatus.GRPC_UNSTARTED;
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
