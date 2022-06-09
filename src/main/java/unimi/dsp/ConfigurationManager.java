package unimi.dsp;

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

    public static ConfigurationManager getInstance() throws IOException {
        if (instance == null) {
            synchronized (ConfigurationManager.class) {
                if (instance == null)
                    instance = new ConfigurationManager();
            }
        }
        return instance;
    }

    private ConfigurationManager() throws IOException {
        props = new Properties();
        try (FileInputStream ip = new FileInputStream(configFilePath)) {
            props.load(ip);
        }
    }

    public String getAdminServerHost() {
        return props.getProperty("adminServerHost");
    }

    public int getAdminServerPort() {
        return Integer.parseInt(props.getProperty("adminServerPort"));
    }

    public int getSmartCityWidth() {
        return Integer.parseInt(props.getProperty("smartCityWidth"));
    }

    public int getSmartCityHeight() {
        return Integer.parseInt(props.getProperty("smartCityHeight"));
    }
}
