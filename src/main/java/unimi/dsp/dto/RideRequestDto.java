package unimi.dsp.dto;

import unimi.dsp.model.types.District;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.util.ConfigurationManager;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.Objects;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class RideRequestDto {
    private int id;
    private int xStart;
    private int yStart;
    private int xEnd;
    private int yEnd;

    private RideRequestDto() {}

    public RideRequestDto(int id, SmartCityPosition start, SmartCityPosition end) {
        if (start.equals(end)) {
            throw new IllegalArgumentException("start and end must be different");
        }

        this.id = id;
        this.xStart = start.x;
        this.yStart = start.y;
        this.xEnd = end.x;
        this.yEnd = end.y;
    }

    public int getId() {
        return id;
    }

    public SmartCityPosition getStart() {
        return new SmartCityPosition(xStart, yStart);
    }

    public SmartCityPosition getEnd() {
        return new SmartCityPosition(xEnd, yEnd);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RideRequestDto that = (RideRequestDto) o;
        return id == that.id && xStart == that.xStart && yStart == that.yStart &&
                xEnd == that.xEnd && yEnd == that.yEnd;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, xStart, yStart, xEnd, yEnd);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RideRequest {\n");
        sb.append("    id: ").append(id).append("\n");
        sb.append("    start: ").append(new SmartCityPosition(xStart, yStart)).append("\n");
        sb.append("    end: ").append(new SmartCityPosition(xEnd, yEnd)).append("\n");
        sb.append("}");

        return sb.toString();
    }
}
