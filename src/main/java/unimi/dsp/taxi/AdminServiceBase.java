package unimi.dsp.taxi;

import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;

public interface AdminServiceBase {
    NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo);
    void unregisterTaxi(int taxiId);
    void loadTaxiStatistics(int taxiId, TaxiStatisticsDto taxiStatistics);
}
