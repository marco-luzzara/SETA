package unimi.dsp.taxi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TaxiTest {
    List<RegisteredTaxi> taxiToUnregister = new ArrayList<>();
    @Test
    public void givenSingleTaxi_WhenRegistered_ThenReturnRegisteredTaxi() {
        Taxi taxi = new Taxi(1, 1);

        RegisteredTaxi registeredTaxi = taxi.registerToServer();

        taxiToUnregister.add(registeredTaxi);
        assertEquals(0, registeredTaxi.getTaxis().size());
    }

    @Test
    public void givenManyTaxisWithSameId_WhenDuplicateTaxiRegister_ThenThrow() {
        Taxi taxi = new Taxi(1, 1);
        RegisteredTaxi registeredTaxi = taxi.registerToServer();
        taxiToUnregister.add(registeredTaxi);
        Taxi taxiDup = new Taxi(1, 2);

        assertThrows(IllegalStateException.class, () -> {
            RegisteredTaxi registeredTaxiDup = taxiDup.registerToServer();
            taxiToUnregister.add(registeredTaxiDup);
        });
    }

    @AfterEach
    public void testCleanup() {
        for (RegisteredTaxi taxi : taxiToUnregister) {
            try {
                taxi.unregisterFromServer();
            }
            catch (Exception ignored) {
            }
        }
    }
}
