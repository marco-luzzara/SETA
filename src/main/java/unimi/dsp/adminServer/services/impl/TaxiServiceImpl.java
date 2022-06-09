package unimi.dsp.adminServer.services.impl;

import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.adminServer.services.TaxiService;

import java.time.OffsetDateTime;
import java.util.List;

public class TaxiServiceImpl implements TaxiService {
    @Override
    public List<TaxiInfoDto> getAllTaxis() {
        return null;
    }

    @Override
    public Object getTaxiStatisticsReport(int id, int n, String type) {
        return null;
    }

    @Override
    public Object getTaxisStatisticsReport(OffsetDateTime tsStart, OffsetDateTime tsEnd, String type) {
        return null;
    }

    @Override
    public void loadTaxiStatistics(int id, TaxiStatisticsDto taxiStatistics) {

    }

    @Override
    public NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo) {
        return null;
    }

    @Override
    public void removeTaxi(int id) {

    }
}
