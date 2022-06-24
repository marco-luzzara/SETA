package unimi.dsp.taxi;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.taxi.services.grpc.TaxiService;
import unimi.dsp.taxi.services.mqtt.SETATaxiPubSub;
import unimi.dsp.taxi.services.rest.AdminService;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Taxi implements Closeable  {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final Logger logger = LogManager.getLogger(Taxi.class.getName());

    private final int id;
    private final String host;
    private final int port;
    private final int rideDeliveryDelay;
    private final int batteryConsumptionPerKm;
    private final int batteryThresholdBeforeRecharge;
    private final int rechargeDelay;
    private int batteryLevel;
    private int x;
    private int y;
    // map that associate a connection to all the other taxis in the network
    private HashMap<Integer, NetworkTaxiConnection> networkTaxis;
    private TaxiStatus status;

    // TODO: for ride request processing to partially avoid double requests
    private Optional<Integer> lastElectionId;
    private long subscriptionTs;
    // the key of the outer map represents the ride request election, while the key of the inner map
    // is the taxiId associated to the OK-like message for the ride approval
    private final Map<RideRequestDto, Map<Integer, Boolean>> rideRequestElectionsMap;

    // for grpc communication
    private Server grpcServer;

    // for mqtt communication
    private final SETATaxiPubSubBase setaPubSub;

    // for admin server communication
    private final AdminServiceBase adminService;

    public Taxi(int id, String host, int port,
                int rideDeliveryDelay,
                int batteryConsumptionPerKm,
                int batteryThresholdBeforeRecharge,
                int rechargeDelay,
                AdminServiceBase adminService,
                SETATaxiPubSubBase setaPubSub) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.rideDeliveryDelay = rideDeliveryDelay;
        this.batteryConsumptionPerKm = batteryConsumptionPerKm;
        this.batteryThresholdBeforeRecharge = batteryThresholdBeforeRecharge;
        this.rechargeDelay = rechargeDelay;
        this.adminService = adminService;
        this.setaPubSub = setaPubSub;
        this.batteryLevel = 100;
        this.status = TaxiStatus.UNSTARTED;
        this.networkTaxis = new HashMap<>();
        this.rideRequestElectionsMap = new HashMap<>();
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

    public int getBatteryLevel() {
        return batteryLevel;
    }

    void setX(int x) {
        this.x = x;
    }

    void setY(int y) {
        this.y = y;
    }

    public District getDistrict() {
        return District.fromPosition(new SmartCityPosition(this.x, this.y));
    }

    public HashMap<Integer, NetworkTaxiConnection> getNetworkTaxiConnections() {
        return networkTaxis;
    }

    public long getSubscriptionTs() {
        return subscriptionTs;
    }

    public Map<RideRequestDto, Map<Integer, Boolean>> getRideRequestElectionsMap() {
        return rideRequestElectionsMap;
    }

    public double getDistanceFromRideStart(RideRequestDto rideRequest) {
        double deltaX = this.getX() - rideRequest.getStart().x;
        double deltaY = this.getY() - rideRequest.getStart().y;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    public Collection<NetworkTaxiConnection> getTaxiConnectionsInSameDistrict() {
        return this.getNetworkTaxiConnections().values().stream()
                .filter(conn -> conn.getRemoteTaxiDistrict().equals(this.getDistrict()))
                .collect(Collectors.toList());
    }

    public Collection<Integer> getTaxiIdsInSameDistrict() {
        return this.getNetworkTaxiConnections().entrySet().stream()
                .filter(entry -> entry.getValue().getRemoteTaxiDistrict().equals(this.getDistrict()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public void enterInSETANetwork() {
        this.startGRPCServer();
        this.setStatus(TaxiStatus.GRPC_STARTED);
        this.registerToServer();
        this.setStatus(TaxiStatus.REGISTERED);
        this.informOtherTaxisAboutEnteringTheNetwork();
        this.subscribeToDistrictTopic();
        this.setStatus(TaxiStatus.AVAILABLE);
    }

    public synchronized void setStatus(TaxiStatus status) {
        logger.info("Taxi with id = {} goes from status {} to {}",
                this.getId(), this.getStatus().toString(), status.toString());
        this.status = status;
        // necessary to unlock a quit or recharge command if pending
        this.notifyAll();
    }

    public synchronized TaxiStatus getStatus() {
        return status;
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
        NewTaxiDto newTaxi = this.adminService.registerTaxi(new TaxiInfoDto(this.id, this.host, this.port));
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

    // synchronized on this object because it is a possibly status changing method
    public synchronized void takeRideIfPossible(RideRequestDto rideRequest) {
        if (!this.getStatus().equals(TaxiStatus.AVAILABLE))
            return;

        Map<RideRequestDto, Map<Integer, Boolean>> rideRequestsMap = this.getRideRequestElectionsMap();
        synchronized (rideRequestsMap) {
            if (!rideRequestsMap.containsKey(rideRequest) ||
                    // check whether there is at least one false response from the other taxis
                    rideRequestsMap.get(rideRequest).values().stream().anyMatch(b -> !b))
                return;

            this.setStatus(TaxiStatus.DRIVING);
            for (int taxiId : this.rideRequestElectionsMap.get(rideRequest).keySet()) {
                this.networkTaxis.get(taxiId).sendUpdateRideRequestApproval(rideRequest, true);
            }
            this.networkTaxis.remove(rideRequest.getId());

            for (Map.Entry<RideRequestDto, Map<Integer, Boolean>>
                    rideRequestMap : this.rideRequestElectionsMap.entrySet()) {
                for (Integer taxiId : rideRequestMap.getValue().keySet()) {
                    this.networkTaxis.get(taxiId).sendUpdateRideRequestApproval(rideRequest, false);
                }
            }
        }

        // TODO: confirm to seta through publish
        this.setaPubSub.publishRideConfirmation(new RideConfirmDto(rideRequest.getId()));

        if (!District.fromPosition(rideRequest.getEnd()).equals(this.getDistrict())) {
            this.unsubscribeFromDistrictTopic();
            this.rideRequestElectionsMap.clear();
        }

        try {
            Thread.sleep(this.rideDeliveryDelay);
        } catch (InterruptedException e) {
            logger.error("Sleep for ride is interrupted", e);
            throw new RuntimeException(e);
        }

        this.informOtherTaxisAboutDistrictChanged();
    }

    void informOtherTaxisAboutDistrictChanged() {
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.sendLocalDistrictChanged();
        }
    }

    void subscribeToDistrictTopic() {
        this.setaPubSub.subscribeToDistrictTopic(this.getDistrict(), (rideRequest) -> {
            logger.info("Taxi {} will try to take the ride request {}", getId(), rideRequest.getId());
            synchronized (rideRequestElectionsMap) {
                Map<Integer, Boolean> rideRequestMap = new HashMap<>();
                Collection<Integer> districtTaxiIds = this.getTaxiIdsInSameDistrict();
                if (districtTaxiIds.isEmpty())
                    this.takeRideIfPossible(rideRequest);

                for (Integer taxiId : districtTaxiIds) {
                    rideRequestMap.put(taxiId, false);
                    networkTaxis.get(taxiId).sendAskRideRequestApproval(rideRequest);
                }

                rideRequestElectionsMap.put(rideRequest, rideRequestMap);
                rideRequestElectionsMap.notifyAll();
            }

            rideRequestElectionsMap.put(rideRequest, new HashMap<>());
        });

        this.lastElectionId = Optional.empty();
        // I am assuming that SETA time and current taxi time are synchronized
        this.subscriptionTs = System.currentTimeMillis();
    }

    // synchronized on this object because it is a possibly status changing method
    public synchronized void goToRechargeStationIfPossible() {
//        synchronized ()
    }

    void unsubscribeFromDistrictTopic() {
        this.setaPubSub.unsubscribeFromDistrictTopic(this.getDistrict());
    }

    void informOtherTaxisAboutExitingFromTheNetwork() {
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.sendRemoveTaxi();
        }

        this.networkTaxis = new HashMap<>();
    }

    private void unregisterFromServer() {
        this.adminService.unregisterTaxi(this.id);
    }

    private void stopGRPCServer() {
        this.grpcServer.shutdown();
    }

    @Override
    public void close() {
        logger.info("Taxi {} is exiting from the network", this.id);
        if (this.status.equals(TaxiStatus.UNSTARTED))
            return;

        try {
            if (this.status.equals(TaxiStatus.GRPC_STARTED)) {
                this.stopGRPCServer();
                return;
            }
            if (this.status.equals(TaxiStatus.REGISTERED)) {
                this.stopGRPCServer();
                this.unregisterFromServer();
                return;
            }

            this.unregisterFromServer();
            this.informOtherTaxisAboutExitingFromTheNetwork();
            this.stopGRPCServer();
            this.unsubscribeFromDistrictTopic();
        } finally {
            this.setStatus(TaxiStatus.UNSTARTED);
        }
    }

    public static void main(String[] args) {
        if (args.length <= 2) {
            throw new IllegalArgumentException("put arguments in the following order: id, host, port");
        }
        int id = Integer.parseInt(args[0]);
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        // rest server initialization
        String serverEndpoint = configurationManager.getAdminServerEndpoint();
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        Client client = Client.create(config);
        AdminServiceBase adminService = new AdminService(client, serverEndpoint);

        // mqtt initialization
        MqttAsyncClient mqttClient = MQTTClientFactory.getClient();
        SETATaxiPubSubBase setaTaxiPubSubBase = new SETATaxiPubSub(mqttClient);

        // taxi initialization
        int rideDeliveryDelay = configurationManager.getRideDeliveryDelay();
        int batteryConsumptionPerKm = configurationManager.getBatteryConsumptionPerKm();
        int batteryThresholdBeforeRecharge = configurationManager.getBatteryThresholdBeforeRecharge();
        int rechargeDelay = configurationManager.getRechargeDelay();

        final String RECHARGE_COMMAND = "recharge";
        final String QUIT_COMMAND = "quit";

        try (Taxi taxi = new Taxi(id, host, port, rideDeliveryDelay, batteryConsumptionPerKm,
                batteryThresholdBeforeRecharge, rechargeDelay, adminService, setaTaxiPubSubBase)) {
            taxi.enterInSETANetwork();

            List<String> availableCmds = Arrays.asList(RECHARGE_COMMAND, QUIT_COMMAND);
            System.out.println("Available commands: " + String.join(", ", availableCmds));

            Scanner scanner = new Scanner(System.in);
            while (true) {
                String command = scanner.nextLine();

                if (command.equals(QUIT_COMMAND)) {
                    synchronized (taxi) {
                        while (!taxi.getStatus().equals(TaxiStatus.AVAILABLE)) {
                            try {
                                taxi.wait();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        // by returning, the "close" method is automatically called
                        break;
                    }
                }
                else if (command.equals(RECHARGE_COMMAND)) {
                    synchronized (taxi) {
                        while (!taxi.getStatus().equals(TaxiStatus.AVAILABLE)) {
                            try {
                                taxi.wait();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        taxi.goToRechargeStationIfPossible();
                    }
                }
                else {
                    System.out.println("The command " + command + " is not available");
                }
            }
        }

        try {
            mqttClient.disconnect().waitForCompletion();
            mqttClient.close();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    public enum TaxiStatus {
        UNSTARTED(0),
        GRPC_STARTED(1),
        REGISTERED(2),
        AVAILABLE(3),
        RECHARGING(4),
        DRIVING(5);

        private final int value;

        TaxiStatus(int value) {
            this.value = value;
        }
    }
}
