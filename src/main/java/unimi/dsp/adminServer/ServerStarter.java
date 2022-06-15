package unimi.dsp.adminServer;

import com.sun.jersey.api.container.httpserver.HttpServerFactory;
import com.sun.net.httpserver.HttpServer;
import unimi.dsp.util.ConfigurationManager;

import java.io.IOException;

public class ServerStarter {
    public static void main(String[] args) throws IOException {
        ConfigurationManager manager = ConfigurationManager.getInstance();
        String serverUri = manager.getAdminServerEndpoint();
        HttpServer server = HttpServerFactory.create(serverUri + "/");
        server.start();

        System.out.println("Server running!");
        System.out.println("Server started");

        System.out.println("Hit return to stop...");
        System.in.read();
        System.out.println("Stopping server");
        server.stop(0);
        System.out.println("Server stopped");
    }
}
