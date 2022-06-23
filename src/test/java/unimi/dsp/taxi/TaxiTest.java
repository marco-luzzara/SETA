package unimi.dsp.taxi;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.fakeFactories.FakeTaxiFactory;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.stubs.AdminServiceStub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TaxiTest {
    private final AdminServiceBase adminService = new AdminServiceStub(new TaxiPositionGenerator() {
        @Override
        public SmartCityPosition getStartingPosition() {
            return new SmartCityPosition(0, 0);
        }
    });

    @Test
    public void givenSingleTaxi_WhenRegistered_ThenReturnRegisteredTaxi() throws MqttException {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, adminService)) {
            taxi.enterInSETANetwork();

            assertEquals(0, taxi.getNetworkTaxiConnections().size());
        }
    }

    @Test
    public void givenManyTaxisWithSameId_WhenDuplicateTaxiRegister_ThenThrow() throws MqttException {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, adminService);
             Taxi taxiDup = FakeTaxiFactory.getTaxi(1, adminService)) {
            taxi.enterInSETANetwork();

            assertThrows(IllegalStateException.class, taxiDup::enterInSETANetwork);
        }
    }

    @Test
    public void givenManyTaxis_WhenTheSecondIsRegistered_ThenItReceivesTheAlreadyRegisteredTaxis()
            throws MqttException {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, adminService)) {
            taxi.enterInSETANetwork();

            taxi2.enterInSETANetwork();

            assertEquals(1, taxi.getNetworkTaxiConnections().size());
            assertEquals(1, taxi2.getNetworkTaxiConnections().size());
        }
    }

    @Test
    public void givenManyTaxis_WhenTheSecondExits_ThenTheLastOneRemovesItFromItsNetwork()
            throws InterruptedException, MqttException {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1, adminService);
             Taxi taxi2 = FakeTaxiFactory.getTaxi(2, adminService)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();

            taxi2.close();

            // sleep is necessary here because the grpc stub is async when taxi exits
            Thread.sleep(1000);
            assertEquals(0, taxi.getNetworkTaxiConnections().size());
        }
    }
}
