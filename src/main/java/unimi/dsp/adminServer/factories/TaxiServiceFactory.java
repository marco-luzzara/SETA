package unimi.dsp.adminServer.factories;

import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.adminServer.services.impl.TaxiServiceImpl;
import unimi.dsp.adminServer.util.TaxiPositionGeneratorImpl;

public class TaxiServiceFactory {
    private static final TaxiService service = new TaxiServiceImpl(
            new TaxiPositionGeneratorImpl()
    );

    public static TaxiService getTaxiService() {
        return service;
    }
}
