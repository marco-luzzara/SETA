package unimi.dsp.taxi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unimi.dsp.fakeFactories.FakeTaxiFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TaxiTest {
    @Test
    public void givenSingleTaxi_WhenRegistered_ThenReturnRegisteredTaxi() {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1)) {
            taxi.enterInSETANetwork();

            assertEquals(0, taxi.getNetworkTaxiConnections().size());
        }
    }

    @Test
    public void givenManyTaxisWithSameId_WhenDuplicateTaxiRegister_ThenThrow() {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1); Taxi taxiDup = FakeTaxiFactory.getTaxi(1)) {
            taxi.enterInSETANetwork();

            assertThrows(IllegalStateException.class, taxiDup::enterInSETANetwork);
        }
    }

    @Test
    public void givenManyTaxis_WhenTheSecondIsRegistered_ThenItReceivesTheAlreadyRegisteredTaxis()
            throws InterruptedException {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1); Taxi taxi2 = FakeTaxiFactory.getTaxi(2)) {
            taxi.enterInSETANetwork();

            taxi2.enterInSETANetwork();

            assertEquals(1, taxi.getNetworkTaxiConnections().size());
            assertEquals(1, taxi2.getNetworkTaxiConnections().size());
        }
    }

    @Test
    public void givenManyTaxis_WhenTheSecondExits_ThenTheLastOneRemovesItFromItsNetwork()
            throws InterruptedException {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1); Taxi taxi2 = FakeTaxiFactory.getTaxi(2)) {
            taxi.enterInSETANetwork();
            taxi2.enterInSETANetwork();

            taxi2.close();

            // sleep is necessary here because the grpc stub is async
            Thread.sleep(1000);
            assertEquals(0, taxi.getNetworkTaxiConnections().size());
        }
    }

//    @AfterEach
//    public void cleanUp() throws InterruptedException {
//        Thread.sleep(500);
//    }
}
