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
    private static SETATaxiPubSubBase setaPubSub = new SETATaxiPubSub(MQTTClientFactory.getClient());

    public static Taxi getTaxi(int seed, AdminServiceBase adminService) {
        return getTaxi(seed,
                configurationManager.getRideDeliveryDelay(),
                configurationManager.getBatteryConsumptionPerKm(),
                configurationManager.getBatteryThresholdBeforeRecharge(),
                configurationManager.getRechargeDelay(),
                adminService);
    }

    public static Taxi getTaxi(int seed,
                               int rideDeliveryDelay,
                               int batteryConsumptionPerKm,
                               int batteryThresholdBeforeRecharge,
                               int rechargeDelay,
                               AdminServiceBase adminService) {
        portCounter++;
        return new Taxi(seed,
                "localhost",
                portCounter,
                rideDeliveryDelay,
                batteryConsumptionPerKm,
                batteryThresholdBeforeRecharge,
                rechargeDelay,
                adminService,
                setaPubSub);
    }
}
