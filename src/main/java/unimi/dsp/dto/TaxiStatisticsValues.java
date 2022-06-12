package unimi.dsp.dto;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Objects;

@XmlRootElement
public class TaxiStatisticsValues {
    private int kmsTraveled;
    private int numRides;
    private List<Double> pollutionAvgList = null;

    public TaxiStatisticsValues() {}
    public TaxiStatisticsValues(int kmsTraveled, int numRides, List<Double> pollutionAvgList) {
        this.kmsTraveled = kmsTraveled;
        this.numRides = numRides;
        this.pollutionAvgList = pollutionAvgList;
    }

    public int getKmsTraveled() {
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
        StringBuilder sb = new StringBuilder();
        sb.append("TaxiStatisticsStats {\n");
        sb.append("        kmsTraveled: ").append(kmsTraveled).append("\n");
        sb.append("        numRides: ").append(numRides).append("\n");
        sb.append("        pollutionAvgList: ").append(pollutionAvgList).append("\n");
        sb.append("    }");

        return sb.toString();
    }
}
