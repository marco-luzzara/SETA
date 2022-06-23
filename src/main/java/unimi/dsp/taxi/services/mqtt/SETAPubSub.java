package unimi.dsp.taxi.services.mqtt;

import unimi.dsp.dto.RideConfirmDto;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.District;
import unimi.dsp.taxi.SETAPubSubBase;

import java.util.function.Consumer;

public class SETAPubSub implements SETAPubSubBase {
    @Override
    public void subscribeToDistrictTopic(District district, Consumer<RideRequestDto> eventAction) {

    }

    @Override
    public void publishRideConfirmation(RideConfirmDto rideConfirm) {

    }

    @Override
    public void unsubscribeFromCurrentDistrictTopic() {

    }
}
