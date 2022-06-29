package unimi.dsp.dto;

import unimi.dsp.dto.types.SerializableOffsetDateTime;
import unimi.dsp.util.DateTimeUtil;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.time.OffsetDateTime;
import java.util.Objects;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxiStatisticsDto {
    private String ts;
    private int batteryLevel;
    private TaxiStatisticsValues statsValues;

    public TaxiStatisticsDto() {}
    public TaxiStatisticsDto(SerializableOffsetDateTime ts, int batteryLevel, TaxiStatisticsValues statsValues) {
        this.ts = DateTimeUtil.getStringFromOffsetDateTime(ts.getOdt());
        this.batteryLevel = batteryLevel;
        this.statsValues = statsValues;
    }

    public OffsetDateTime getTs() {
        return DateTimeUtil.getOffsetDateTimeFromString(this.ts);
    }
    public void setTs(SerializableOffsetDateTime ts) {
        this.ts = DateTimeUtil.getStringFromOffsetDateTime(ts.getOdt());
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }
    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public TaxiStatisticsValues getStatsValues() {
        return statsValues;
    }
    public void setStatsValues(TaxiStatisticsValues statsValues) {
        this.statsValues = statsValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaxiStatisticsDto taxiStatistics = (TaxiStatisticsDto) o;
        return Objects.equals(this.ts, taxiStatistics.ts) &&
                Objects.equals(this.batteryLevel, taxiStatistics.batteryLevel) &&
                Objects.equals(this.statsValues, taxiStatistics.statsValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ts, batteryLevel, statsValues);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TaxiStatistics {\n");
        sb.append("    ts: ").append(ts).append("\n");
        sb.append("    batteryLevel: ").append(batteryLevel).append("\n");
        sb.append("    stats: ").append(statsValues).append("\n");
        sb.append("}");

        return sb.toString();
    }
}

