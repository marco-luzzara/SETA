package unimi.dsp.dto.types;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SerializableOffsetDateTime {
    private OffsetDateTime odt;

    public SerializableOffsetDateTime(String odtString) {
        this.odt = OffsetDateTime.parse(odtString, DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSX"));
    }

    public OffsetDateTime getOdt() {
        return odt;
    }
}
