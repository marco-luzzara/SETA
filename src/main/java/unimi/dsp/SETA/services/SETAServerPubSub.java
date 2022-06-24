package unimi.dsp.SETA.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import unimi.dsp.SETA.SETAServerPubSubBase;
import unimi.dsp.SETA.SetaSystem;
import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.District;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;
import unimi.dsp.util.SerializationUtil;

import java.util.function.Consumer;

public class SETAServerPubSub implements SETAServerPubSubBase {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final String RIDE_REQUEST_TOPIC_PREFIX = configurationManager.getRideRequestTopicPrefix();
    private static final String RIDE_CONFIRM_TOPIC = configurationManager.getRideConfirmationTopic();
    private static final Logger logger = LogManager.getLogger(SETAServerPubSub.class.getName());

    private final MqttAsyncClient mqttClient;

    public SETAServerPubSub(MqttAsyncClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    public void subscribeToRideConfirmationTopic(Consumer<RideConfirmDto> confirmAction) {
        this.mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (!topic.equals(RIDE_CONFIRM_TOPIC))
                    return;

                RideConfirmDto rideConfirm = SerializationUtil.deserialize(
                        message.getPayload(), RideConfirmDto.class);
                logger.info("Ride confirmation with id {} has arrived", rideConfirm.getRideId());
                confirmAction.accept(rideConfirm);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        // the qos is 1 because if I receive many messages with the same id,
        // the `pendingRideRequestsSet.remove()` would return false and nothing happens.
        try {
            this.mqttClient.subscribe(RIDE_CONFIRM_TOPIC, 1).waitForCompletion();
            logger.info("SETA subscribed to ride confirmation topic");
        } catch (MqttException e) {
            logger.error("Cannot subscribe to confirmation topic", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unsubscribeFromRideConfirmationTopic() {
        try {
            this.mqttClient.unsubscribe(RIDE_CONFIRM_TOPIC);
            logger.info("SETA unsubscribed to ride confirmation topic");
        } catch (MqttException e) {
            logger.error("Cannot unsubscribe from confirmation topic", e);
            throw new RuntimeException(e);
        }
    }

    public void publishRideRequest(District district, RideRequestDto rideRequest) {
        try {
            mqttClient.publish(
                    RIDE_REQUEST_TOPIC_PREFIX + "/district" + district.toString(),
                    SerializationUtil.serialize(rideRequest),
                    1, false);
            logger.info("Ride request with Id {} has been published", rideRequest.getId());
        } catch (MqttException e) {
            logger.error("Cannot publish ride request", e);
            throw new RuntimeException(e);
        }
    }
}
