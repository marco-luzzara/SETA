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

    public double getDistanceBetweenRideStartAndEnd() {
        double deltaX = this.getEnd().x - this.getStart().x;
        double deltaY = this.getEnd().y - this.getStart().y;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    @Override
    public String toString() {
        return "RideRequest {\n" +
                "    id: " + id + "\n" +
                "    start: " + new SmartCityPosition(xStart, yStart) + "\n" +
                "    end: " + new SmartCityPosition(xEnd, yEnd) + "\n" +
                "}";
    }
}
