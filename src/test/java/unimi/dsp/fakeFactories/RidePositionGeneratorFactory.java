package unimi.dsp.fakeFactories;

import unimi.dsp.SETA.SetaSystem;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.SmartCityPosition;

import java.util.List;
import java.util.Map;

public class RidePositionGeneratorFactory {
    public static RideRequestDto getRideRequest(int xStart, int yStart, int xEnd, int yEnd) {
        return getRideRequest(0, xStart, yStart, xEnd, yEnd);
    }

    public static RideRequestDto getRideRequest(int rideId, int xStart, int yStart, int xEnd, int yEnd) {
        return new RideRequestDto(rideId,
                new SmartCityPosition(xStart, yStart),
                new SmartCityPosition(xEnd, yEnd));
    }

    public static SetaSystem.RideGenerator getGenerator(int xStart, int yStart, int xEnd, int yEnd) {
        return () -> RidePositionGeneratorFactory.getRideRequest(xStart, yStart, xEnd, yEnd);
    }

    /**
     * return a generator
     * @param rides the sequence of smart city positions
     * @return
     */
    public static SetaSystem.RideGenerator getGenerator(RideRequestDto... rides) {
        return new SetaSystem.RideGenerator() {
            private int counter = -1;
            @Override
            public RideRequestDto generateRide() {
                counter++;
                return rides[counter];
            }
        };
    }
}
