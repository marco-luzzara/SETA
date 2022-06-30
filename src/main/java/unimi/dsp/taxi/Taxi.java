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
import unimi.dsp.model.RideElectionInfo;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.sensors.Buffer;
import unimi.dsp.sensors.SlidingWindowBuffer;
import unimi.dsp.sensors.simulators.PM10Simulator;
import unimi.dsp.sensors.simulators.Simulator;
import unimi.dsp.taxi.services.grpc.TaxiService;
import unimi.dsp.taxi.services.mqtt.SETATaxiPubSub;
import unimi.dsp.taxi.services.rest.AdminService;
import unimi.dsp.taxi.services.stats.StatsCollectorThread;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;
import unimi.dsp.util.ConcurrencyUtils;

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
    private volatile int batteryLevel;
    // x and y are volatile because they are never updated at the same time by different threads
    // but they could be read by different threads
    private volatile int x;
    private volatile int y;
    // map that associate a taxi id with a connection to the corresponding taxi in the network
    private Map<Integer, NetworkTaxiConnection> networkTaxis;
    private TaxiStatus status;
    // recharging
    private long localRechargeRequestTs;
    // I have approved the requests coming from these taxis. this means that I cannot recharge
    // until I get back an OK from them (if I have approved them it means they have the priority).
    private Set<Integer> rechargeAwaitingTaxiIds;
    // the key of the outer map represents the ride request, while the key of the inner map
    // is the currently greater Id of that election
    private final Map<RideRequestDto, RideElectionInfo> rideRequestElectionsMap;

    // statistics
    private Simulator pollutionDataProvider;
    private Thread pollutionCollectingThread;
    private volatile double kmsTraveled = 0;
    private final Set<Integer> takenRides;

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
        this.takenRides = new HashSet<>();
        this.status = TaxiStatus.UNSTARTED;
        this.networkTaxis = ConcurrencyUtils.makeSynchronized(Map.class,
                new HashMap<Integer, NetworkTaxiConnection>());
        this.rideRequestElectionsMap = ConcurrencyUtils.makeSynchronized(Map.class,
                new HashMap<RideRequestDto, RideElectionInfo>());
        Buffer buffer = new SlidingWindowBuffer(taxiConfig.slidingWindowBufferSize,
                taxiConfig.slidingWindowOverlappingFactor);
        this.initializeSimulator(buffer);
        this.pollutionCollectingThread = new StatsCollectorThread(buffer,
                taxiConfig.statsLoadingDelay, this, this.adminService);
    }

    void initializeSimulator(Buffer buffer) {
        this.pollutionDataProvider = new PM10Simulator(buffer);
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

    public double getKmsTraveled() {
        return kmsTraveled;
    }

    public long getLocalRechargeRequestTs() {
        assert this.status.equals(TaxiStatus.WAITING_TO_RECHARGE) ||
                this.status.equals(TaxiStatus.RECHARGING);
        return localRechargeRequestTs;
    }

    public Set<Integer> getRechargeAwaitingTaxiIds() {
        return rechargeAwaitingTaxiIds;
    }

    public District getDistrict() {
        return District.fromPosition(this.getPosition());
    }
    public SmartCityPosition getPosition() {
        return new SmartCityPosition(this.x, this.y);
    }

    public Map<Integer, NetworkTaxiConnection> getNetworkTaxiConnections() {
        return networkTaxis;
    }

    public Map<RideRequestDto, RideElectionInfo> getRideRequestElectionsMap() {
        return rideRequestElectionsMap;
    }

    public double getDistanceFromPosition(SmartCityPosition position) {
        double deltaX = this.getX() - position.x;
        double deltaY = this.getY() - position.y;
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
        this.startCollectingPollutionData();
        this.informOtherTaxisAboutEnteringTheNetwork();
        this.subscribeToDistrictTopic();
        if (this.batteryLevel < this.taxiConfig.batteryThresholdBeforeRecharge)
            this.setStatus(TaxiStatus.WAITING_TO_RECHARGE);
        else
            this.setStatus(TaxiStatus.AVAILABLE);
    }

    public void clearStatistics() {
        // I do not need to synchronize in this because it is volatile and assignment is
        // not based on previous state of object
        this.kmsTraveled = 0;

        synchronized (this.takenRides) {
            takenRides.clear();
        }
    }

    private void startCollectingPollutionData() {
        this.pollutionDataProvider.start();
        this.pollutionCollectingThread.start();
    }

    public synchronized void setStatus(TaxiStatus status) {
        logger.info("Taxi {} goes from status {} to {}",
                this.id, this.status.toString(), status.toString());
        this.status = status;
        // necessary to unlock a quit or recharge command if pending
        if (!this.status.equals(TaxiStatus.RECHARGING) && !this.status.equals(TaxiStatus.DRIVING))
            this.notify();
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

    public synchronized void askForTheRechargeStation() {
        if (this.getStatus().equals(TaxiStatus.WAITING_TO_RECHARGE) ||
                this.getStatus().equals(TaxiStatus.RECHARGING))
            return;

        this.setStatus(TaxiStatus.WAITING_TO_RECHARGE);

        this.rechargeAwaitingTaxiIds = new HashSet<>();
        this.localRechargeRequestTs = System.currentTimeMillis();
        NetworkTaxiConnection[] taxiConnections = this.networkTaxis.values().toArray(new NetworkTaxiConnection[0]);
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(taxiConnections.length, (i) -> {
            taxiConnections[i].sendAskRechargeRequestApproval();
        });

        this.accessTheRechargeStationIfPossible();
    }

    public synchronized void accessTheRechargeStationIfPossible() {
        if (!this.getStatus().equals(TaxiStatus.WAITING_TO_RECHARGE))
            return;

        synchronized (this.getRechargeAwaitingTaxiIds()) {
            if (!this.getRechargeAwaitingTaxiIds().isEmpty())
                return;
        }

        this.setStatus(TaxiStatus.RECHARGING);
        double distanceFromRechargeStation = this.getDistanceFromPosition(
                this.getDistrict().getRechargeStationPosition());
        this.kmsTraveled += distanceFromRechargeStation;
        this.batteryLevel -= distanceFromRechargeStation *
                this.taxiConfig.batteryConsumptionPerKm;
        SmartCityPosition rechargeStationPosition = this.getDistrict().getRechargeStationPosition();
        this.x = rechargeStationPosition.x;
        this.y = rechargeStationPosition.y;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(this.taxiConfig.rechargeDelay);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // synchronize the entire block because I do not want the taxi to exit
            // before informing the other taxis too
            synchronized (this) {
                this.batteryLevel = this.taxiConfig.initialBatteryLevel;
                this.setStatus(TaxiStatus.AVAILABLE);
                this.informOtherTaxisRechargeStationIsFree();
            }
        });
        t.start();
    }

    public void informOtherTaxisRechargeStationIsFree() {
        NetworkTaxiConnection[] taxiConnections = this.networkTaxis.values().toArray(new NetworkTaxiConnection[0]);
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(taxiConnections.length, (i) -> {
            taxiConnections[i].sendUpdateRechargeRequestApproval();
        });
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

            this.takenRides.add(rideRequest.getId());
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

        double rideDistance = (this.getDistanceFromPosition(rideRequest.getStart()) +
                Taxi.getDistanceBetweenRideStartAndEnd(rideRequest));
        this.kmsTraveled += rideDistance;
        this.batteryLevel -= rideDistance * this.taxiConfig.batteryConsumptionPerKm;
        this.x = rideRequest.getEnd().x;
        this.y = rideRequest.getEnd().y;

        if (!oldDistrict.equals(this.getDistrict())) {
            this.rideRequestElectionsMap.clear();
            this.informOtherTaxisAboutDistrictChanged();
            this.subscribeToDistrictTopic();
        }
        // TODO: check if recharge
        if (this.batteryLevel < this.taxiConfig.batteryThresholdBeforeRecharge)
            this.askForTheRechargeStation();
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
                                    this.getDistanceFromPosition(rideRequest.getStart()),
                                    this.batteryLevel),
                            RideElectionInfo.RideElectionState.ELECTION);
                    rideRequestElectionsMap.put(rideRequest, rideElectionInfo);

                    this.forwardRideElectionIdOrTakeRide(rideRequest, rideElectionInfo.getRideElectionId());
                }
            }
        });
        logger.info("Taxi {} subscribed to district topic {}", Taxi.this.id, Taxi.this.getDistrict());
    }

    public void forwardRideElectionIdOrTakeRide(RideRequestDto rideRequest,
                                                RideElectionInfo.RideElectionId rideElectionId) {
        boolean retry;
        do {
            if (!District.fromPosition(rideRequest.getStart()).equals(this.getDistrict()))
                return;
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
            if (!District.fromPosition(rideRequest.getStart()).equals(this.getDistrict()))
                return;
            Optional<NetworkTaxiConnection> optNextTaxiInRing = this.getNextDistrictTaxiConnection();
            retry = optNextTaxiInRing.map(networkTaxiConnection -> networkTaxiConnection
                    .sendForwardElectionIdOrTakeRide(rideRequest,
                    rideElectionId)).orElse(false);
        } while (retry);
    }

    void unsubscribeFromDistrictTopic() {
        this.setaPubSub.unsubscribeFromDistrictTopic(this.getDistrict());
        logger.info("Taxi {} unsubscribed from district {}", this.id, this.getDistrict());
    }

    void informOtherTaxisAboutExitingFromTheNetwork() {
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.sendRemoveTaxi();
        }

        this.networkTaxis = ConcurrencyUtils.makeSynchronized(Map.class,
                new HashMap<Integer, NetworkTaxiConnection>());
        logger.info("Taxi {} informed other taxis that it exited from the network", this.id);
    }

    private void unregisterFromServer() {
        this.adminService.unregisterTaxi(this.id);
        logger.info("Taxi {} unregistered from the server", this.id);
    }

    private void stopGRPCServer() {
        try {
            this.grpcServer.shutdown().awaitTermination();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

            synchronized (this) {
                while (this.getStatus().equals(TaxiStatus.RECHARGING) ||
                        this.getStatus().equals(TaxiStatus.DRIVING)) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            this.pollutionDataProvider.stopMeGently();
            this.pollutionCollectingThread.interrupt();
            this.informOtherTaxisRechargeStationIsFree();
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
        private int slidingWindowBufferSize = configurationManager.getSlidingWindowBufferSize();
        private float slidingWindowOverlappingFactor = configurationManager.getSlidingWindowOverlappingFactor();
        private int statsLoadingDelay = configurationManager.getStatsLoadingDelay();
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

        public TaxiConfig withInitialBatteryLevel(int initialBatteryLevel) {
            this.initialBatteryLevel = initialBatteryLevel;
            return this;
        }

        public TaxiConfig withSlidingWindowBufferSize(int slidingWindowBufferSize) {
            this.slidingWindowBufferSize = slidingWindowBufferSize;
            return this;
        }

        public TaxiConfig withSlidingWindowOverlappingFactor(float slidingWindowOverlappingFactor) {
            this.slidingWindowOverlappingFactor = slidingWindowOverlappingFactor;
            return this;
        }

        public TaxiConfig withStatsLoadingDelay(int statsLoadingDelay) {
            this.statsLoadingDelay = statsLoadingDelay;
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
                    break;
                }
                else if (command.equals(RECHARGE_COMMAND)) {
                    synchronized (taxi) {
                        if (taxi.getStatus().equals(TaxiStatus.WAITING_TO_RECHARGE) ||
                                taxi.getStatus().equals(TaxiStatus.RECHARGING)) {
                            System.out.println("The taxi is recharging or is waiting for it");
                        }
                        taxi.askForTheRechargeStation();
                        System.out.println("Taxi will go to the recharge station as soon as possible");
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
