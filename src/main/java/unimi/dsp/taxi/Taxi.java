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
import unimi.dsp.model.types.election.RideElectionInfo;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.election.RideRequestMessage;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.model.types.TaxiStats;
import unimi.dsp.model.types.concurrency.ThreadSafeQueue;
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
    // x and y are volatile because they are never updated at the same time by different threads,
    // but they could be read by different threads
    private volatile int x;
    private volatile int y;
    // map that associate a taxi id with a connection to the corresponding taxi in the network
    private final Map<Integer, NetworkTaxiConnection> networkTaxis;
    private volatile TaxiStatus status;
    // recharging
    private volatile long localRechargeRequestTs;
    // I have approved the requests coming from these taxis. this means that I cannot recharge
    // until I get back an OK from them (if I have approved them it means they have the priority).
    private final Set<Integer> rechargeAwaitingTaxiIds;
    // the key of the outer map represents the ride request, while the key of the inner map
    // is the currently greater id of that election
    private final Map<RideRequestDto, RideElectionInfo> rideRequestElectionsMap;
    private final ThreadSafeQueue<RideRequestMessage> rideRequestMessages;
    private final RideRequestMessageProcessor rideRequestMessageProcessor;

    // statistics
    private Simulator pollutionDataProvider;
    private final Thread pollutionCollectingThread;
    private volatile double kmsTraveled = 0;
    private final Set<Integer> takenRides;
    // lock objects
    private final Object lockStats = new Object();
    private final Object lockTaxiPosition = new Object();

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
        this.networkTaxis = new HashMap<>();
        this.rideRequestElectionsMap = new HashMap<>();
        this.rideRequestMessages = new ThreadSafeQueue<>();
        this.rideRequestMessageProcessor = new RideRequestMessageProcessor();
        this.rechargeAwaitingTaxiIds = new HashSet<>();
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
        synchronized (this.lockTaxiPosition) {
            return new SmartCityPosition(this.x, this.y);
        }
    }

    public Map<Integer, NetworkTaxiConnection> getNetworkTaxiConnections() {
        return networkTaxis;
    }

    public ThreadSafeQueue<RideRequestMessage> getRideRequestMessagesQueue() {
        return rideRequestMessages;
    }

    public double getDistanceFromPosition(SmartCityPosition position) {
        double deltaX = this.getX() - position.x;
        double deltaY = this.getY() - position.y;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    public Collection<NetworkTaxiConnection> getTaxiConnectionsInSameDistrict() {
        District curDistrict = this.getDistrict();
        synchronized (this.networkTaxis) {
            return this.networkTaxis.values().stream()
                    .filter(conn -> conn.getRemoteTaxiDistrict().equals(curDistrict))
                    .collect(Collectors.toList());
        }
    }

    public Optional<NetworkTaxiConnection> getNextDistrictTaxiConnection() {
        District curDistrict = this.getDistrict();
        // I am synchronizing because I access twice to network takis in different places
        synchronized (this.networkTaxis) {
            Collection<Integer> districtTaxiIds = this.getDistrictTaxiIds(curDistrict);
            if (districtTaxiIds.isEmpty())
                return Optional.empty();
            List<Integer> sortedIds = districtTaxiIds.stream().sorted().collect(Collectors.toList());

            Optional<Integer> optNextId = sortedIds.stream().filter(id -> id > this.id).findFirst();
            int nextId = optNextId.orElseGet(() -> sortedIds.get(0));

            return Optional.of(this.networkTaxis.get(nextId));
        }
    }

    public Collection<Integer> getDistrictTaxiIds(District district) {
        synchronized (this.networkTaxis) {
            return this.networkTaxis.entrySet().stream()
                    .filter(entry -> entry.getValue().getRemoteTaxiDistrict().equals(district))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }

    // synchronized because I am accessing the ride election map
    public synchronized void resendElectionMessagesToDistrictNextConnection() {
        this.rideRequestElectionsMap.entrySet().stream()
                .filter(e -> e.getValue().getRideElectionState()
                        .equals(RideElectionInfo.RideElectionState.ELECTION))
                .forEach(e -> this.handleRideElectionId(e.getKey(), e.getValue().getRideElectionId()));
    }

    public void enterInSETANetwork() {
        this.startGRPCServer();
        this.setStatus(TaxiStatus.GRPC_STARTED);
        this.registerToServer();
        this.setStatus(TaxiStatus.REGISTERED);
        this.startCollectingPollutionData();
        this.informOtherTaxisAboutEnteringTheNetwork();
        this.rideRequestMessageProcessor.start();
        this.subscribeToDistrictTopic();
        if (this.batteryLevel < this.taxiConfig.batteryThresholdBeforeRecharge)
            this.setStatus(TaxiStatus.WAITING_TO_RECHARGE);
        else
            this.setStatus(TaxiStatus.AVAILABLE);
    }

    public TaxiStats readAndClearStatistics() {
        TaxiStats taxiStats;
        synchronized (this.lockStats) {
            taxiStats = new TaxiStats(this.kmsTraveled, this.takenRides.size());
            this.kmsTraveled = 0;
            takenRides.clear();
        }

        return taxiStats;
    }

    private void startCollectingPollutionData() {
        this.pollutionDataProvider.start();
        this.pollutionCollectingThread.start();
    }

    public synchronized void setStatus(TaxiStatus status) {
        logger.info("Taxi {} goes from status {} to {}",
                this.id, this.status.toString(), status.toString());
        this.status = status;
        // necessary to unlock a quit command if pending
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
        synchronized (this.networkTaxis) {
            for (TaxiInfoDto taxiInfoDto : newTaxi.getTaxiInfos()) {
                this.networkTaxis.put(taxiInfoDto.getId(), new NetworkTaxiConnection(this, taxiInfoDto));
            }
        }
        logger.info("Taxi {} registered to server", this.id);
    }

    void informOtherTaxisAboutEnteringTheNetwork() {
        NetworkTaxiConnection[] taxiConnections;
        synchronized (this.networkTaxis) {
            taxiConnections = this.networkTaxis.values().toArray(new NetworkTaxiConnection[0]);
        }
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(taxiConnections.length,
                (i) -> taxiConnections[i].sendAddTaxi());

        logger.info("Taxi {} presented itself to the other taxis", this.id);
    }

    public void askForTheRechargeStation() {
        synchronized (this) {
            if (!this.getStatus().equals(TaxiStatus.DRIVING) &&
                    !this.getStatus().equals(TaxiStatus.AVAILABLE))
                return;

            this.setStatus(TaxiStatus.WAITING_TO_RECHARGE);
        }

        this.rechargeAwaitingTaxiIds.clear();
        this.localRechargeRequestTs = System.currentTimeMillis();

        NetworkTaxiConnection[] taxiConnections = this.getTaxiConnectionsInSameDistrict()
                .toArray(new NetworkTaxiConnection[0]);
        List<Integer> taxiIdsToWait = new ArrayList<>();
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(taxiConnections.length, (i) -> {
            if (!taxiConnections[i].sendAskRechargeRequestApproval())
                taxiIdsToWait.add(taxiConnections[i].getRemoteTaxiId());
        });

        synchronized (this.rechargeAwaitingTaxiIds) {
            this.rechargeAwaitingTaxiIds.addAll(taxiIdsToWait);
        }

        this.accessTheRechargeStationIfPossible();
    }

    public void accessTheRechargeStationIfPossible() {
        if (!this.getStatus().equals(TaxiStatus.WAITING_TO_RECHARGE))
            return;

        synchronized (this.rechargeAwaitingTaxiIds) {
            if (!this.rechargeAwaitingTaxiIds.isEmpty())
                return;
        }

        this.setStatus(TaxiStatus.RECHARGING);
        double distanceFromRechargeStation = this.getDistanceFromPosition(
                this.getDistrict().getRechargeStationPosition());
        synchronized (this.lockStats) {
            this.kmsTraveled += distanceFromRechargeStation;
        }
        SmartCityPosition rechargeStationPosition = this.getDistrict().getRechargeStationPosition();
        this.batteryLevel -= (distanceFromRechargeStation * this.taxiConfig.batteryConsumptionPerKm);
        synchronized (this.lockTaxiPosition) {
            this.x = rechargeStationPosition.x;
            this.y = rechargeStationPosition.y;
        }
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(this.taxiConfig.rechargeDelay);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            this.batteryLevel = this.taxiConfig.initialBatteryLevel;
            this.informOtherTaxisRechargeStationIsFree();
            this.setStatus(TaxiStatus.AVAILABLE);
        });
        t.start();
    }

    public void informOtherTaxisRechargeStationIsFree() {
        NetworkTaxiConnection[] taxiConnections = this.getTaxiConnectionsInSameDistrict()
                .toArray(new NetworkTaxiConnection[0]);
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(taxiConnections.length,
                (i) -> taxiConnections[i].sendUpdateRechargeRequestApproval());
    }

    public void takeRide(RideRequestDto rideRequest) {
        assert Thread.holdsLock(this) && this.getStatus().equals(TaxiStatus.AVAILABLE);

        this.setStatus(TaxiStatus.DRIVING);

        logger.info("Taxi {} has taken ride {}", this.id, rideRequest.getId());
        // this map is accessed only by the message processor, so I do not need to synchronize it
        this.rideRequestElectionsMap.get(rideRequest)
            .setRideElectionState(RideElectionInfo.RideElectionState.ELECTED);
        setaPubSub.publishRideConfirmation(new RideConfirmDto(rideRequest.getId()));

        NetworkTaxiConnection[] districtTaxiConnections = this.getTaxiConnectionsInSameDistrict()
                .toArray(new NetworkTaxiConnection[0]);
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(districtTaxiConnections.length,
                (i) -> districtTaxiConnections[i].sendMarkElectionConfirmed(rideRequest.getId(), this.id));

        District oldDistrict = this.getDistrict();
        if (!District.fromPosition(rideRequest.getEnd()).equals(oldDistrict))
            unsubscribeFromDistrictTopic();
        Thread rideSimulation = new Thread(() -> {
            try {
                Thread.sleep(this.taxiConfig.rideDeliveryDelay);
            } catch (InterruptedException e) {
                logger.error("taxi riding is interrupted", e);
                throw new RuntimeException(e);
            }

            double rideDistance = (this.getDistanceFromPosition(rideRequest.getStart()) +
                    rideRequest.getDistanceBetweenRideStartAndEnd());
            synchronized (this.lockStats) {
                this.kmsTraveled += rideDistance;
                this.takenRides.add(rideRequest.getId());
            }
            // I do not need to synchronize on battery level because it can be modified while driving
            // or recharging, that do not happen in parallel
            this.batteryLevel -= rideDistance * this.taxiConfig.batteryConsumptionPerKm;
            synchronized (this.lockTaxiPosition) {
                this.x = rideRequest.getEnd().x;
                this.y = rideRequest.getEnd().y;
            }

            synchronized (this) {
                if (!oldDistrict.equals(this.getDistrict()))
                    this.rideRequestElectionsMap.clear();
                else
                    this.removeNonElectedRideRequests();
            }

            if (!oldDistrict.equals(this.getDistrict())) {
                this.rideRequestMessages.removeAll();
                this.informOtherTaxisAboutDistrictChanged();
                this.subscribeToDistrictTopic();
            }

            if (this.batteryLevel < this.taxiConfig.batteryThresholdBeforeRecharge)
                this.askForTheRechargeStation();
            else
                this.setStatus(TaxiStatus.AVAILABLE);
        });
        rideSimulation.start();
    }

    private void removeNonElectedRideRequests() {
        assert Thread.holdsLock(this) && this.getStatus().equals(TaxiStatus.DRIVING);
        List<RideRequestDto> rideRequestsToDelete = new ArrayList<>();
        for (Map.Entry<RideRequestDto, RideElectionInfo> entry : this.rideRequestElectionsMap.entrySet()) {
            if (entry.getValue().getRideElectionState().equals(RideElectionInfo.RideElectionState.ELECTION))
                rideRequestsToDelete.add(entry.getKey());
        }

        rideRequestsToDelete.forEach(this.rideRequestElectionsMap.keySet()::remove);
    }

    void informOtherTaxisAboutDistrictChanged() {
        NetworkTaxiConnection[] taxiConnections;
        synchronized (this.networkTaxis) {
            taxiConnections = this.networkTaxis.values().toArray(new NetworkTaxiConnection[0]);
        }
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(taxiConnections.length,
                (i) -> taxiConnections[i].sendChangeRemoteTaxiDistrict());

        logger.info("Taxi {} informed the other taxis that it is now in the district {}",
                this.id, this.getDistrict());
    }

    void subscribeToDistrictTopic() {
        this.setaPubSub.subscribeToDistrictTopic(this.getDistrict(), (rideRequest) ->
                this.rideRequestMessages.put(new RideRequestMessage(
                    new RideElectionInfo(
                            new RideElectionInfo.RideElectionId(this.id,
                                    this.getDistanceFromPosition(rideRequest.getStart()),
                                    this.batteryLevel),
                            RideElectionInfo.RideElectionState.ELECTION),
                    rideRequest)));
        logger.info("Taxi {} subscribed to district topic {}", Taxi.this.id, Taxi.this.getDistrict());
    }

    public void handleRideElectionId(RideRequestDto rideRequest,
                                     RideElectionInfo.RideElectionId rideElectionId) {
        assert Thread.holdsLock(this);
        boolean retry;
        do {
            Optional<NetworkTaxiConnection> optNextTaxiInRing = this.getNextDistrictTaxiConnection();
            if (optNextTaxiInRing.isPresent())
                retry = optNextTaxiInRing.get().sendForwardElectionIdOrTakeRide(rideRequest,
                        rideElectionId);
            else {
                retry = false;
                if (this.getStatus().equals(TaxiStatus.AVAILABLE))
                    this.takeRide(rideRequest);
            }
        } while (retry);
    }

    void unsubscribeFromDistrictTopic() {
        District curDistrict = this.getDistrict();
        this.setaPubSub.unsubscribeFromDistrictTopic(curDistrict);
        logger.info("Taxi {} unsubscribed from district {}", this.id, curDistrict);
    }

    void informOtherTaxisAboutExitingFromTheNetwork() {
        NetworkTaxiConnection[] taxiConnections;
        synchronized (this.networkTaxis) {
            taxiConnections = this.networkTaxis.values().toArray(new NetworkTaxiConnection[0]);
        }
        ConcurrencyUtils.runThreadsConcurrentlyAndJoin(taxiConnections.length,
                (i) -> taxiConnections[i].sendRemoveTaxi());

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

                // I need to do this because some taxis might be waiting for this OK, even if in WAITING_TO_RECHARGE
                this.informOtherTaxisRechargeStationIsFree();
                this.informOtherTaxisAboutExitingFromTheNetwork();
            }

            this.rideRequestMessageProcessor.interrupt();
            this.pollutionDataProvider.stopMeGently();
            this.pollutionCollectingThread.interrupt();
            this.unsubscribeFromDistrictTopic();
            synchronized (this.networkTaxis) {
                for (NetworkTaxiConnection conn : this.networkTaxis.values())
                    conn.close();
                this.networkTaxis.clear();
            }
            this.stopGRPCServer();
            this.unregisterFromServer();
        } finally {
            this.setStatus(TaxiStatus.UNSTARTED);
        }
    }

    private class RideRequestMessageProcessor extends Thread {
        @Override
        public void run() {
            try {
                while (!this.isInterrupted()) {
                    RideRequestMessage message = Taxi.this.rideRequestMessages.take();
                    RideRequestDto rideRequest = message.getRideRequest();
                    RideElectionInfo rideElectionInfo = message.getRideElectionInfo();

                    Taxi.logger.info("Taxi {} received the ride request {}",
                            Taxi.this.id, rideRequest.getId());

                    // if the district is different I do not want to process this request so respond
                    // with retry, otherwise I process it
//                    if (!District.fromPosition(rideRequest.getStart()).equals(Taxi.this.getDistrict())) {
//                        message.getSender().ifPresent(s -> s.respondWithRetry(true));
//                        continue;
//                    }
//                    else
//                        message.getSender().ifPresent(s -> s.respondWithRetry(false));

                    // I synchronize on taxi so that the status cannot change to recharging and
                    // the ride election map is not cleared in the meanwhile
                    synchronized (Taxi.this) {
                        // if the district is different I do not want to process this request so continue
                        if (!District.fromPosition(rideRequest.getStart()).equals(Taxi.this.getDistrict()))
                            continue;

                        if (Taxi.this.rideRequestElectionsMap.containsKey(rideRequest) &&
                                Taxi.this.rideRequestElectionsMap.get(rideRequest).getRideElectionState()
                                        .equals(RideElectionInfo.RideElectionState.ELECTED))
                            continue;

                        if (rideElectionInfo.getRideElectionState().equals(RideElectionInfo.RideElectionState.ELECTED)) {
                            Taxi.this.rideRequestElectionsMap.put(rideRequest, rideElectionInfo);
                            this.restartElectionsAssociatedToElectedTaxi(rideRequest,
                                    rideElectionInfo.getRideElectionId().getTaxiId());
                            continue;
                        }

                        if (Taxi.this.getStatus().equals(Taxi.TaxiStatus.AVAILABLE)) {
                            if (Taxi.this.rideRequestElectionsMap.containsKey(rideRequest))
                                electionLogicWhenAlreadyParticipant(rideRequest, rideElectionInfo.getRideElectionId());
                            else
                                electionLogicWhenNotParticipant(rideRequest, rideElectionInfo.getRideElectionId());
                        } else
                            electionLogicWhenUnavailable(rideRequest, rideElectionInfo.getRideElectionId());
                    }
                }
            } catch (InterruptedException e) {
                // the thread terminated during close()
            }
        }

        private void restartElectionsAssociatedToElectedTaxi(RideRequestDto rideRequest, int electedTaxiId) {
            // if the taxi winning the election is the greater id in an ongoing
            // election, then that election is restarted
            for (Map.Entry<RideRequestDto, RideElectionInfo> rideElectionEntry :
                    Taxi.this.rideRequestElectionsMap.entrySet()) {
                if (!rideElectionEntry.getValue().getRideElectionState()
                        .equals(RideElectionInfo.RideElectionState.ELECTED) &&
                        rideElectionEntry.getValue().getRideElectionId().getTaxiId() == electedTaxiId) {
                    RideElectionInfo.RideElectionId newRideElectionId = this.createElectionIdFromRideRequest(
                            rideElectionEntry.getKey());
                    rideElectionEntry.getValue().setRideElectionId(newRideElectionId);
                    Taxi.this.handleRideElectionId(rideElectionEntry.getKey(), newRideElectionId);
                    logger.info("Election for ride {} is restarted by taxi {}",
                            rideRequest.getId(), Taxi.this.getId());
                }
            }
        }

        private void electionLogicWhenAlreadyParticipant(RideRequestDto rideRequest,
                                                         RideElectionInfo.RideElectionId receivedElectionId) {
            RideElectionInfo thisRideElectionInfo = Taxi.this.rideRequestElectionsMap.get(rideRequest);
            // forward if the election info id is greater
            if (receivedElectionId.isGreaterThan(thisRideElectionInfo.getRideElectionId())) {
                thisRideElectionInfo.setRideElectionId(receivedElectionId);
                Taxi.this.handleRideElectionId(rideRequest, receivedElectionId);
            } else if (receivedElectionId.equals(thisRideElectionInfo.getRideElectionId())) {
                // take the ride if the received election id is the same as the stored one and the taxi id
                // is the same too
                if (thisRideElectionInfo.getRideElectionId().getTaxiId() == Taxi.this.getId())
                    Taxi.this.takeRide(rideRequest);
                else
                    // otherwise restart the election because the token already completed the ring
                    // but no taxi started the elected phase
                    Taxi.this.handleRideElectionId(rideRequest,
                            createElectionIdFromRideRequest(rideRequest));
            }
        }

        private void electionLogicWhenNotParticipant(RideRequestDto rideRequest,
                                                     RideElectionInfo.RideElectionId receivedElectionId) {
            // not a participant yet, forward the received request or the current election info
            // based on the greater id
            RideElectionInfo.RideElectionId thisRideElectionId = this.createElectionIdFromRideRequest(rideRequest);
            // it cannot be equal, otherwise there entry would exist already
            RideElectionInfo.RideElectionId winningRideElectionId =
                    thisRideElectionId.isGreaterThan(receivedElectionId) ?
                            thisRideElectionId : receivedElectionId;

            Taxi.this.rideRequestElectionsMap.put(rideRequest, new RideElectionInfo(
                    winningRideElectionId, RideElectionInfo.RideElectionState.ELECTION
            ));
            Taxi.this.handleRideElectionId(rideRequest, winningRideElectionId);
        }

        private RideElectionInfo.RideElectionId createElectionIdFromRideRequest(RideRequestDto rideRequest) {
            return new RideElectionInfo.RideElectionId(
                    Taxi.this.getId(),
                    Taxi.this.getDistanceFromPosition(rideRequest.getStart()),
                    Taxi.this.getBatteryLevel());
        }

        private void electionLogicWhenUnavailable(RideRequestDto rideRequest,
                                                  RideElectionInfo.RideElectionId receivedElectionId) {
            if (!Taxi.this.rideRequestElectionsMap.containsKey(rideRequest)) {
                Taxi.this.rideRequestElectionsMap.put(rideRequest, new RideElectionInfo(
                        receivedElectionId, RideElectionInfo.RideElectionState.ELECTION
                ));
                Taxi.this.handleRideElectionId(rideRequest, receivedElectionId);
            }
            else {
                RideElectionInfo thisRideElectionInfo = Taxi.this.rideRequestElectionsMap.get(rideRequest);
                // I forward the message only if the taxi id is different from this taxi id and
                // it is not the same taxi id stored (because it means that no taxi can take it after
                // having completed the ring)
                if (!receivedElectionId.equals(thisRideElectionInfo.getRideElectionId()) &&
                        receivedElectionId.getTaxiId() != Taxi.this.getId())
                    Taxi.this.handleRideElectionId(rideRequest, receivedElectionId);
            }
        }
    }

    public enum TaxiStatus {
        UNSTARTED,
        GRPC_STARTED,
        REGISTERED,
        AVAILABLE,
        WAITING_TO_RECHARGE,
        RECHARGING,
        DRIVING
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
                        while (!taxi.getStatus().equals(TaxiStatus.AVAILABLE))
                            taxi.wait();
                        if (taxi.getStatus().equals(TaxiStatus.WAITING_TO_RECHARGE) ||
                                taxi.getStatus().equals(TaxiStatus.RECHARGING)) {
                            System.out.println("The taxi is recharging or is waiting for it");
                            continue;
                        }
                        taxi.askForTheRechargeStation();
                        System.out.println("Taxi will go to the recharge station as soon as possible");
                    }
                }
                else {
                    System.out.println("The command " + command + " is not available");
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            mqttClient.disconnect().waitForCompletion();
            mqttClient.close();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }
}
