package unimi.dsp.SETA;

import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Test;
import unimi.dsp.fakeFactories.RidePositionGeneratorFactory;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SetaSystemTest {

    private static final String RIDE_REQUEST_TOPIC_PREFIX = ConfigurationManager
            .getInstance().getRideRequestTopicPrefix();

    @Test
    public void GivenASingleRideRequest_WhenSETARun_MQTTSubSeeRide() throws MqttException, InterruptedException {
        runWithinClient(client -> {
            try {
                IMqttToken token = client.subscribe(RIDE_REQUEST_TOPIC_PREFIX + "1", 2);
                SetaSystem ss = new SetaSystem(
                        RidePositionGeneratorFactory.getGenerator(0, 0, 1, 1),
                        1, 1, 1);

                ss.run();

                token.waitForCompletion();
            } catch (MqttException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void runWithinClient(Consumer<IMqttAsyncClient> testBody) throws MqttException {
        try (MqttAsyncClient client = MQTTClientFactory.getClient()) {
            testBody.accept(client);
            client.disconnect().waitForCompletion();
        }
        catch (MqttException exc) {

        }
    }
}
