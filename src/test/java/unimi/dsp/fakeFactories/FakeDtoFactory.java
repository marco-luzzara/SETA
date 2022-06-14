package unimi.dsp.fakeFactories;

import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.dto.TaxiStatisticsValues;
import unimi.dsp.dto.types.SerializableOffsetDateTime;

import java.time.OffsetDateTime;
import java.util.ArrayList;

public class FakeDtoFactory {
    public static TaxiInfoDto createTaxiInfoDto(int seed) {
        return new TaxiInfoDto(seed,
                String.format("%d.%d.%d.%d", seed, seed, seed, seed),
                seed);
    }

    public static TaxiStatisticsDto createTaxiStatisticsDtoFromSeed(int seed) {
        return createTaxiStatisticsDtoFromParams(seed, OffsetDateTime.now());
    }

    public static TaxiStatisticsDto createTaxiStatisticsDtoFromSeed(int seed, OffsetDateTime ts) {
        return createTaxiStatisticsDtoFromParams(seed, ts);
    }

    private static TaxiStatisticsDto createTaxiStatisticsDtoFromParams(int seed, OffsetDateTime ts) {
        return new TaxiStatisticsDto(new SerializableOffsetDateTime(ts.toString()), seed,
                new TaxiStatisticsValues(seed, seed,
                        new ArrayList<Double>() {{ add((double)seed); }}));
    }
}
