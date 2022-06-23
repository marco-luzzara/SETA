package unimi.dsp.fakeFactories;

import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.stubs.AdminServiceStub;
import unimi.dsp.taxi.AdminServiceBase;
import unimi.dsp.taxi.SETAPubSubBase;
import unimi.dsp.taxi.Taxi;
import unimi.dsp.taxi.services.mqtt.SETAPubSub;
import unimi.dsp.util.ConfigurationManager;
import unimi.dsp.util.MQTTClientFactory;

public class FakeTaxiFactory {
    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static int portCounter = 5050;
    private static SETAPubSubBase setaPubSub = new SETAPubSub(MQTTClientFactory.getClient());

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
