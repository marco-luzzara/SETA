package unimi.dsp.taxi;

import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.District;

import java.io.Closeable;
import java.util.function.Consumer;

public interface SETATaxiPubSubBase {
    void subscribeToDistrictTopic(District district, Consumer<RideRequestDto> eventAction);
    void publishRideConfirmation(RideConfirmDto rideConfirm);
    void unsubscribeFromDistrictTopic(District district);
}
