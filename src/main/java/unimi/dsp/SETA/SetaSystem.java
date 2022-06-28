package unimi.dsp.SETA;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import unimi.dsp.SETA.services.SETAServerPubSub;
import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;
import unimi.dsp.util.SerializationUtil;

import java.io.Closeable;
import java.util.*;

public class SetaSystem implements Closeable {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final String RIDE_REQUEST_TOPIC_PREFIX = configurationManager.getRideRequestTopicPrefix();
    private static final String RIDE_CONFIRM_TOPIC = configurationManager.getRideConfirmationTopic();
    private static final Logger logger = LogManager.getLogger(SetaSystem.class.getName());

    private final RideGenerator rideGenerator;
    private final int requestLimit;
    private final int genFrequencyMillis;
    private final int numGeneratedRequest;
    private final int rideRequestTimeout;
    private int curId;
    private final SETAServerPubSubBase setaServerPubSub;
    private final Map<Integer, Set<RideRequestDto>> districtNewRequestsMap;
    private final Set<Integer> pendingRideConfirmations;
    private final List<Thread> workingThreads = new ArrayList<>();

    /**
     * Create a SETA system
     * @param rideGenerator generates the starting and destination position for a ride
     * @param requestLimit specifies the number of requests the SETA system is going to send before exiting.
     *                     if 0, the SETA never stops (unless forced manually).
     * @param genFrequencyMillis number of milliseconds it waits before publishing new rides
     * @param numGeneratedRequest number of requests published before sleeping again
     */
    public SetaSystem(RideGenerator rideGenerator,
                      int requestLimit,
                      int genFrequencyMillis,
                      int numGeneratedRequest,
                      int rideRequestTimeout,
                      SETAServerPubSubBase setaServerPubSub) {
        this.rideGenerator = rideGenerator;
        this.requestLimit = requestLimit;
        this.genFrequencyMillis = genFrequencyMillis;
        this.numGeneratedRequest = numGeneratedRequest;
        this.rideRequestTimeout = rideRequestTimeout;
        this.setaServerPubSub = setaServerPubSub;
        this.curId = 0;

        this.districtNewRequestsMap = new HashMap<>();
        this.pendingRideConfirmations = new HashSet<>();
        for (int i = 1; i <= configurationManager.getNumDistricts(); i++) {
            this.districtNewRequestsMap.put(i, new HashSet<>());
        }
    }

    public void run() throws MqttException, InterruptedException {
        this.subscribeToRideConfirmations();
        this.startThreadsToPublishRideRequests();

        while (requestLimit == 0 || curId < requestLimit) {
            for (int i = 0; i < numGeneratedRequest; i++) {
                RideRequestDto rideRequest = this.rideGenerator.generateRide();
                addToNewRideRequests(rideRequest);
                this.curId++;

                if (curId == requestLimit)
                    return;
            }

            Thread.sleep(this.genFrequencyMillis);
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
                setaServerPubSub.publishRideRequest(this.rideRequest);

                synchronized (pendingRideConfirmations) {
                    pendingRideConfirmations.add(this.rideRequest.getId());
                }

                Thread.sleep(rideRequestTimeout);

                synchronized (pendingRideConfirmations) {
                    if (pendingRideConfirmations.contains(this.rideRequest.getId())) {
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
                    workingThreads.add(this);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
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
            synchronized (newRideRequestsSet) {
                while (true) {
                    while (newRideRequestsSet.size() == 0) {
                        try {
                            newRideRequestsSet.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    for (RideRequestDto rideRequest : newRideRequestsSet) {
                        RideRequestPublisher rideRequestPublisher = new RideRequestPublisher(
                                this.districtId, rideRequest);
                        // I do not need to synchronized on `newRideRequestsSet` again because already in
                        // the synchronized block
                        // TODO: maybe this remove should be put in the thread body
                        newRideRequestsSet.remove(rideRequest);
                        rideRequestPublisher.start();
                        synchronized (workingThreads) {
                            workingThreads.add(rideRequestPublisher);
                        }
                    }
                }
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

    public static void main(String[] args) throws MqttException, InterruptedException {
        MqttAsyncClient client = MQTTClientFactory.getClient();
        if (client.isConnected())
            System.out.println("MQTT client for SETA is ready");
        int generationFrequencyMillis = configurationManager.getSETAGenerationFrequencyMillis();
        int numGeneratedRequest = configurationManager.getSETANumGeneratedRequest();
        int rideRequestTimeout = configurationManager.getRideRequestTimeout();
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

        try (SetaSystem ss = new SetaSystem(rideGenerator, 0,
                generationFrequencyMillis, numGeneratedRequest, rideRequestTimeout, setaServerPubSub)) {
            ss.run();
        }
        finally {
            client.disconnect().waitForCompletion();
            client.close();
        }
    }

    public interface RideGenerator {
        RideRequestDto generateRide();
    }
}
