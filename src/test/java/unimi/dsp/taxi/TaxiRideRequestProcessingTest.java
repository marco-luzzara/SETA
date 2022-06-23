package unimi.dsp.taxi;

import org.junit.jupiter.api.Test;
import unimi.dsp.SETA.SetaSystem;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.fakeFactories.FakeTaxiFactory;
import unimi.dsp.fakeFactories.RidePositionGeneratorFactory;
import unimi.dsp.stubs.AdminServiceStub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaxiRideRequestProcessingTest {
    private final TaxiPositionGenerator taxiPositionGenerator = mock(TaxiPositionGenerator.class);
    private final AdminServiceBase adminService = new AdminServiceStub(taxiPositionGenerator);

//    @Test
//    public void givenOneTaxi_WhenARideIsPublished_ThenItTakesItImmediately() {
//        try (Taxi taxi = FakeTaxiFactory.getTaxi(1); SetaSystem ss = new SetaSystem(
//                RidePositionGeneratorFactory.getGenerator(0, 0, 1, 1),
//                1, 1, 1, 1500, 2)) {
//            taxi.enterInSETANetwork();
//
//            assertEquals(1, taxi.getNetworkTaxiConnections().size());
//        }
//    }

    private void mockEnterSetaNetwork(Taxi taxi, int startX, int startY) {
//        when(taxi.
    }
}
