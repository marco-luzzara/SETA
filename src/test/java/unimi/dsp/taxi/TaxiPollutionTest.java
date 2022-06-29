package unimi.dsp.taxi;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unimi.dsp.SETA.SETAServerPubSubBase;
import unimi.dsp.SETA.services.SETAServerPubSub;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.fakeFactories.FakeTaxiFactory;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.sensors.Buffer;
import unimi.dsp.sensors.Measurement;
import unimi.dsp.sensors.simulators.Simulator;
import unimi.dsp.stubs.AdminServiceStub;
import unimi.dsp.taxi.services.mqtt.SETATaxiPubSub;
import unimi.dsp.testUtils.mocks.PositionGeneratorMock;
import unimi.dsp.util.MQTTClientFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class TaxiPollutionTest {
    private final TaxiPositionGenerator taxiPositionGenerator = mock(TaxiPositionGenerator.class);
    private final AdminServiceStub adminService = new AdminServiceStub(taxiPositionGenerator);
    private final MqttAsyncClient mqttClient = MQTTClientFactory.getClient();
    private final SETAServerPubSubBase setaServerPubSub = new SETAServerPubSub(mqttClient);
    private final PositionGeneratorMock positionGeneratorMock = new PositionGeneratorMock(
            when(taxiPositionGenerator.getStartingPosition()));

    private List<Integer> confirmedRides = new ArrayList<>();

    @AfterEach
    public void testCleanup() throws MqttException {
        mqttClient.disconnect().waitForCompletion();
        mqttClient.close();
    }

    @Test
    public void givenATaxi_WhenRideIsCompleteAndLoadStatistics_ThenStatsIncludeTheRide() throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = spy(FakeTaxiFactory.getTaxi(1,new Taxi.TaxiConfig()
                        .withRideDeliveryDelay(0)
                        .withSlidingWindowBufferSize(4)
                        .withStatsLoadingDelay(500),
                adminService))) {
            this.mockTaxiWithCustomSimulator(taxi, 0.3, 0.5, 0.5, 0.3);
            taxi.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(0, 3), new SmartCityPosition(9, 3)));

            Thread.sleep(700);
            assertThat(taxi.getTakenRides()).hasSize(0);
            assertThat(adminService.getLoadedStatistics()).hasSize(1);
//            assertThat(adminService.getLoadedStatistics().get(0).getBatteryLevel()).isEqualTo(88);
            assertThat(adminService.getLoadedStatistics().get(0).getStatsValues().getNumRides())
                    .isEqualTo(1);
            assertThat(adminService.getLoadedStatistics().get(0).getStatsValues().getKmsTraveled())
                    .isEqualTo(12);
            assertThat(adminService.getLoadedStatistics().get(0).getStatsValues().getPollutionAvgList())
                    .contains(0.4);
        }
    }

    private void mockTaxiWithCustomSimulator(Taxi taxi, final double... producedData) {
        doAnswer(invocation -> {
            Taxi obj = (Taxi) invocation.getMock();
            Buffer buffer = invocation.getArgument(0);
            obj.getClass().getField("pollutionDataProvider").set(obj,
                    new Simulator("test", "test", buffer) {
                @Override
                public void run() {
                    for (double val : producedData)
                        this.getBuffer().addMeasurement(new Measurement("test", "test", val,
                                System.currentTimeMillis()));
                }
            });
            return null;
        }).when(taxi).initializeSimulator(any(Buffer.class));
    }
}
