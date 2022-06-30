package unimi.dsp.SETA;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import unimi.dsp.SETA.services.SETAServerPubSub;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;

import java.io.Closeable;
import java.util.*;

public class SetaSystem implements Closeable {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final Logger logger = LogManager.getLogger(SetaSystem.class.getName());

    private final RideGenerator rideGenerator;
    private final SETAConfig setaConfig;
    private final SETAServerPubSubBase setaServerPubSub;
    private final Map<Integer, Set<RideRequestDto>> districtNewRequestsMap;
    private final Set<Integer> pendingRideConfirmations;
    private final List<Thread> workingThreads = new ArrayList<>();

    /**
     * Create a SETA system
     * @param rideGenerator generates the starting and destination position for a ride
     * @param setaConfig configuration properties for seta
     * @param setaServerPubSub pubsub implementation
     */
    public SetaSystem(RideGenerator rideGenerator,
                      SETAConfig setaConfig,
                      SETAServerPubSubBase setaServerPubSub) {
        this.rideGenerator = rideGenerator;
        this.setaConfig = setaConfig;
        this.setaServerPubSub = setaServerPubSub;

        this.districtNewRequestsMap = new HashMap<>();
        this.pendingRideConfirmations = new HashSet<>();
        for (int i = 1; i <= configurationManager.getNumDistricts(); i++) {
            this.districtNewRequestsMap.put(i, new HashSet<>());
        }
    }

    public void run() throws MqttException {
        this.subscribeToRideConfirmations();
        this.startThreadsToPublishRideRequests();
        int curId = 0;

        try {
            while (!Thread.currentThread().isInterrupted() &&
                    (this.setaConfig.requestLimit == 0 || curId < this.setaConfig.requestLimit)) {
                for (int i = 0; i < this.setaConfig.numGeneratedRequest; i++) {
                    RideRequestDto rideRequest = this.rideGenerator.generateRide();
                    addToNewRideRequests(rideRequest);
                    curId++;

                    if (curId == this.setaConfig.requestLimit)
                        return;
                }

                Thread.sleep(this.setaConfig.genFrequencyMillis);
            }
        }
        catch (InterruptedException e) {
        }
    }

    @Override
    public void close() {
        for (Thread workingThread : this.workingThreads)
            workingThread.interrupt();

        this.setaServerPubSub.unsubscribeFromRideConfirmationTopic();
    }

    private class RideRequestPublisher extends Thread {
        private final int district;
        private final RideRequestDto rideRequest;

        public RideRequestPublisher(int district, RideRequestDto rideRequest) {
            super();
            this.district = district;
            this.rideRequest = rideRequest;
        }

        @Override
        public void run() {
            try {
                SetaSystem.this.setaServerPubSub.publishRideRequest(this.rideRequest);

                synchronized (SetaSystem.this.pendingRideConfirmations) {
                    SetaSystem.this.pendingRideConfirmations.add(this.rideRequest.getId());
                }

                Thread.sleep(setaConfig.rideRequestTimeout);

                synchronized (SetaSystem.this.pendingRideConfirmations) {
                    if (SetaSystem.this.pendingRideConfirmations.contains(this.rideRequest.getId())) {
                        logger.info("Ride request with Id {} will be sent again (cause: idleness)",
                                this.rideRequest.getId());
                        this.rideRequest.resetTimestamp();
                        Set<RideRequestDto> newRideRequestsSet = districtNewRequestsMap.get(this.district);
                        synchronized (newRideRequestsSet) {
                            newRideRequestsSet.add(this.rideRequest);
                            newRideRequestsSet.notify();
                        }
                    }
                }

                synchronized (workingThreads) {
                    workingThreads.remove(this);
                }
            } catch (InterruptedException e) {
            }
        }
    }

    private class DistrictPublisherThread extends Thread {
        private final int districtId;

        public DistrictPublisherThread(int districtId) {
            super();
            this.districtId = districtId;
        }

        @Override
        public void run() {
            Set<RideRequestDto> newRideRequestsSet = districtNewRequestsMap.get(this.districtId);
            try {
                synchronized (newRideRequestsSet) {
                    while (!this.isInterrupted()) {
                        while (newRideRequestsSet.size() == 0) {
                            newRideRequestsSet.wait();
                        }

                        List<RideRequestDto> publishedRequests = new ArrayList<>();
                        for (RideRequestDto rideRequest : newRideRequestsSet) {
                            RideRequestPublisher rideRequestPublisher = new RideRequestPublisher(
                                    this.districtId, rideRequest);

                            rideRequestPublisher.start();
                            publishedRequests.add(rideRequest);
                            synchronized (workingThreads) {
                                workingThreads.add(rideRequestPublisher);
                            }
                        }
                        publishedRequests.forEach(newRideRequestsSet::remove);
                    }
                }
            }
            catch (InterruptedException e) {
            }
        }
    }

    private void startThreadsToPublishRideRequests() {
        for (int i = 1; i <= configurationManager.getNumDistricts(); i++) {
            Thread districtPublisherThread = new DistrictPublisherThread(i);
            synchronized (this.workingThreads) {
                this.workingThreads.add(districtPublisherThread);
            }

            districtPublisherThread.start();
        }
    }

    void addToNewRideRequests(RideRequestDto rideRequest) {
        District district = District.fromPosition(rideRequest.getStart());
        Set<RideRequestDto> newRideRequestsSet = districtNewRequestsMap
                .get(Integer.parseInt(district.toString()));
        logger.info("Ride request with Id {} has been generated", rideRequest.getId());
        synchronized (newRideRequestsSet) {
            newRideRequestsSet.add(rideRequest);
            newRideRequestsSet.notify();
        }
    }

    private void subscribeToRideConfirmations() {
        this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
            synchronized (pendingRideConfirmations) {
                pendingRideConfirmations.remove(rideConfirm.getRideId());
            }
        });
    }

//    private int getDistrictFromRideTopic(String topic) {
//        String districtString = topic.split("/")[3];
//        return Integer.parseInt(districtString.substring(8)); // start from the digit after "district"
//    }

    public interface RideGenerator {
        RideRequestDto generateRide();
    }

    public static class SETAConfig {
        private int requestLimit = 0;
        private int genFrequencyMillis = configurationManager.getSETAGenerationFrequencyMillis();
        private int numGeneratedRequest = configurationManager.getSETANumGeneratedRequest();
        private int rideRequestTimeout = configurationManager.getRideRequestTimeout();

        public SETAConfig withRequestLimit(int requestLimit) {
            this.requestLimit = requestLimit;
            return this;
        }

        public SETAConfig withGenFrequencyMillis(int genFrequencyMillis) {
            this.genFrequencyMillis = genFrequencyMillis;
            return this;
        }

        public SETAConfig withNumGeneratedRequest(int numGeneratedRequest) {
            this.numGeneratedRequest = numGeneratedRequest;
            return this;
        }

        public SETAConfig withRideRequestTimeout(int rideRequestTimeout) {
            this.rideRequestTimeout = rideRequestTimeout;
            return this;
        }
    }

    public static void main(String[] args) throws MqttException, InterruptedException {
        MqttAsyncClient client = MQTTClientFactory.getClient();
        if (client.isConnected())
            System.out.println("MQTT client for SETA is ready");
        SETAConfig setaConfig = new SETAConfig();
        SETAServerPubSubBase setaServerPubSub = new SETAServerPubSub(client);

        RideGenerator rideGenerator = new RideGenerator() {
            private int id = -1;
            private final Random random = new Random();
            private final int smartCityWidth = configurationManager.getSmartCityWidth();
            private final int smartCityHeight = configurationManager.getSmartCityHeight();

            @Override
            public RideRequestDto generateRide() {
                SmartCityPosition start, end;
                do {
                    start = new SmartCityPosition(
                            random.nextInt(smartCityWidth),
                            random.nextInt(smartCityHeight)
                    );
                    end = new SmartCityPosition(
                            random.nextInt(smartCityWidth),
                            random.nextInt(smartCityHeight)
                    );
                } while (start.equals(end));

                this.id++;

                return new RideRequestDto(this.id, start, end);
            }
        };

        try (SetaSystem ss = new SetaSystem(rideGenerator, setaConfig, setaServerPubSub)) {
            ss.run();
        }
        finally {
            client.disconnect().waitForCompletion();
            client.close();
        }
    }
}
