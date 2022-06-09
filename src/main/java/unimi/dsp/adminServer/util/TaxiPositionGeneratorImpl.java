package unimi.dsp.adminServer.util;

import unimi.dsp.ConfigurationManager;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

// this class uses ThreadLocalRandom because performs better, but the Random class is thread-safe anyway
public class TaxiPositionGeneratorImpl implements TaxiPositionGenerator {
    @Override
    public int getXCoordinate() {
        try {
            int width = ConfigurationManager.getInstance().getSmartCityWidth();
            int genValue = ThreadLocalRandom.current().nextInt(2);
            return genValue == 1 ? width - 1 : 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getYCoordinate() {
        try {
            int height = ConfigurationManager.getInstance().getSmartCityHeight();
            int genValue = ThreadLocalRandom.current().nextInt(2);
            return genValue == 1 ? height - 1 : 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
