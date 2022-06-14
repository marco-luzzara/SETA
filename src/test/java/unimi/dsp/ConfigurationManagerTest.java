package unimi.dsp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import unimi.dsp.util.ConfigurationManager;

public class ConfigurationManagerTest {
    @Test
    public void getAdminServerHost_fromDefaultFile() {
        ConfigurationManager manager = ConfigurationManager.getInstance();
        Assertions.assertEquals("localhost", manager.getAdminServerHost());
    }
}
