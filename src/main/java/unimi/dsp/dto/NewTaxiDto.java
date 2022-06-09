package unimi.dsp.dto;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Objects;

@XmlRootElement
public class NewTaxiDto {
    private int x;
    private int y;
    private List<TaxiInfoDto> taxiInfos;

    public NewTaxiDto() {}

    public NewTaxiDto(int x, int y, List<TaxiInfoDto> taxiInfos) {
        this.x = x;
        this.y = y;
        this.taxiInfos = taxiInfos;
    }

    public int getX() {
        return x;
    }
    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }
    public void setY(int y) {
        this.y = y;
    }

    public List<TaxiInfoDto> getTaxiInfos() {
        return taxiInfos;
    }
    public void setTaxiInfos(List<TaxiInfoDto> taxiInfos) {
        this.taxiInfos = taxiInfos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewTaxiDto that = (NewTaxiDto) o;
        return x == that.x && y == that.y && taxiInfos.equals(that.taxiInfos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, taxiInfos);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NewTaxi {\n");
        sb.append("    x: ").append(x).append("\n");
        sb.append("    y: ").append(y).append("\n");
        sb.append("    taxiInfo: ").append(taxiInfos).append("\n");
        sb.append("}");

        return sb.toString();
    }
}
