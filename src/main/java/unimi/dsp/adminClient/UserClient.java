package unimi.dsp.adminClient;

import com.sun.jersey.api.client.*;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsAvgReportDto;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.DateTimeUtil;
import unimi.dsp.util.RestUtils;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class UserClient {
    public static void main(String[] args) {
        ClientConfig config = new DefaultClientConfig();
        // enabling automatic marshalling for json
        // https://stackoverflow.com/questions/27341788/jersey-clientresponse-getentity-of-generic-type
        config.getClasses().add(JacksonJaxbJsonProvider.class);

        Client client = Client.create(config);
        ConfigurationManager configManager = ConfigurationManager.getInstance();
        String serverAddress = configManager.getAdminServerEndpoint();

        int userChoice = 0;
        do {
            printMenuMessage();

            // I recreate the scanner every time to avoid space-separated options like "1 2 3"
            // https://stackoverflow.com/questions/10604125/how-can-i-clear-the-scanner-buffer-in-java
            Scanner scanner = new Scanner(System.in);
            try {
                userChoice = scanner.nextInt();
                switch (userChoice) {
                    case 1:
                        enterGetAllTaxisFlow(client, serverAddress);
                        break;
                    case 2:
                        enterGetSingleTaxiStatisticsFlow(client, serverAddress);
                        break;
                    case 3:
                        enterGetAllTaxiStatisticsFlow(client, serverAddress);
                        break;
                    case 4:
                        break;
                    default:
                        System.out.println("Not supported choice");
                }
            }
            catch (InputMismatchException e) {
                System.out.println("Not supported choice");
            }
        } while (userChoice != 4);
    }

    private static void printMenuMessage() {
        System.out.println("Choose one of the following options:\n" +
                "1 - Get the list of taxis\n" +
                "2 - Get the average of the last n statistics of a given taxi\n" +
                "3 - Get the average of the statistics loaded in a certain period for all taxis\n" +
                "4 - exit");
    }

    private static <T> void printResponseBody(ClientResponse response, GenericType<T> serializationType) {
        T body = response.getEntity(serializationType);
        System.out.println(body);
    }

    private static void enterGetAllTaxisFlow(Client client, String serverAddress) {
        ClientResponse response = RestUtils.sendGetRequest(client, serverAddress + "/taxis");
        printResponseBody(response, new GenericType<List<TaxiInfoDto>>() {});
    }

    private static void enterGetSingleTaxiStatisticsFlow(Client client, String serverAddress) {
        Scanner scanner = new Scanner(System.in);
        int taxiId;
        int n;
        try {
            System.out.print("taxiId = ");
            taxiId = scanner.nextInt();

            System.out.print("n = ");
            n = scanner.nextInt();
            if (n <= 0)
                throw new IllegalArgumentException();
        }
        catch (InputMismatchException | IllegalArgumentException e) {
            System.out.println("taxiId must be an integer, n must be an integer > 0");
            return;
        }

        ClientResponse response = RestUtils.sendGetRequest(client,
                serverAddress + String.format("/taxis/%d/statistics/report?n=%d", taxiId, n));

        if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            System.out.println("the specified taxi id has not been found");
            return;
        }

        printResponseBody(response, new GenericType<TaxiStatisticsAvgReportDto>() {});
    }

    private static void enterGetAllTaxiStatisticsFlow(Client client, String serverAddress) {
        Scanner scanner = new Scanner(System.in);
        OffsetDateTime tsStart;
        OffsetDateTime tsEnd;
        try {
            System.out.print("tsStart = ");
            tsStart = getOffsetDateTimeFromScanner(scanner);
            System.out.print("tsEnd = ");
            tsEnd = getOffsetDateTimeFromScanner(scanner);
            if (!tsStart.isBefore(tsEnd))
                throw new IllegalArgumentException();
        }
        catch (IllegalArgumentException e) {
            System.out.println("tsStart < tsEnd, and must be compliant with " + DateTimeUtil.DATETIME_FORMAT);
            return;
        }

        String url = serverAddress +
                String.format("/taxis/statistics/report?tsStart=%s&tsEnd=%s",
                        DateTimeUtil.getStringFromOffsetDateTime(tsStart),
                        DateTimeUtil.getStringFromOffsetDateTime(tsEnd));
        ClientResponse response = RestUtils.sendGetRequest(client,url);

        printResponseBody(response, new GenericType<TaxiStatisticsAvgReportDto>() {});
    }

    private static OffsetDateTime getOffsetDateTimeFromScanner(Scanner scanner) {
        try {
            String strTsStart = scanner.next();
            return DateTimeUtil.getOffsetDateTimeFromString(strTsStart);
        }
        catch (DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
