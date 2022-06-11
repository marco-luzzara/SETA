package unimi.dsp.dto;

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
        return new TaxiStatisticsDto(ts, seed,
                new TaxiStatisticsDto.TaxiStatisticsValues(seed, seed,
                        new ArrayList<Double>() {{ add((double)seed); }}));
    }
}
