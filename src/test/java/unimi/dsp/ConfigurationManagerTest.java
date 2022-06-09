package unimi.dsp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ConfigurationManagerTest {
    @Test
    public void getAdminServerHost_fromDefaultFile() throws IOException {
        ConfigurationManager manager = ConfigurationManager.getInstance();
        Assertions.assertEquals("localhost", manager.getAdminServerHost());
    }
}
