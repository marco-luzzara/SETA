package unimi.dsp.fakeFactories;

import unimi.dsp.taxi.Taxi;

public class FakeTaxiFactory {
    private static int portCounter = 5050;

    public static Taxi getTaxi(int seed) {
        portCounter++;
        return new Taxi(seed,
                "localhost",
                portCounter);
    }
}
