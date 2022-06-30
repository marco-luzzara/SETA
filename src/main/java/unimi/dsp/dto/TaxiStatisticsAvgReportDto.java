package unimi.dsp.dto;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Objects;

@XmlRootElement
public class TaxiStatisticsAvgReportDto {
    private double avgKmsTraveled;
    private double avgBatteryLevel;
    private double avgPollutionLevel;
    private double avgNumRides;

    private TaxiStatisticsAvgReportDto() {}
    public TaxiStatisticsAvgReportDto(double avgKmsTraveled,
                                      double avgBatteryLevel,
                                      double avgPollutionLevel,
                                      double avgNumRides) {
        this.avgKmsTraveled = avgKmsTraveled;
        this.avgBatteryLevel = avgBatteryLevel;
        this.avgPollutionLevel = avgPollutionLevel;
        this.avgNumRides = avgNumRides;
    }

    public double getAvgKmsTraveled() {
        return avgKmsTraveled;
    }
    public void setAvgKmsTraveled(double avgKmsTraveled) {
        this.avgKmsTraveled = avgKmsTraveled;
    }

    public double getAvgBatteryLevel() {
        return avgBatteryLevel;
    }
    public void setAvgBatteryLevel(double avgBatteryLevel) {
        this.avgBatteryLevel = avgBatteryLevel;
    }

    public double getAvgPollutionLevel() {
        return avgPollutionLevel;
    }
    public void setAvgPollutionLevel(double avgPollutionLevel) {
        this.avgPollutionLevel = avgPollutionLevel;
    }

    public double getAvgNumRides() {
        return avgNumRides;
    }
    public void setAvgNumRides(double avgNumRides) {
        this.avgNumRides = avgNumRides;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaxiStatisticsAvgReportDto taxiStatisticsReportOfTypeAvg = (TaxiStatisticsAvgReportDto) o;
        return Objects.equals(this.avgKmsTraveled, taxiStatisticsReportOfTypeAvg.avgKmsTraveled) &&
                Objects.equals(this.avgBatteryLevel, taxiStatisticsReportOfTypeAvg.avgBatteryLevel) &&
                Objects.equals(this.avgPollutionLevel, taxiStatisticsReportOfTypeAvg.avgPollutionLevel) &&
                Objects.equals(this.avgNumRides, taxiStatisticsReportOfTypeAvg.avgNumRides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(avgKmsTraveled, avgBatteryLevel, avgPollutionLevel, avgNumRides);
    }

    @Override
    public String toString() {
        return "TaxiStatisticsReportOfTypeAvg {\n" +
                "    avgKmsTraveled: " + avgKmsTraveled + "\n" +
                "    avgBatteryLevel: " + avgBatteryLevel + "\n" +
                "    avgPollutionLevel: " + avgPollutionLevel + "\n" +
                "    avgNumRides: " + avgNumRides + "\n" +
                "}";
    }
}
