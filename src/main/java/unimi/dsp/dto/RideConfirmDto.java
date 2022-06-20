package unimi.dsp.dto;

import unimi.dsp.model.types.SmartCityPosition;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Objects;

@XmlRootElement
public class RideConfirmDto {
    private int rideId;

    private RideConfirmDto() {}

    public RideConfirmDto(int rideId) {
        this.rideId = rideId;
    }

    public int getRideId() {
        return rideId;
    }

    public void setRideId(int rideId) {
        this.rideId = rideId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RideConfirmDto that = (RideConfirmDto) o;
        return rideId == that.rideId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rideId);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RideConfirm {\n");
        sb.append("    id: ").append(this.rideId).append("\n");
        sb.append("}");

        return sb.toString();
    }
}
