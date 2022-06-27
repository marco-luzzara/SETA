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
import unimi.dsp.model.RechargeRequest;
import unimi.dsp.model.RideElectionInfo;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.taxi.services.grpc.TaxiService;
import unimi.dsp.taxi.services.mqtt.SETATaxiPubSub;
import unimi.dsp.taxi.services.rest.AdminService;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;
import unimi.dsp.util.SynchronizationUtils;

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
    private final TaxiConfig taxiConfig;
    private int batteryLevel;
    private int x;
    private int y;
    // map that associate a taxi id with a connection to the corresponding taxi in the network
    private Map<Integer, NetworkTaxiConnection> networkTaxis;
    private TaxiStatus status;
    private Set<Integer> takenRides = new HashSet<>();
    // recharging
    private long localRechargeRequestTs;
    private Set<Integer> rechargeAwaitingTaxiIds;
    // the key of the outer map represents the ride request, while the key of the inner map
    // is the currently greater Id of that election
    private final Map<RideRequestDto, RideElectionInfo> rideRequestElectionsMap;

    // for grpc communication
    private Server grpcServer;

    // for mqtt communication
    private final SETATaxiPubSubBase setaPubSub;

    // for admin server communication
    private final AdminServiceBase adminService;

    public Taxi(int id, String host, int port,
                TaxiConfig taxiConfig,
                AdminServiceBase adminService,
                SETATaxiPubSubBase setaPubSub) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.taxiConfig = taxiConfig;
        this.adminService = adminService;
        this.setaPubSub = setaPubSub;
        this.batteryLevel = this.taxiConfig.initialBatteryLevel;
        this.status = TaxiStatus.UNSTARTED;
        this.networkTaxis = SynchronizationUtils.makeSynchronized(Map.class,
                new HashMap<Integer, NetworkTaxiConnection>());
        this.rideRequestElectionsMap = SynchronizationUtils.makeSynchronized(Map.class,
                new HashMap<RideRequestDto, RideElectionInfo>());
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

    public Set<Integer> getTakenRides() {
        return takenRides;
    }

    public long getLocalRechargeRequestTs() {
        assert this.getStatus().equals(TaxiStatus.RECHARGING);
        return localRechargeRequestTs;
    }

    public Set<Integer> getRechargeAwaitingTaxiIds() {
        return rechargeAwaitingTaxiIds;
    }

    public District getDistrict() {
        return District.fromPosition(new SmartCityPosition(this.x, this.y));
    }

    public Map<Integer, NetworkTaxiConnection> getNetworkTaxiConnections() {
        return networkTaxis;
    }

    public Map<RideRequestDto, RideElectionInfo> getRideRequestElectionsMap() {
        return rideRequestElectionsMap;
    }

    public double getDistanceFromRideStart(RideRequestDto rideRequest) {
        double deltaX = this.getX() - rideRequest.getStart().x;
        double deltaY = this.getY() - rideRequest.getStart().y;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    static public double getDistanceBetweenRideStartAndEnd(RideRequestDto rideRequest) {
        double deltaX = rideRequest.getEnd().x - rideRequest.getStart().x;
        double deltaY = rideRequest.getEnd().y - rideRequest.getStart().y;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    public Collection<NetworkTaxiConnection> getTaxiConnectionsInSameDistrict() {
        return this.networkTaxis.values().stream()
                .filter(conn -> conn.getRemoteTaxiDistrict().equals(this.getDistrict()))
                .collect(Collectors.toList());
    }

    public Optional<NetworkTaxiConnection> getNextDistrictTaxiConnection() {
        // I am synchronizing because I access twice to network takis in different places
        synchronized (this.networkTaxis) {
            Collection<Integer> districtTaxiIds = this.getTaxiIdsInSameDistrict();
            if (districtTaxiIds.isEmpty())
                return Optional.empty();
            List<Integer> sortedIds = districtTaxiIds.stream().sorted().collect(Collectors.toList());

            Optional<Integer> optNextId = sortedIds.stream().filter(id -> id > this.id).findFirst();
            int nextId = optNextId.orElseGet(() -> sortedIds.get(0));

            return Optional.of(this.networkTaxis.get(nextId));
        }
    }

    public Collection<Integer> getTaxiIdsInSameDistrict() {
        return this.networkTaxis.entrySet().stream()
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
        if (this.batteryLevel < this.taxiConfig.batteryThresholdBeforeRecharge)
            this.setStatus(TaxiStatus.WAITING_TO_RECHARGE);
        else
            this.setStatus(TaxiStatus.AVAILABLE);
    }

    public synchronized void setStatus(TaxiStatus status) {
        logger.info("Taxi {} goes from status {} to {}",
                this.id, this.status.toString(), status.toString());
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
            logger.info("Taxi {} started the grpc server", this.id);
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
        logger.info("Taxi {} registered to server", this.id);
    }

    void informOtherTaxisAboutEnteringTheNetwork() {
        List<Thread> notificationThreads = new ArrayList<>();
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            Thread notificationThread = new Thread(taxiConnection::sendAddTaxi);
            notificationThreads.add(notificationThread);
            notificationThread.start();
        }

        for (Thread notificationThread : notificationThreads) {
            try {
                notificationThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        logger.info("Taxi {} presented itself to the other taxis", this.id);
    }

    public void tryAccessTheStation() {
        this.rechargeAwaitingTaxiIds = new HashSet<>();
        this.localRechargeRequestTs = System.currentTimeMillis();
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.
        }
    }

    // synchronized on this object because it is a possible status changing method
    public synchronized void takeRideIfPossible(RideRequestDto rideRequest) {
        if (!this.getStatus().equals(TaxiStatus.AVAILABLE) ||
                !this.getDistrict().equals(District.fromPosition(rideRequest.getStart())))
            return;

        this.setStatus(TaxiStatus.DRIVING);
        logger.info("Taxi {} has taken ride {}", this.id, rideRequest.getId());
        synchronized (this.rideRequestElectionsMap) {
            this.rideRequestElectionsMap.get(rideRequest)
                    .setRideElectionState(RideElectionInfo.RideElectionState.ELECTED);
        }

        District oldDistrict = getDistrict();
        Collection<NetworkTaxiConnection> districtTaxiConnections = this.getTaxiConnectionsInSameDistrict();
        Thread rideTakenFinalizeThread = new Thread(() -> {
            // I am running them consequently because they are all async calls
            for (NetworkTaxiConnection conn : districtTaxiConnections)
                conn.sendMarkElectionConfirmed(rideRequest.getId(), this.id);

            takenRides.add(rideRequest.getId());
            setaPubSub.publishRideConfirmation(new RideConfirmDto(rideRequest.getId()));

            if (!District.fromPosition(rideRequest.getEnd()).equals(oldDistrict))
                unsubscribeFromDistrictTopic();
        });
        rideTakenFinalizeThread.start();

        try {
            Thread.sleep(this.taxiConfig.rideDeliveryDelay);
            rideTakenFinalizeThread.join();
        } catch (InterruptedException e) {
            logger.error("Sleep for ride is interrupted", e);
            throw new RuntimeException(e);
        }

        this.batteryLevel -= (getDistanceFromRideStart(rideRequest) +
                Taxi.getDistanceBetweenRideStartAndEnd(rideRequest)) * this.taxiConfig.batteryConsumptionPerKm;
        this.x = rideRequest.getEnd().x;
        this.y = rideRequest.getEnd().y;

        if (!oldDistrict.equals(this.getDistrict())) {
            this.rideRequestElectionsMap.clear();
            this.informOtherTaxisAboutDistrictChanged();
            this.subscribeToDistrictTopic();
        }
        // TODO: check if recharge
        if (this.batteryLevel < this.taxiConfig.batteryThresholdBeforeRecharge) {
            this.setStatus(TaxiStatus.RECHARGING);
        }
        else
            this.setStatus(TaxiStatus.AVAILABLE);
    }

    void informOtherTaxisAboutDistrictChanged() {
        List<Thread> notificationThreads = new ArrayList<>();
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            Thread notificationThread = new Thread(taxiConnection::sendChangeRemoteTaxiDistrict);
            notificationThreads.add(notificationThread);
            notificationThread.start();
        }

        for (Thread notificationThread : notificationThreads) {
            try {
                notificationThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        logger.info("Taxi {} informed the other taxis that it is now in the district {}",
                this.id, this.getDistrict());
    }

    void subscribeToDistrictTopic() {
        this.setaPubSub.subscribeToDistrictTopic(this.getDistrict(), (rideRequest) -> {
            // I want to add an entry in rideRequestElectionsMap only if available and
            synchronized (rideRequestElectionsMap) {
                synchronized (this) {
                    logger.info("Taxi {} received the ride request {}", this.id, rideRequest.getId());
                    if (!this.getStatus().equals(TaxiStatus.AVAILABLE) ||
                            (rideRequestElectionsMap.containsKey(rideRequest) &&
                                    rideRequestElectionsMap.get(rideRequest).getRideElectionState()
                                        .equals(RideElectionInfo.RideElectionState.ELECTED)))
                        return;

                    RideElectionInfo rideElectionInfo = new RideElectionInfo(
                            new RideElectionInfo.RideElectionId(this.id,
                                    this.getDistanceFromRideStart(rideRequest),
                                    this.batteryLevel),
                            RideElectionInfo.RideElectionState.ELECTION);
                    rideRequestElectionsMap.put(rideRequest, rideElectionInfo);

                    this.forwardRideElectionIdOrTakeRide(rideRequest, rideElectionInfo.getRideElectionId());
                }
            }
        });
        logger.info("Taxi {} subscribed to district topic {}", Taxi.this.id, Taxi.this.getDistrict());

        // I am assuming that SETA time and current taxi time are synchronized
        // TODO: necessary?
        this.subscriptionTs = System.currentTimeMillis();
    }

    public synchronized void forwardRideElectionIdOrTakeRide(RideRequestDto rideRequest,
                                                RideElectionInfo.RideElectionId rideElectionId) {
        boolean retry;
        do {
            Optional<NetworkTaxiConnection> optNextTaxiInRing = this.getNextDistrictTaxiConnection();
            if (optNextTaxiInRing.isPresent())
                retry = optNextTaxiInRing.get().sendForwardElectionIdOrTakeRide(rideRequest,
                        rideElectionId);
            else {
                retry = false;
                this.takeRideIfPossible(rideRequest);
            }
        } while (retry);
    }

    public void forwardRideElectionId(RideRequestDto rideRequest,
                                      RideElectionInfo.RideElectionId rideElectionId) {
        boolean retry;
        do {
            Optional<NetworkTaxiConnection> optNextTaxiInRing = this.getNextDistrictTaxiConnection();
            retry = optNextTaxiInRing.map(networkTaxiConnection -> networkTaxiConnection
                    .sendForwardElectionIdOrTakeRide(rideRequest,
                    rideElectionId)).orElse(false);
        } while (retry);
    }

    // synchronized on this object because it is a possibly status changing method
    public synchronized void goToRechargeStationIfPossible() {
//        synchronized ()
    }

    void unsubscribeFromDistrictTopic() {
        this.setaPubSub.unsubscribeFromDistrictTopic(this.getDistrict());
        logger.info("Taxi {} unsubscribed from district {}", this.id, this.getDistrict());
    }

    void informOtherTaxisAboutExitingFromTheNetwork() {
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.sendRemoveTaxi();
        }

        this.networkTaxis = SynchronizationUtils.makeSynchronized(Map.class,
                new HashMap<Integer, NetworkTaxiConnection>());
        logger.info("Taxi {} informed other taxis that it exited from the network", this.id);
    }

    private void unregisterFromServer() {
        this.adminService.unregisterTaxi(this.id);
        logger.info("Taxi {} unregistered from the server", this.id);
    }

    private void stopGRPCServer() {
        this.grpcServer.shutdown();
        logger.info("Taxi {} stopped its grpc server", this.id);
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

            this.informOtherTaxisAboutExitingFromTheNetwork();
            this.unsubscribeFromDistrictTopic();
            this.unregisterFromServer();
            this.stopGRPCServer();
        } finally {
            this.setStatus(TaxiStatus.UNSTARTED);
        }
    }

    public enum TaxiStatus {
        UNSTARTED(0),
        GRPC_STARTED(1),
        REGISTERED(2),
        AVAILABLE(3),
        WAITING_TO_RECHARGE(4),
        RECHARGING(5),
        DRIVING(6);

        private final int value;

        TaxiStatus(int value) {
            this.value = value;
        }
    }

    public static class TaxiConfig {
        private int rideDeliveryDelay = configurationManager.getRideDeliveryDelay();
        private int batteryConsumptionPerKm = configurationManager.getBatteryConsumptionPerKm();
        private int batteryThresholdBeforeRecharge = configurationManager.getBatteryThresholdBeforeRecharge();
        private int rechargeDelay = configurationManager.getRechargeDelay();
        private int initialBatteryLevel = 100;

        public TaxiConfig withRideDeliveryDelay(int rideDeliveryDelay) {
            this.rideDeliveryDelay = rideDeliveryDelay;
            return this;
        }

        public TaxiConfig withBatteryConsumptionPerKm(int batteryConsumptionPerKm) {
            this.batteryConsumptionPerKm = batteryConsumptionPerKm;
            return this;
        }

        public TaxiConfig withBatteryThresholdBeforeRecharge(int batteryThresholdBeforeRecharge) {
            this.batteryThresholdBeforeRecharge = batteryThresholdBeforeRecharge;
            return this;
        }

        public TaxiConfig withRechargeDelay(int rechargeDelay) {
            this.rechargeDelay = rechargeDelay;
            return this;
        }

        public TaxiConfig withInitialBatterylevel(int initialBatteryLevel) {
            this.initialBatteryLevel = initialBatteryLevel;
            return this;
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
        TaxiConfig taxiConfig = new TaxiConfig();

        final String RECHARGE_COMMAND = "recharge";
        final String QUIT_COMMAND = "quit";

        try (Taxi taxi = new Taxi(id, host, port, taxiConfig, adminService, setaTaxiPubSubBase)) {
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
}
