package unimi.dsp.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigurationManager {
    private static final String configFilePath = "config.properties";
    // Properties is thread safe
    // https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html
    private static Properties props;
    private static volatile ConfigurationManager instance;

    public static ConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (ConfigurationManager.class) {
                if (instance == null)
                    instance = new ConfigurationManager();
            }
        }
        return instance;
    }

    private ConfigurationManager() {
        props = new Properties();
        try (FileInputStream ip = new FileInputStream(configFilePath)) {
            props.load(ip);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getAdminServerEndpoint() {
        return props.getProperty("adminServerEndpoint");
    }

    public String getBrokerEndpoint() {
        return props.getProperty("brokerEndpoint");
    }

    public int getSmartCityWidth() {
        return Integer.parseInt(props.getProperty("smartCityWidth"));
    }

    public int getSmartCityHeight() {
        return Integer.parseInt(props.getProperty("smartCityHeight"));
    }

    public int getSETAGenerationFrequencyMillis() {
        return Integer.parseInt(props.getProperty("SETAGenerationFrequencyMillis"));
    }

    public int getSETANumGeneratedRequest() {
        return Integer.parseInt(props.getProperty("SETANumGeneratedRequest"));
    }

    public String getRideRequestTopicPrefix() {
        return props.getProperty("rideRequestTopicPrefix");
    }
    public String getRideConfirmationTopic() {
        return props.getProperty("rideConfirmationTopic");
    }

    public int getNumDistricts() {
        return Integer.parseInt(props.getProperty("numDistricts"));
    }

    public int getRideRequestTimeout() {
        return Integer.parseInt(props.getProperty("rideRequestTimeout"));
    }

    public int getRideDeliveryDelay() {
        return Integer.parseInt(props.getProperty("rideDeliveryDelay"));
    }
    public int getBatteryConsumptionPerKm() {
        return Integer.parseInt(props.getProperty("batteryConsumptionPerKm"));
    }
    public int getBatteryThresholdBeforeRecharge() {
        return Integer.parseInt(props.getProperty("batteryThresholdBeforeRecharge"));
    }
    public int getRechargeDelay() {
        return Integer.parseInt(props.getProperty("rechargeDelay"));
    }
}
