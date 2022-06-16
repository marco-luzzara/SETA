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

            assertEquals(0, taxi.getRegisteredTaxis().size());
        }
    }

    @Test
    public void givenManyTaxisWithSameId_WhenDuplicateTaxiRegister_ThenThrow() {
        try (Taxi taxi = FakeTaxiFactory.getTaxi(1); Taxi taxiDup = FakeTaxiFactory.getTaxi(1)) {
            taxi.enterInSETANetwork();

            assertThrows(IllegalStateException.class, taxiDup::enterInSETANetwork);
        }
    }

//    @Test
//    public void givenManyTaxis_WhenTheSecondIsRegistered_ThenItReceivesTheAlreadyRegisteredTaxis()
//            throws InterruptedException {
//        try (Taxi taxi = FakeTaxiFactory.getTaxi(1); Taxi taxi2 = FakeTaxiFactory.getTaxi(2)) {
//            taxi.enterInSETANetwork();
//
//            taxi2.enterInSETANetwork();
//
//            Thread.sleep(1000);
//            assertEquals(1, taxi.getRegisteredTaxis().size());
//            assertEquals(1, taxi2.getRegisteredTaxis().size());
//        }
//    }


    @Test
    public void givenARegisteredTaxi_WhenNewTaxiEnterInNetwork_ThenItIsPresentedToTheOthers() {

    }

//    @AfterEach
//    public void cleanUp() throws InterruptedException {
//        Thread.sleep(500);
//    }
}
