package unimi.dsp.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class RideRequestProcessingInfoDto {
    private int taxiId;
    private boolean isAvailable;
    private double distanceFromSP;
    private int batteryLevel;

    private RideRequestProcessingInfoDto() {}

    public RideRequestProcessingInfoDto(int taxiId, boolean isAvailable, double distanceFromSP, int batteryLevel) {
        this.taxiId = taxiId;
        this.isAvailable = isAvailable;
        this.distanceFromSP = distanceFromSP;
        this.batteryLevel = batteryLevel;
    }

    public int getTaxiId() {
        return taxiId;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public double getDistanceFromSP() {
        return distanceFromSP;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }
}
