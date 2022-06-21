package unimi.dsp.util;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MQTTClientFactory {
    private static final ConfigurationManager configManager;
    private static final String brokerUri;

    static {
        configManager = ConfigurationManager.getInstance();
        brokerUri = configManager.getBrokerEndpoint();
    }

    public static MqttAsyncClient getClient() {
        try {
            String clientId = MqttClient.generateClientId();
            MqttAsyncClient client = new MqttAsyncClient(brokerUri, clientId, new MemoryPersistence());

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setAutomaticReconnect(true);
            client.connect(connOpts).waitForCompletion();

            return client;
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }
}
