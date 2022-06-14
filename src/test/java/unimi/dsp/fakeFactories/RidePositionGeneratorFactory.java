package unimi.dsp.fakeFactories;

import unimi.dsp.SETA.SetaSystem;
import unimi.dsp.dto.RideRequestDto;
import unimi.dsp.model.types.SmartCityPosition;

import java.util.List;
import java.util.Map;

public class RidePositionGeneratorFactory {
    public static SetaSystem.RideGenerator getGenerator(int xStart, int yStart, int xEnd, int yEnd) {
        return () -> new RideRequestDto(0,
                new SmartCityPosition(xStart, yStart),
                new SmartCityPosition(xEnd, yEnd));
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
