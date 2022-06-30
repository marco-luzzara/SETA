package unimi.dsp.dto;

import unimi.dsp.dto.types.SerializableOffsetDateTime;
import unimi.dsp.util.DateTimeUtil;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class TaxiStatisticsDto {
    private String ts;
    private int batteryLevel;
    private TaxiStatisticsValues statsValues;

    private TaxiStatisticsDto() {}
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
        return "TaxiStatistics {\n" +
                "    ts: " + ts + "\n" +
                "    batteryLevel: " + batteryLevel + "\n" +
                "    stats: " + statsValues + "\n" +
                "}";
    }

    @XmlRootElement
    public static class TaxiStatisticsValues {
        private double kmsTraveled;
        private int numRides;
        private List<Double> pollutionAvgList = null;

        private TaxiStatisticsValues() {}
        public TaxiStatisticsValues(double kmsTraveled, int numRides, List<Double> pollutionAvgList) {
            this.kmsTraveled = kmsTraveled;
            this.numRides = numRides;
            this.pollutionAvgList = pollutionAvgList;
        }

        public double getKmsTraveled() {
            return kmsTraveled;
        }
        public void setKmsTraveled(int kmsTraveled) {
            this.kmsTraveled = kmsTraveled;
        }

        public int getNumRides() {
            return numRides;
        }
        public void setNumRides(int numRides) {
            this.numRides = numRides;
        }

        public List<Double> getPollutionAvgList() {
            return pollutionAvgList;
        }
        public void setPollutionAvgList(List<Double> pollutionAvgList) {
            this.pollutionAvgList = pollutionAvgList;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TaxiStatisticsValues taxiStatisticsValues = (TaxiStatisticsValues) o;
            return Objects.equals(this.kmsTraveled, taxiStatisticsValues.kmsTraveled) &&
                    Objects.equals(this.numRides, taxiStatisticsValues.numRides) &&
                    Objects.equals(this.pollutionAvgList, taxiStatisticsValues.pollutionAvgList);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kmsTraveled, numRides, pollutionAvgList);
        }

        @Override
        public String toString() {
            return "TaxiStatisticsStats {\n" +
                    "        kmsTraveled: " + kmsTraveled + "\n" +
                    "        numRides: " + numRides + "\n" +
                    "        pollutionAvgList: " + pollutionAvgList + "\n" +
                    "    }";
        }
    }
}

