package unimi.dsp.adminServer.factories;

import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.adminServer.services.impl.TaxiServiceImpl;

public class TaxiServiceFactory {
    private static final TaxiService service = new TaxiServiceImpl();

    public static TaxiService getTaxiService() {
        return service;
    }
}
