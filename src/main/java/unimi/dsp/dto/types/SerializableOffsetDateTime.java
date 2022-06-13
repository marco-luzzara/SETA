package unimi.dsp.dto.types;

import unimi.dsp.util.DateTimeUtil;

import java.time.OffsetDateTime;

public class SerializableOffsetDateTime {
    private OffsetDateTime odt;

    public SerializableOffsetDateTime(String odtString) {
        this.odt = DateTimeUtil.getOffsetDateTimeFromString(odtString);
    }

    public OffsetDateTime getOdt() {
        return odt;
    }
}
