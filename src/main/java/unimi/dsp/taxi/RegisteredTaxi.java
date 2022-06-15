package unimi.dsp.taxi;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.RestUtils;

import javax.ws.rs.core.Response;
import java.util.List;

public class RegisteredTaxi {
    private int id;
    private int port;
    private int batteryLevel;
    private int x;
    private int y;
    private List<TaxiInfoDto> taxis;

    public RegisteredTaxi(int id, int port, int x, int y, List<TaxiInfoDto> taxis) {
        this.id = id;
        this.port = port;
        this.batteryLevel = 100;
        this.x = x;
        this.y = y;
        this.taxis = taxis;
    }

    List<TaxiInfoDto> getTaxis() {
        return taxis;
    }

    public void unregisterFromServer() {
        String serverEndpoint = ConfigurationManager.getInstance().getAdminServerEndpoint();
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        Client client = Client.create(config);

        ClientResponse response = RestUtils.sendDeleteRequest(client, serverEndpoint + "/taxis/" + this.id);
        int statusCode = response.getStatus();
        if (statusCode != Response.Status.OK.getStatusCode())
            throw new IllegalStateException("The taxi did not register correctly, status code: " + statusCode);
    }
}
