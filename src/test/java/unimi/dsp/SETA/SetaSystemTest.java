package unimi.dsp.SETA;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.fakeFactories.RidePositionGeneratorFactory;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;
import unimi.dsp.util.SerializationUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
requirements: sudo service mosquitto start
 */
public class SetaSystemTest {
    // at the end of each test I check if "OK" has been written in this sb. this is an easy way
    // to verify if the test was successful, given that assertions exceptions are sinked in threads.
    private StringBuilder sb = new StringBuilder();
    // this exception includes all the sinked exceptions coming from threads.
    // in the catch clause the caught exception is added as suppressed
    private RuntimeException sinkedException = new RuntimeException();
    private static final String RIDE_REQUEST_TOPIC_PREFIX = ConfigurationManager
            .getInstance().getRideRequestTopicPrefix();

    @Test
    public void givenASingleRideRequest_WhenSETARun_MQTTSubSeeRide() throws MqttException {
        runWithinClient(client -> {
            try (SetaSystem ss = new SetaSystem(
                    RidePositionGeneratorFactory.getGenerator(0, 0, 1, 1),
                    1, 1, 1, 2000, 2)) {
                client.setCallback(getCallbackForMessageArrived((topic, message, counter) -> {
                    RideRequestDto rideRequest = SerializationUtil.deserialize(
                            message.getPayload(), RideRequestDto.class);

                    assertEquals(new SmartCityPosition(0, 0), rideRequest.getStart());
                }));
                IMqttToken token = client.subscribe(RIDE_REQUEST_TOPIC_PREFIX + "/district1", 2);

                ss.run();

                token.waitForCompletion();
                Thread.sleep(1000);
            } catch (MqttException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertCallbacksSuccessful(1);
    }

    @Test
    public void givenASingleRideRequest_WhenTimeoutExpires_ItIsResentAfterAWhile() throws MqttException {
        runWithinClient(client -> {
            try (SetaSystem ss = new SetaSystem(
                    RidePositionGeneratorFactory.getGenerator(0, 0, 1, 1),
                    1, 1, 1, 1500, 2)) {
                client.setCallback(getCallbackForMessageArrived((topic, message, counter) -> {
                    RideRequestDto rideRequest = SerializationUtil.deserialize(
                            message.getPayload(), RideRequestDto.class);

                    assertEquals(new SmartCityPosition(0, 0), rideRequest.getStart());
                }));
                IMqttToken token = client.subscribe(RIDE_REQUEST_TOPIC_PREFIX + "/district1", 2);

                ss.run();

                token.waitForCompletion();
                Thread.sleep(3000);
            } catch (MqttException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // the real check is here because i make sure that I receive 2 equal requests
        assertCallbacksSuccessful(2);
    }

    @Test
    public void givenManyRideRequest_WhenSETARun_MQTTSubSeeAllRides() {
        List<RideRequestDto> rideRequestsArrived = new ArrayList<>();
        runWithinClient(client -> {
            try (SetaSystem ss = new SetaSystem(
                    RidePositionGeneratorFactory.getGenerator(
                            RidePositionGeneratorFactory.getRideRequest(0, 0, 0, 1),
                            RidePositionGeneratorFactory.getRideRequest(1, 2, 3, 4)),
                    2, 10, 1, 2000, 2)){
                client.setCallback(getCallbackForMessageArrived((topic, message, counter) -> {
                    RideRequestDto rideRequest = SerializationUtil.deserialize(
                            message.getPayload(), RideRequestDto.class);
                    rideRequestsArrived.add(rideRequest);
                }));
                IMqttToken token = client.subscribe(RIDE_REQUEST_TOPIC_PREFIX + "/district1", 2);

                ss.run();

                token.waitForCompletion();
                Thread.sleep(1000);
            } catch (MqttException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertCallbacksSuccessful(2);
        assertThat(rideRequestsArrived.stream().mapToInt(RideRequestDto::getId)).contains(0, 1);
    }

    private void assertCallbacksSuccessful(int messagesNum) {
        try {
            assertEquals(
                    String.join("", Collections.nCopies(messagesNum, "OK")),
                    sb.toString());
        }
        catch (AssertionFailedError err) {
            sinkedException.addSuppressed(err);
            throw sinkedException;
        }
    }

    @FunctionalInterface
    public interface Consumer3Args<T1, T2, T3> {
        void accept(T1 arg1, T2 arg2, T3 arg3);
    }

    private MqttCallback getCallbackForMessageArrived(Consumer3Args<String, MqttMessage, Integer> messageArrivedCb) {
        return new MqttCallback() {
            private int messageCounter = 0;
            @Override
            public void connectionLost(Throwable cause) {
                sinkedException.addSuppressed(cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                try {
                    messageCounter++;
                    messageArrivedCb.accept(topic, message, messageCounter);
                    sb.append("OK");
                }
                catch (Throwable e) {
                    sinkedException.addSuppressed(e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        };
    }

    private void runWithinClient(Consumer<IMqttAsyncClient> testBody) {
        try (MqttAsyncClient client = MQTTClientFactory.getClient()) {
            testBody.accept(client);
            client.disconnect().waitForCompletion();
        }
        catch (MqttException exc) {
            throw new RuntimeException(exc);
        }
    }
}
