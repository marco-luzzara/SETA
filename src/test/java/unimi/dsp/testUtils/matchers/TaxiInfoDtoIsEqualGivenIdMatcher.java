package unimi.dsp.testUtils.matchers;

import org.mockito.ArgumentMatcher;
import unimi.dsp.dto.TaxiInfoDto;

public class TaxiInfoDtoIsEqualGivenIdMatcher implements ArgumentMatcher<TaxiInfoDto> {
    private final int taxiId;

    public TaxiInfoDtoIsEqualGivenIdMatcher(int taxiId) {
        this.taxiId = taxiId;
    }

    public boolean matches(TaxiInfoDto taxiInfo) {
        return taxiInfo.getId() == this.taxiId;
    }
    public String toString() {
        return "{ TaxiInfoDto - Id = " + this.taxiId + " }";
    }
}
