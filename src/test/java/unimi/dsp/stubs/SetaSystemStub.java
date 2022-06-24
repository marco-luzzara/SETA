package unimi.dsp.stubs;

import org.eclipse.paho.client.mqttv3.*;
import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;
import unimi.dsp.util.SerializationUtil;

import java.util.function.Consumer;

public class SetaSystemStub {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final String RIDE_REQUEST_TOPIC_PREFIX = configurationManager.getRideRequestTopicPrefix();
    private static final String RIDE_CONFIRM_TOPIC = configurationManager.getRideConfirmationTopic();

    private final MqttAsyncClient mqttClient;

    public SetaSystemStub() {
        this.mqttClient = MQTTClientFactory.getClient();
    }

    private void subscribeToRideConfirmations(Consumer<RideConfirmDto> confirmAction) throws MqttException {
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
                confirmAction.accept(rideConfirm);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        this.mqttClient.subscribe(RIDE_CONFIRM_TOPIC, 1).waitForCompletion();
    }
}
