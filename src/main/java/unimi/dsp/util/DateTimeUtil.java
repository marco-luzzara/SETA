package unimi.dsp.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {
    public static final String DATETIME_FORMAT = "uuuu-MM-dd'T'HH:mm:ss.SSSX";

    public static OffsetDateTime getOffsetDateTimeFromString(String s) {
        return OffsetDateTime.parse(s, DateTimeFormatter.ofPattern(DATETIME_FORMAT));
    }

    public static String getStringFromOffsetDateTime(OffsetDateTime odt) {
        return odt.format(DateTimeFormatter.ofPattern(DATETIME_FORMAT));
    }
}
