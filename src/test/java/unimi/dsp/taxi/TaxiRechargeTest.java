package unimi.dsp.taxi;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import unimi.dsp.SETA.SETAServerPubSubBase;
import unimi.dsp.SETA.services.SETAServerPubSub;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.fakeFactories.FakeTaxiFactory;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.stubs.AdminServiceStub;
import unimi.dsp.testUtils.mocks.PositionGeneratorMock;
import unimi.dsp.util.MQTTClientFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaxiRechargeTest {
    private final TaxiPositionGenerator taxiPositionGenerator = mock(TaxiPositionGenerator.class);
    private final AdminServiceBase adminService = new AdminServiceStub(taxiPositionGenerator);
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
    public void givenATaxiAfterRide_WhenBatteryIsBelowThreshold_ThenGoToRechargeImmediately() throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1,
                new Taxi.TaxiConfig()
                        .withRideDeliveryDelay(0)
                        .withRechargeDelay(1500)
                        .withBatteryThresholdBeforeRecharge(99),
                adminService)) {
            taxi.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(0, 3), new SmartCityPosition(9, 3)));

            Thread.sleep(500);
            assertThat(taxi.getStatus()).isEqualTo(Taxi.TaxiStatus.RECHARGING);
            assertThat(taxi.getPosition()).isEqualTo(new SmartCityPosition(9, 0));
            assertThat(taxi.getBatteryLevel()).isEqualTo(85); // 100 - (3 + 9 + 3)

            Thread.sleep(1100);
            assertThat(taxi.getBatteryLevel()).isEqualTo(100);
        }
    }

    @Test
    public void givenATaxi_WhenManuallyGoToRecharge_ThenBatteryLevelAndPositionUpdated() throws InterruptedException {
        positionGeneratorMock.generate(4, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1,
                new Taxi.TaxiConfig()
                        .withRechargeDelay(1000),
                adminService)) {
            taxi.enterInSETANetwork();

            taxi.askForTheRechargeStation();
            Thread.sleep(500);

            assertThat(taxi.getStatus()).isEqualTo(Taxi.TaxiStatus.RECHARGING);
            assertThat(taxi.getPosition()).isEqualTo(new SmartCityPosition(0, 0));
            assertThat(taxi.getBatteryLevel()).isEqualTo(96); // 100 - 4

            Thread.sleep(600);
            assertThat(taxi.getBatteryLevel()).isEqualTo(100);
        }
    }

    @Test
    public void givenATaxiThatIsRechargingAndOneWaiting_WhenItExitsFromStation_ThenTheOtherTaxiTakesIt() throws InterruptedException {
        positionGeneratorMock.generate(4, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1,
                new Taxi.TaxiConfig()
                    .withRechargeDelay(1000),
                adminService); Taxi taxi2 = FakeTaxiFactory.getTaxi(2,
                new Taxi.TaxiConfig()
                    .withRechargeDelay(2000),
                adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();

            taxi.askForTheRechargeStation();
            Thread.sleep(500);
            assertThat(taxi.getStatus()).isEqualTo(Taxi.TaxiStatus.RECHARGING);

            taxi2.askForTheRechargeStation();
            Thread.sleep(1000);

            assertThat(taxi2.getStatus()).isEqualTo(Taxi.TaxiStatus.RECHARGING);
        }
    }

    @Test
    public void givenARechargingTaxi_WhenItTriesToExit_ThenRechargingMustComplete() throws InterruptedException {
        positionGeneratorMock.generate(4, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1,
                new Taxi.TaxiConfig()
                        .withRechargeDelay(1000),
                adminService)) {
            taxi.enterInSETANetwork();

            taxi.askForTheRechargeStation();
            Thread.sleep(200);

            Thread t = new Thread(taxi::close);
            t.start();

            Thread.sleep(100);
            assertThat(taxi.getStatus()).isEqualTo(Taxi.TaxiStatus.RECHARGING);

            Thread.sleep(800);
            assertThat(taxi.getStatus()).isEqualTo(Taxi.TaxiStatus.UNSTARTED);
        }
    }

    @Test
    public void givenManyRechargingTaxis_WhenOneTriesToExit_ThenItIsRemovedImmediately() throws InterruptedException {
        positionGeneratorMock.generate(4, 0);
        Taxi.TaxiConfig configs = new Taxi.TaxiConfig().withRechargeDelay(1000);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, configs, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, configs, adminService);
             Taxi taxi3 = FakeTaxiFactory.getTaxi(3, configs, adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();
            taxi3.enterInSETANetwork();

            taxi.askForTheRechargeStation();
            Thread.sleep(100);

            taxi2.askForTheRechargeStation();
            taxi3.askForTheRechargeStation();
            Thread.sleep(100);

            Thread t = new Thread(taxi2::close);
            t.start();

            Thread.sleep(300);
            assertThat(taxi2.getStatus()).isEqualTo(Taxi.TaxiStatus.UNSTARTED);

            Thread.sleep(700);
            assertThat(taxi3.getStatus()).isEqualTo(Taxi.TaxiStatus.RECHARGING);
        }
    }
}
