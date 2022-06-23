package unimi.dsp.taxi.services.rest;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.taxi.AdminServiceBase;
import unimi.dsp.util.RestUtil;

import javax.ws.rs.core.Response;

public class AdminService implements AdminServiceBase {
    private final Client client;
    private final String serverEndpoint;

    public AdminService(Client client, String serverEndpoint) {
        this.client = client;
        this.serverEndpoint = serverEndpoint;
    }

    @Override
    public NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo) {
        ClientResponse response = RestUtil.postRequest(this.client, this.serverEndpoint + "/taxis", taxiInfo);
        if (response.getStatus() == Response.Status.CONFLICT.getStatusCode())
            throw new IllegalStateException("Taxi cannot register because another taxi has the same id");

        return response.getEntity(new GenericType<NewTaxiDto>() {});
    }

    @Override
    public void unregisterTaxi(int taxiId) {
        ClientResponse response = RestUtil.sendDeleteRequest(this.client,
                this.serverEndpoint + "/taxis/" + taxiId);
        int statusCode = response.getStatus();
        if (statusCode != Response.Status.OK.getStatusCode())
            throw new IllegalStateException("The taxi did not unregister correctly, status code: " + statusCode);
    }
}
