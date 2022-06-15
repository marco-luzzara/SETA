package unimi.dsp.taxi;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.RestUtils;

import javax.ws.rs.core.Response;

public class Taxi {
    private int id;
    private int port;
    private int batteryLevel;

    public Taxi(int id, int port) {
        this.id = id;
        this.port = port;
        this.batteryLevel = 100;
    }

    public RegisteredTaxi registerToServer() {
        String serverEndpoint = ConfigurationManager.getInstance().getAdminServerEndpoint();
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        Client client = Client.create(config);

        ClientResponse response = RestUtils.postRequest(client, serverEndpoint + "/taxis",
                new TaxiInfoDto(this.id, "localhost", this.port));
        if (response.getStatus() == Response.Status.CONFLICT.getStatusCode())
            throw new IllegalStateException("Taxi cannot register because another taxi has the same id");
        NewTaxiDto newTaxi = response.getEntity(new GenericType<NewTaxiDto>() {});

        return new RegisteredTaxi(id, port, newTaxi.getX(), newTaxi.getY(), newTaxi.getTaxiInfos());
    }
}
