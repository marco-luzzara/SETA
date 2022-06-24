package unimi.dsp.taxi.services.mqtt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.District;
import unimi.dsp.taxi.SETATaxiPubSubBase;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.SerializationUtil;

import java.util.function.Consumer;

public class SETATaxiPubSub implements SETATaxiPubSubBase {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final String RIDE_REQUEST_TOPIC_PREFIX = configurationManager.getRideRequestTopicPrefix();
    private static final String RIDE_CONFIRM_TOPIC = configurationManager.getRideConfirmationTopic();
    private static final Logger logger = LogManager.getLogger(SETATaxiPubSub.class.getName());

    private final MqttAsyncClient mqttClient;
    public SETATaxiPubSub(MqttAsyncClient mqttClient) {
        this.mqttClient = mqttClient;
        if (!this.mqttClient.isConnected()) {
            try {
                this.mqttClient.connect().waitForCompletion();
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void subscribeToDistrictTopic(District district, Consumer<RideRequestDto> eventAction) {
        this.mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                logger.error("MQTT connection failed", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (!topic.equals(RIDE_REQUEST_TOPIC_PREFIX))
                    return;

                RideRequestDto rideRequest = SerializationUtil.deserialize(message.getPayload(),
                        RideRequestDto.class);
                eventAction.accept(rideRequest);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });

        try {
            this.mqttClient.subscribe(RIDE_REQUEST_TOPIC_PREFIX + "/district" + district.toString(), 1)
                    .waitForCompletion();
        } catch (MqttException e) {
            logger.error("Taxi cannot subscribe to district topic", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void publishRideConfirmation(RideConfirmDto rideConfirm) {
        byte[] message = SerializationUtil.serialize(rideConfirm);
        try {
            this.mqttClient.publish(RIDE_CONFIRM_TOPIC, message, 1, false);
        } catch (MqttException e) {
            logger.error("Publishing ride confirm for ride " + rideConfirm.getRideId(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unsubscribeFromDistrictTopic(District district) {
        try {
            this.mqttClient.unsubscribe(RIDE_REQUEST_TOPIC_PREFIX + "/district" + district.toString())
                    .waitForCompletion();
        } catch (MqttException e) {
            logger.error("Taxi cannot unsubscribe to district", e);
            throw new RuntimeException(e);
        }
    }
}
