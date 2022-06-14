package unimi.dsp.adminServer.factories;

import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.adminServer.services.impl.TaxiServiceImpl;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.util.ConfigurationManager;

import java.util.Random;

public class TaxiServiceFactory {
    private static final TaxiService service = new TaxiServiceImpl(
            () -> {
                ConfigurationManager configurationManager = ConfigurationManager.getInstance();
                Random random = new Random();

                int width = configurationManager.getSmartCityWidth();
                int genX = random.nextInt(2);

                int height = configurationManager.getSmartCityHeight();
                int genY = random.nextInt(2);

                return new SmartCityPosition(genX == 1 ? width - 1 : 0, genY == 1 ? height - 1 : 0);
            }
    );

    public static TaxiService getTaxiService() {
        return service;
    }
}
