package unimi.dsp.adminServer.dto;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Objects;
import java.time.OffsetDateTime;

@XmlRootElement
public class TaxiStatisticsDto {
    private OffsetDateTime ts;
    private Integer batteryLevel;
    private TaxiStatisticsValues statsValues;

    public OffsetDateTime getTs() {
        return ts;
    }
    public void setTs(OffsetDateTime ts) {
        this.ts = ts;
    }

    public Integer getBatteryLevel() {
        return batteryLevel;
    }
    public void setBatteryLevel(Integer batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public TaxiStatisticsValues getStats() {
        return statsValues;
    }
    public void setStats(TaxiStatisticsValues statsValues) {
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

    public class TaxiStatisticsValues {
        private Integer kmsTraveled;
        private Integer numRides;
        private List<Double> pollutionAvgList = null;

        public Integer getKmsTraveled() {
            return kmsTraveled;
        }
        public void setKmsTraveled(Integer kmsTraveled) {
            this.kmsTraveled = kmsTraveled;
        }

        public Integer getNumRides() {
            return numRides;
        }
        public void setNumRides(Integer numRides) {
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
            StringBuilder sb = new StringBuilder();
            sb.append("TaxiStatisticsStats {\n");
            sb.append("        kmsTraveled: ").append(kmsTraveled).append("\n");
            sb.append("        numRides: ").append(numRides).append("\n");
            sb.append("        pollutionAvgList: ").append(pollutionAvgList).append("\n");
            sb.append("    }");

            return sb.toString();
        }
    }
}

