package unimi.dsp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ConfigurationManager {
    private static final String configFilePath = "config.properties";
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
}
