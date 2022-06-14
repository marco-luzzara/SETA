package unimi.dsp.util;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.IOException;

public class MQTTClientFactory {
    private static final ConfigurationManager configManager;
    private static final String brokerUri;

    static {
        configManager = ConfigurationManager.getInstance();
        brokerUri = String.format("tcp://%s:%d", configManager.getBrokerHost(), configManager.getBrokerPort());
    }

    public static MqttAsyncClient getClient() throws MqttException {
        String clientId = MqttClient.generateClientId();
        MqttAsyncClient client = new MqttAsyncClient(brokerUri, clientId);

        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        connOpts.setAutomaticReconnect(true);
        client.connect(connOpts).waitForCompletion();

        return client;
    }
}
