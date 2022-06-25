package unimi.dsp.model;

import java.util.Objects;

public class RideElectionInfo {
    private RideElectionId rideElectionId;
    private RideElectionState rideElectionState;

    public RideElectionInfo(RideElectionId rideElectionId, RideElectionState rideElectionState) {
        this.rideElectionId = rideElectionId;
        this.rideElectionState = rideElectionState;
    }

    public RideElectionId getRideElectionId() {
        return rideElectionId;
    }

    public RideElectionState getRideElectionState() {
        return rideElectionState;
    }

    public void setRideElectionId(RideElectionId rideElectionId) {
        this.rideElectionId = rideElectionId;
    }

    public void setRideElectionState(RideElectionState rideElectionState) {
        this.rideElectionState = rideElectionState;
    }

    public static class RideElectionId {
        private final int taxiId;
        private final double distanceFromSP;
        private final int batteryLevel;

        public RideElectionId(int taxiId, double distanceFromSP, int batteryLevel) {
            this.taxiId = taxiId;
            this.distanceFromSP = distanceFromSP;
            this.batteryLevel = batteryLevel;
        }

        public int getTaxiId() {
            return taxiId;
        }

        public double getDistanceFromSP() {
            return distanceFromSP;
        }

        public int getBatteryLevel() {
            return batteryLevel;
        }

        public boolean isGreaterThan(RideElectionId rideElectionId) {
            if (this.distanceFromSP < rideElectionId.distanceFromSP)
                return true;
            else if (this.distanceFromSP > rideElectionId.distanceFromSP)
                return false;

            if (this.batteryLevel > rideElectionId.batteryLevel)
                return true;
            else if (this.batteryLevel < rideElectionId.batteryLevel)
                return false;

            return this.taxiId > rideElectionId.taxiId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RideElectionId that = (RideElectionId) o;
            return taxiId == that.taxiId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(taxiId);
        }
    }

    public enum RideElectionState {
        ELECTION,
        ELECTED
    }
}

