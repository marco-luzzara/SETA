package unimi.dsp.dto;

import unimi.dsp.model.types.SmartCityPosition;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Objects;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class RideRequestDto {
    private int id;
    private long timestamp;
    private int xStart;
    private int yStart;
    private int xEnd;
    private int yEnd;

    private RideRequestDto() {}

    public RideRequestDto(int id, SmartCityPosition start, SmartCityPosition end) {
        this.id = id;
        this.xStart = start.x;
        this.yStart = start.y;
        this.xEnd = end.x;
        this.yEnd = end.y;
        this.timestamp = System.currentTimeMillis();
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

    public long getTimestamp() {
        return timestamp;
    }

    public void resetTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RideRequestDto that = (RideRequestDto) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
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
