package unimi.dsp.util;

import java.util.logging.LogManager;
import java.util.logging.Logger;

public class LoggingUtil {
    public static Logger getLogger(Class cls) {
        return Logger.getLogger(cls.getName());
    }
}
