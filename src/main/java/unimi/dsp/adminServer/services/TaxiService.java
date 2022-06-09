package unimi.dsp.adminServer.services;

import unimi.dsp.adminServer.dto.TaxiInfoDto;
import unimi.dsp.adminServer.dto.TaxiStatisticsDto;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.time.OffsetDateTime;

public interface TaxiService {
    void getAllTaxis();
    void getTaxiStatisticsReport(Integer id, Integer n,String type);
    void getTaxisStatisticsReport(OffsetDateTime tsStart, OffsetDateTime tsEnd, String type);
    void loadTaxiStatistics(Integer id, TaxiStatisticsDto taxiStatistics);
    void registerTaxi(TaxiInfoDto taxiInfo);
    void removeTaxi(Integer id);
}
