package unimi.dsp.fakeFactories;

import unimi.dsp.taxi.AdminServiceBase;
import unimi.dsp.taxi.SETATaxiPubSubBase;
import unimi.dsp.taxi.Taxi;
import unimi.dsp.taxi.services.mqtt.SETATaxiPubSub;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;

public class FakeTaxiFactory {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static int portCounter = 5050;

    public static Taxi getTaxi(int seed, AdminServiceBase adminService) {
        return getTaxi(seed,
                new Taxi.TaxiConfig(),
                adminService);
    }

    public static Taxi getTaxi(int seed, int rideDeliveryDelay, AdminServiceBase adminService) {
        return getTaxi(seed,
                new Taxi.TaxiConfig().withRideDeliveryDelay(rideDeliveryDelay),
                adminService);
    }

    public static Taxi getTaxi(int seed,
                               Taxi.TaxiConfig taxiConfig,
                               AdminServiceBase adminService) {
        portCounter++;
        return new Taxi(seed,
                "localhost",
                portCounter,
                taxiConfig,
                adminService,
                new SETATaxiPubSub(MQTTClientFactory.getClient()));
    }
}
