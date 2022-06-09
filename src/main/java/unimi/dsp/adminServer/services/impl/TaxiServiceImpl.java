package unimi.dsp.adminServer.services.impl;

import unimi.dsp.adminServer.dto.TaxiInfoDto;
import unimi.dsp.adminServer.dto.TaxiStatisticsDto;
import unimi.dsp.adminServer.services.TaxiService;

import java.time.OffsetDateTime;

public class TaxiServiceImpl implements TaxiService {
    @Override
    public void getAllTaxis() {

    }

    @Override
    public void getTaxiStatisticsReport(Integer id, Integer n, String type) {

    }

    @Override
    public void getTaxisStatisticsReport(OffsetDateTime tsStart, OffsetDateTime tsEnd, String type) {

    }

    @Override
    public void loadTaxiStatistics(Integer id, TaxiStatisticsDto taxiStatistics) {

    }

    @Override
    public void registerTaxi(TaxiInfoDto taxiInfo) {

    }

    @Override
    public void removeTaxi(Integer id) {

    }
}
