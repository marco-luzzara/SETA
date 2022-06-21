package unimi.dsp.fakeFactories;

import unimi.dsp.taxi.Taxi;
import unimi.dsp.util.ConfigurationManager;

public class FakeTaxiFactory {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static int portCounter = 5050;

    public static Taxi getTaxi(int seed) {
        return getTaxi(seed,
                configurationManager.getRideDeliveryDelay(),
                configurationManager.getBatteryConsumptionPerKm(),
                configurationManager.getBatteryThresholdBeforeRecharge(),
                configurationManager.getRechargeDelay());
    }

    public static Taxi getTaxi(int seed,
                               int rideDeliveryDelay,
                               int batteryConsumptionPerKm,
                               int batteryThresholdBeforeRecharge,
                               int rechargeDelay) {
        portCounter++;
        return new Taxi(seed,
                "localhost",
                portCounter,
                rideDeliveryDelay,
                batteryConsumptionPerKm,
                batteryThresholdBeforeRecharge,
                rechargeDelay);
    }
}
