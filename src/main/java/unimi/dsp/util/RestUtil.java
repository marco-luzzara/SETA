package unimi.dsp.util;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import javax.ws.rs.core.MediaType;
import java.util.function.Supplier;

public class RestUtil {
    public static ClientResponse sendGetRequest(Client client, String url){
        return wrapWithExceptionHandler(() -> {
            WebResource webResource = client.resource(url);
            return webResource.accept(MediaType.APPLICATION_JSON).get(ClientResponse.class);
        });
    }

    public static <T> ClientResponse sendPostRequest(Client client, String url, T entity){
        return wrapWithExceptionHandler(() -> {
            WebResource webResource = client.resource(url);
            return webResource.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, entity);
        });
    }

    public static ClientResponse sendDeleteRequest(Client client, String url) {
        return wrapWithExceptionHandler(() -> {
            WebResource webResource = client.resource(url);
            return webResource.delete(ClientResponse.class);
        });
    }

    private static <T> T wrapWithExceptionHandler(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ClientHandlerException e) {
            System.out.println("Server issue: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }
}
