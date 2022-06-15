package unimi.dsp.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

    public String getAdminServerHost() {
        return props.getProperty("adminServerHost");
    }

    public int getAdminServerPort() {
        return Integer.parseInt(props.getProperty("adminServerPort"));
    }

    public String getBrokerHost() {
        return props.getProperty("brokerHost");
    }

    public int getBrokerPort() {
        return Integer.parseInt(props.getProperty("brokerPort"));
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
}