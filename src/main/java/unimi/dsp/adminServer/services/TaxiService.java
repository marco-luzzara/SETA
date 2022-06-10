package unimi.dsp.adminServer.services;

import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaxiService {
    List<TaxiInfoDto> getAllTaxis();
    Object getTaxiStatisticsReport(int id, int n, String type);
    Object getTaxisStatisticsReport(OffsetDateTime tsStart, OffsetDateTime tsEnd, String type);
    void loadTaxiStatistics(int id, TaxiStatisticsDto taxiStatistics);
    NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo) throws IdAlreadyRegisteredException;
    void removeTaxi(int id) throws IdNotFoundException;
}
