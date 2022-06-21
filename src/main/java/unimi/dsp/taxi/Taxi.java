package unimi.dsp.taxi;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.eclipse.paho.client.mqttv3.*;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.taxi.grpc.services.TaxiService;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;
import unimi.dsp.util.RestUtil;
import unimi.dsp.util.SerializationUtil;

import javax.ws.rs.core.Response;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Taxi implements Closeable  {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final String RIDE_REQUEST_TOPIC_PREFIX = configurationManager.getRideRequestTopicPrefix();
    private static final String RIDE_CONFIRM_TOPIC_SUFFIX = configurationManager.getRideConfirmationTopicSuffix();
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

    // for ride request processing
    private Optional<Integer> lastElectionId;
    private long subscriptionTs;
    // the key of the outer map represents the ride request election, while the key of the inner map
    // is the taxiId associated to the OK-like message for the ride approval
    private final Map<RideRequestDto, Map<Integer, Boolean>> rideRequestElectionsMap;

    // for grpc communication
    private Server grpcServer;

    // for mqtt communication
    private final MqttAsyncClient mqttClient;

    // for admin server communication
    private final Client restClient;
    private final String serverEndpoint = configurationManager.getAdminServerEndpoint();

    public Taxi(int id, String host, int port,
                int rideDeliveryDelay,
                int batteryConsumptionPerKm,
                int batteryThresholdBeforeRecharge,
                int rechargeDelay) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.rideDeliveryDelay = rideDeliveryDelay;
        this.batteryConsumptionPerKm = batteryConsumptionPerKm;
        this.batteryThresholdBeforeRecharge = batteryThresholdBeforeRecharge;
        this.rechargeDelay = rechargeDelay;
        this.batteryLevel = 100;
        this.status = TaxiStatus.UNSTARTED;
        this.networkTaxis = new HashMap<>();
        this.rideRequestElectionsMap = new HashMap<>();

        // for rest register and unregister
        ClientConfig config = new DefaultClientConfig();
        config.getClasses().add(JacksonJaxbJsonProvider.class);
        this.restClient = Client.create(config);

        this.mqttClient = MQTTClientFactory.getClient();
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

    public void enterInSETANetwork() throws MqttException {
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
        ClientResponse response = RestUtil.postRequest(restClient, serverEndpoint + "/taxis",
                new TaxiInfoDto(this.id, this.host, this.port));
        if (response.getStatus() == Response.Status.CONFLICT.getStatusCode())
            throw new IllegalStateException("Taxi cannot register because another taxi has the same id");
        NewTaxiDto newTaxi = response.getEntity(new GenericType<NewTaxiDto>() {
        });
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

    // synchronize on this object because it is a possibly status changing method
    public synchronized void takeRideIfPossible(RideRequestDto rideRequest) {
        Map<RideRequestDto, Map<Integer, Boolean>> rideRequestsMap = this.getRideRequestElectionsMap();
        synchronized (rideRequestsMap) {
            if (!rideRequestsMap.containsKey(rideRequest) ||
                    // check whether there is at least one false response from the other taxis
                    rideRequestsMap.get(rideRequest).values().stream().anyMatch(b -> !b) ||
                    !this.getStatus().equals(TaxiStatus.AVAILABLE))
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

        // confirm to seta through publish

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

    private class RideRequestReceiveCallback implements MqttCallback {
        @Override
        public void connectionLost(Throwable cause) {
            logger.error(cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            if (!topic.startsWith(RIDE_REQUEST_TOPIC_PREFIX))
                return;

            RideRequestDto rideRequest = SerializationUtil.deserialize(message.getPayload(), RideRequestDto.class);
            synchronized (rideRequestElectionsMap) {
                Map<Integer, Boolean> rideRequestMap = new HashMap<>();
                for (Integer taxiId : getTaxiIdsInSameDistrict()) {
                    rideRequestMap.put(taxiId, false);
                    networkTaxis.get(taxiId).sendAskRideRequestApproval(rideRequest);
                }

                rideRequestElectionsMap.put(rideRequest, rideRequestMap);
                rideRequestElectionsMap.notifyAll();
            }

            rideRequestElectionsMap.put(rideRequest, new HashMap<>());
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    void subscribeToDistrictTopic() throws MqttException {
        String district = this.getDistrict().toString();
        this.mqttClient.setCallback(new RideRequestReceiveCallback());
        this.lastElectionId = Optional.empty();

        this.mqttClient.subscribe(RIDE_REQUEST_TOPIC_PREFIX + "/district" + district, 1)
                .waitForCompletion();
        // I am assuming that SETA time and current taxi time are synchronized
        this.subscriptionTs = System.currentTimeMillis();
    }

    void unsubscribeFromDistrictTopic() {
        String district = this.getDistrict().toString();
        try {
            this.mqttClient.unsubscribe(RIDE_REQUEST_TOPIC_PREFIX + "/district" + district)
                    .waitForCompletion();
        } catch (MqttException e) {
            logger.error("Taxi with id " + this.getId() + " cannot unsubscribe", e);
            throw new RuntimeException(e);
        }
    }

    void informOtherTaxisAboutExitingFromTheNetwork() {
        for (NetworkTaxiConnection taxiConnection : this.networkTaxis.values()) {
            taxiConnection.sendRemoveTaxi();
        }

        this.networkTaxis = new HashMap<>();
    }

    private void unregisterFromServer() {
        String serverEndpoint = ConfigurationManager.getInstance().getAdminServerEndpoint();

        ClientResponse response = RestUtil.sendDeleteRequest(restClient,
                serverEndpoint + "/taxis/" + this.id);
        int statusCode = response.getStatus();
        if (statusCode != Response.Status.OK.getStatusCode())
            throw new IllegalStateException("The taxi did not unregister correctly, status code: " + statusCode);
    }

    private void stopGRPCServer() {
        this.grpcServer.shutdown();
    }

    @Override
    public void close() {
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

            this.stopGRPCServer();
            this.unregisterFromServer();
            this.informOtherTaxisAboutExitingFromTheNetwork();
            this.unsubscribeFromDistrictTopic();
        } finally {
            this.setStatus(TaxiStatus.UNSTARTED);
        }
    }

    public static void main(String[] args) {

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
