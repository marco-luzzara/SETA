package unimi.dsp.dto;

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
        return "RideConfirm {\n" +
                "    id: " + this.rideId + "\n" +
                "}";
    }
}
