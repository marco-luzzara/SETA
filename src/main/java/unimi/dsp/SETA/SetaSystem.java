package unimi.dsp.SETA;

import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;

import java.nio.charset.StandardCharsets;

public class SetaSystem {
    private static ConfigurationManager configurationManager = ConfigurationManager.getInstance();

    private RideGenerator rideGenerator;
    private int requestLimit;
    private int genFrequencyMillis;
    private int numGeneratedRequest;
    private int curId;
    private IMqttAsyncClient mqttClient;

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
                      int numGeneratedRequest) throws MqttException {
        this.rideGenerator = rideGenerator;
        this.requestLimit = requestLimit;
        this.genFrequencyMillis = genFrequencyMillis;
        this.numGeneratedRequest = numGeneratedRequest;
        this.curId = 0;

        this.mqttClient = MQTTClientFactory.getClient();
    }

    public void run() throws MqttException, InterruptedException {
        while (requestLimit == 0 || curId < requestLimit) {
            for (int i = 0; i < numGeneratedRequest; i++) {
                RideRequestDto rideRequest = this.rideGenerator.generateRide();
                this.mqttClient.publish(
                        configurationManager.getRideRequestTopicPrefix() + rideRequest.getDistrict(),
                        getPayloadFromRideRequest(rideRequest),
                        2, false);
                this.curId++;

                if (curId == requestLimit)
                    return;
            }

            Thread.sleep(this.genFrequencyMillis);
        }
    }

    private byte[] getPayloadFromRideRequest(RideRequestDto rideRequest) {
        String jsonRideRequest = new Gson().toJson(rideRequest);
        return jsonRideRequest.getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws MqttException, InterruptedException {
//        MqttAsyncClient client = MQTTClientFactory.getClient();
//        int curId = 0;
//        ConfigurationManager configurationManager = ConfigurationManager.getInstance();
//        int generationFrequencyMillis = configurationManager.getSETAGenerationFrequencyMillis();
//        int numGeneratedRequest = configurationManager.getSETANumGeneratedRequest();
//
//        while(true) {
//
//
//            Thread.sleep(generationFrequencyMillis);
//            client.publish()
//        }
    }

    public interface RideGenerator {
        RideRequestDto generateRide();
    }
}
