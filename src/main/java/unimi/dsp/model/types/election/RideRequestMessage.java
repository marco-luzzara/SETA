package unimi.dsp.model.types.election;

import unimi.dsp.dto.RideRequestDto;

import java.util.Optional;

public class RideRequestMessage {
    private final RideElectionInfo rideElectionInfo;
    private final RideRequestDto rideRequest;

    public RideRequestMessage(RideElectionInfo rideElectionInfo,
                              RideRequestDto rideRequest) {
        this.rideElectionInfo = rideElectionInfo;
        this.rideRequest = rideRequest;
    }

    public RideElectionInfo getRideElectionInfo() {
        return rideElectionInfo;
    }

    public RideRequestDto getRideRequest() {
        return rideRequest;
    }
}
