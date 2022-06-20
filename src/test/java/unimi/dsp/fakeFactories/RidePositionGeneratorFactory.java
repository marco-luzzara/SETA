package unimi.dsp.fakeFactories;

import unimi.dsp.SETA.SetaSystem;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.SmartCityPosition;

import java.util.List;
import java.util.Map;

public class RidePositionGeneratorFactory {
    private static int rideRequestCounter = -1;
    public static RideRequestDto getRideRequest(int xStart, int yStart, int xEnd, int yEnd) {
        rideRequestCounter++;
        return new RideRequestDto(rideRequestCounter,
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
