package unimi.dsp.model.types.election;

import unimi.dsp.dto.RideRequestDto;

import java.util.Optional;

public class RideRequestMessage {
    private final RideElectionInfo rideElectionInfo;
    private final RideRequestDto rideRequest;
    private final RideRequestSender sender;

    public RideRequestMessage(RideElectionInfo rideElectionInfo,
                              RideRequestDto rideRequest,
                              RideRequestSender sender) {
        this.rideElectionInfo = rideElectionInfo;
        this.rideRequest = rideRequest;
        this.sender = sender;
    }

    public RideRequestMessage(RideElectionInfo rideElectionInfo,
                              RideRequestDto rideRequest) {
        this(rideElectionInfo, rideRequest, null);
    }

    public RideElectionInfo getRideElectionInfo() {
        return rideElectionInfo;
    }

    public RideRequestDto getRideRequest() {
        return rideRequest;
    }

    public Optional<RideRequestSender> getSender() {
        return Optional.ofNullable(this.sender);
    }
}
