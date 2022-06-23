package unimi.dsp.taxi;

import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;

public interface AdminServiceBase {
    NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo);
    void unregisterTaxi(int taxiId);
}
