package unimi.dsp.taxi;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unimi.dsp.SETA.SETAServerPubSubBase;
import unimi.dsp.SETA.services.SETAServerPubSub;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.fakeFactories.FakeTaxiFactory;
import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.stubs.AdminServiceStub;
import unimi.dsp.testUtils.mocks.PositionGeneratorMock;
import unimi.dsp.util.MQTTClientFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaxiRideRequestProcessingTest {
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
    public void givenOneTaxi_WhenARideIsPublishedInSameDistrict_ThenTheTaxiTakesItImmediately() throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService)) {
            taxi.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(0, 0), new SmartCityPosition(9, 0)));

            Thread.sleep(500);
            assertThat(this.confirmedRides).contains(0);
        }
    }

    @Test
    public void givenOneTaxi_WhenARideIsPublishedInDifferentDistrict_ThenTheTaxiIgnoresIt() throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService)) {
            taxi.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(9, 0), new SmartCityPosition(9, 9)));

            Thread.sleep(500);
            assertThat(this.confirmedRides).isEmpty();
        }
    }

    @Test
    public void givenOneTaxi_WhenARideIsPublished_ThenDrivesAndReduceBattery() throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService)) {
            taxi.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(3, 4), new SmartCityPosition(7, 1)));

            Thread.sleep(500);
            assertThat(this.confirmedRides).contains(0);
            assertThat(taxi.getBatteryLevel()).isEqualTo(90);
        }
    }

    @Test
    public void givenTwoTaxisInSameDistrict_WhenARideIsPublished_ThenOnlyOneTaxiTakesIt() throws InterruptedException {
        positionGeneratorMock.generate(0, 0).generate(4, 4);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, 0, adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();
            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });

            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(0, 0), new SmartCityPosition(9, 0)));
            Thread.sleep(500);

            assertThat(this.confirmedRides).contains(0);
            assertThat(taxi.getTakenRides()).contains(0);
            assertThat(taxi2.getTakenRides()).isEmpty();
        }
    }

    @Test
    public void given3TaxisInSameDistrict_WhenARideIsPublished_ThenTheNearestTaxiTakesIt() throws InterruptedException {
        positionGeneratorMock
                .generate(0, 0).generate(3, 3).generate(4, 4);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, 0, adminService);
             Taxi taxi3 = FakeTaxiFactory.getTaxi(3, 0, adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();
            taxi3.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(2, 2), new SmartCityPosition(9, 0)));
            Thread.sleep(500);

            assertThat(this.confirmedRides).contains(0);
            assertThat(taxi.getTakenRides()).hasSize(0);
            assertThat(taxi2.getTakenRides()).contains(0);
            assertThat(taxi3.getTakenRides()).hasSize(0);
        }
    }

    @Test
    public void given3TaxisEquallyDistantToARide_WhenARideIsPublished_ThenTheTaxiWithTheHighestBatteryTakesIt()
            throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1,
                    new Taxi.TaxiConfig().withInitialBatterylevel(60).withRideDeliveryDelay(0), adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2,
                    new Taxi.TaxiConfig().withInitialBatterylevel(70).withRideDeliveryDelay(0), adminService);
             Taxi taxi3 = FakeTaxiFactory.getTaxi(3,
                    new Taxi.TaxiConfig().withInitialBatterylevel(50).withRideDeliveryDelay(0), adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();
            taxi3.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(2, 2), new SmartCityPosition(9, 0)));
            Thread.sleep(500);

            assertThat(this.confirmedRides).contains(0);
            assertThat(taxi.getTakenRides()).hasSize(0);
            assertThat(taxi2.getTakenRides()).contains(0);
            assertThat(taxi3.getTakenRides()).hasSize(0);
        }
    }

    @Test
    public void given3TaxisDifferentFromIdOnly_WhenARideIsPublished_ThenTheTaxiWithTheHighestIdTakesIt()
            throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, 0, adminService);
             Taxi taxi3 = FakeTaxiFactory.getTaxi(3, 0, adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();
            taxi3.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(2, 2), new SmartCityPosition(9, 0)));
            Thread.sleep(500);

            assertThat(this.confirmedRides).contains(0);
            assertThat(taxi.getTakenRides()).hasSize(0);
            assertThat(taxi2.getTakenRides()).hasSize(0);
            assertThat(taxi3.getTakenRides()).contains(0);
        }
    }

    @Test
    public void givenATaxi_WhenTheRideDPIsInAnotherDistrict_ThenChangeDistrict() throws InterruptedException {
        positionGeneratorMock.generate(0, 0);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService)) {
            taxi.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(2, 2), new SmartCityPosition(9, 0)));
            Thread.sleep(500);

            assertThat(this.confirmedRides).contains(0);
            assertThat(taxi.getDistrict()).isEqualTo(District.BOTTOM_LEFT);
            assertThat(taxi.getStatus()).isEqualTo(Taxi.TaxiStatus.AVAILABLE);
        }
    }

    @Test
    public void given2TaxisInSameDistrict_When2RidesArePublishedNearerToTheFirstTaxi_ThenTaxisTakeOneRideEach()
            throws InterruptedException {
        positionGeneratorMock.generate(1, 1).generate(4, 4);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 2000, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, 2000, adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(2, 2), new SmartCityPosition(9, 0)));
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(1,
                    new SmartCityPosition(0, 1), new SmartCityPosition(9, 0)));

            Thread.sleep(500);
            assertThat(this.confirmedRides).contains(0, 1);
            assertThat(taxi.getTakenRides()).hasSize(1);
            assertThat(taxi2.getTakenRides()).hasSize(1);
        }
    }

    @Test
    public void given2TaxisInSameDistrict_When2RidesArePublishedOneForEach_ThenTaxisTakeBoth()
            throws InterruptedException {
        positionGeneratorMock.generate(1, 1).generate(4, 4);
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, 0, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, 0, adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();

            this.setaServerPubSub.subscribeToRideConfirmationTopic(rideConfirm -> {
                confirmedRides.add(rideConfirm.getRideId());
            });
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(0,
                    new SmartCityPosition(2, 2), new SmartCityPosition(9, 0)));
            this.setaServerPubSub.publishRideRequest(new RideRequestDto(1,
                    new SmartCityPosition(3, 3), new SmartCityPosition(9, 0)));

            Thread.sleep(500);
            assertThat(this.confirmedRides).contains(0, 1);
            assertThat(taxi.getTakenRides()).hasSize(1);
            assertThat(taxi2.getTakenRides()).hasSize(1);
        }
    }

//    private void mockAdminServiceGeneration(int taxiId, int startX, int startY) {
//        when(adminService.registerTaxi(argThat(new TaxiInfoDtoIsEqualGivenIdMatcher(taxiId))))
//                .thenAnswer(a -> {
//                    NewTaxiDto newTaxi = (NewTaxiDto) a.callRealMethod();
//                    newTaxi.setX(startX);
//                    newTaxi.setY(startY);
//                    return newTaxi;
//                });
//    }

//    private void mockAdminServicePositionGeneration(int startX, int startY) {
//        when(taxiPositionGenerator.getStartingPosition()).argThat(new TaxiInfoDtoIsEqualGivenIdMatcher(taxiId))))
//                .thenAnswer(a -> {
//                    NewTaxiDto newTaxi = (NewTaxiDto) a.callRealMethod();
//                    newTaxi.setX(startX);
//                    newTaxi.setY(startY);
//                    return newTaxi;
//                });
//    }
}
