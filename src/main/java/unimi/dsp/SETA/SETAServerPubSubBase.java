package unimi.dsp.SETA;

import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.District;

import java.util.function.Consumer;

public interface SETAServerPubSubBase {
    void publishRideRequest(RideRequestDto rideRequest);

    void subscribeToRideConfirmationTopic(Consumer<RideConfirmDto> confirmAction);

    void unsubscribeFromRideConfirmationTopic();
}
