package unimi.dsp.adminServer.services;

import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.exceptions.ReportTypeNotFoundException;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.model.types.TaxiStatisticsReportType;

import java.time.OffsetDateTime;
import java.util.List;

public interface TaxiService {
    List<TaxiInfoDto> getAllTaxis();
    Object getTaxiStatisticsReport(int id, int n, TaxiStatisticsReportType type) throws IdNotFoundException, ReportTypeNotFoundException;
    Object getTaxisStatisticsReport(OffsetDateTime tsStart, OffsetDateTime tsEnd, TaxiStatisticsReportType type) throws ReportTypeNotFoundException;
    void loadTaxiStatistics(int id, TaxiStatisticsDto taxiStatistics) throws IdNotFoundException;
    NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo) throws IdAlreadyRegisteredException;
    void removeTaxi(int id) throws IdNotFoundException;
}
