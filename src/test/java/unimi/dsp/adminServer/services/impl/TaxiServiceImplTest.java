package unimi.dsp.adminServer.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.exceptions.ReportTypeNotFoundException;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.adminServer.services.impl.TaxiServiceImpl;
import unimi.dsp.dto.*;
import unimi.dsp.fakeFactories.FakeDtoFactory;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.model.types.TaxiStatisticsReportType;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaxiServiceImplTest {
    private final TaxiPositionGenerator taxiPositionGenerator = mock(TaxiPositionGenerator.class);
    private final TaxiService service = new TaxiServiceImpl(taxiPositionGenerator);

    @BeforeEach
    public void initializeTest() {
        when(taxiPositionGenerator.getStartingPosition()).thenReturn(new SmartCityPosition(0, 0));
    }

    @Test
    public void Given0RegisteredTaxi_WhenGetAllTaxis_ThenReturn0Taxis() {
        List<TaxiInfoDto> taxiInfos = service.getAllTaxis();

        assertEquals(0, taxiInfos.size());
    }

    @Test
    public void GivenManyRegisteredTaxi_WhenGetAllTaxis_ThenReturnManyTaxis()
            throws IdAlreadyRegisteredException {
        service.registerTaxi(FakeDtoFactory.createTaxiInfoDto(1));
        service.registerTaxi(FakeDtoFactory.createTaxiInfoDto(2));

        List<TaxiInfoDto> taxiInfos = service.getAllTaxis();

        assertEquals(2, taxiInfos.size());
    }

    @Test
    public void GivenANewTaxi_WhenItIsTheFirstToBeRegistered_ThenThereIsNoTaxiInfo()
            throws IdAlreadyRegisteredException {
        when(taxiPositionGenerator.getStartingPosition())
                .thenReturn(new SmartCityPosition(0, 9));
        TaxiInfoDto taxiInfo = FakeDtoFactory.createTaxiInfoDto(1);

        NewTaxiDto newTaxiDto = service.registerTaxi(taxiInfo);

        assertEquals(0, newTaxiDto.getX());
        assertEquals(9, newTaxiDto.getY());
        assertEquals(0, newTaxiDto.getTaxiInfos().size());
    }

    @Test
    public void GivenANewTaxi_WhenManyTaxisAreRegistered_ThenThereAreManyTaxiInfo()
            throws IdAlreadyRegisteredException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        TaxiInfoDto taxiInfo2 = FakeDtoFactory.createTaxiInfoDto(2);
        TaxiInfoDto taxiInfo3 = FakeDtoFactory.createTaxiInfoDto(3);
        service.registerTaxi(taxiInfo1);
        service.registerTaxi(taxiInfo2);

        NewTaxiDto newTaxiDto = service.registerTaxi(taxiInfo3);

        assertEquals(2, newTaxiDto.getTaxiInfos().size());
    }

    @Test
    public void GivenANewTaxi_WhenAnotherTaxiHasTheSameId_ThenThrow()
            throws IdAlreadyRegisteredException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        TaxiInfoDto taxiInfo2 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);

        assertThrows(IdAlreadyRegisteredException.class,
                () -> service.registerTaxi(taxiInfo2));

        assertEquals(1, service.getAllTaxis().size());
    }

    @Test
    public void GivenARegisteredTaxi_WhenItIsRemoved_ThenItDoesNotAppearAnymore()
            throws IdAlreadyRegisteredException, IdNotFoundException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);

        service.removeTaxi(taxiInfo1.getId());

        assertEquals(0, service.getAllTaxis().size());
    }

    @Test
    public void GivenANonExistingId_WhenTaxiIsRemoved_ThenThrow()
            throws IdAlreadyRegisteredException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);

        assertThrows(IdNotFoundException.class,
                () -> service.removeTaxi(2));
    }

    @Test
    public void GivenANonExistingId_WhenLoadTaxiStatistics_ThenThrow()
            throws IdAlreadyRegisteredException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);

        assertThrows(IdNotFoundException.class,
                () -> service.loadTaxiStatistics(2, null));
    }

    @Test
    public void GivenANonExistingId_WhenGetTaxiStatisticsReport_ThenThrow()
            throws IdAlreadyRegisteredException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);

        assertThrows(IdNotFoundException.class,
                () -> service.getTaxiStatisticsReport(2, 1, TaxiStatisticsReportType.AVERAGE));
    }

    @Test
    public void GivenASingleStatistics_WhenGetAvgReportOfSingleTaxi_ThenReturnStatUnchanged()
            throws IdAlreadyRegisteredException, IdNotFoundException, ReportTypeNotFoundException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);
        TaxiStatisticsDto taxiStatisticsDto = loadTaxiStatistics(1, 1, OffsetDateTime.now(ZoneOffset.UTC));

        TaxiStatisticsAvgReportDto report = (TaxiStatisticsAvgReportDto) service
                .getTaxiStatisticsReport(1, 1, TaxiStatisticsReportType.AVERAGE);

        assertEquals(taxiStatisticsDto.getBatteryLevel(), report.getAvgBatteryLevel());
        assertEquals(taxiStatisticsDto.getStatsValues().getKmsTraveled(), report.getAvgKmsTraveled());
        assertEquals(taxiStatisticsDto.getStatsValues().getNumRides(), report.getAvgNumRides());
        assertEquals(taxiStatisticsDto.getStatsValues().getPollutionAvgList().get(0), report.getAvgPollutionLevel());
    }

    @Test
    public void GivenManyStatistics_WhenGetAvgReportOfSingleTaxi_ThenReturnAvg()
            throws IdAlreadyRegisteredException, IdNotFoundException, ReportTypeNotFoundException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);
        loadTaxiStatistics(1, 1, OffsetDateTime.now(ZoneOffset.UTC));
        loadTaxiStatistics(1, 2, OffsetDateTime.now(ZoneOffset.UTC));

        TaxiStatisticsAvgReportDto report = (TaxiStatisticsAvgReportDto) service
                .getTaxiStatisticsReport(1, 2, TaxiStatisticsReportType.AVERAGE);

        assertEquals(1.5, report.getAvgBatteryLevel());
        assertEquals(1.5, report.getAvgKmsTraveled());
        assertEquals(1.5, report.getAvgNumRides());
        assertEquals(1.5, report.getAvgPollutionLevel());
    }

    @Test
    public void GivenEndBeforeStart_WhenGetAvgReportOfAllTaxis_ThenThrow() {
        assertThrows(IllegalArgumentException.class, () -> service
                .getTaxisStatisticsReport(OffsetDateTime.now(),
                        OffsetDateTime.now().minus(Duration.ofDays(1)), TaxiStatisticsReportType.AVERAGE));
    }

    @Test
    public void GivenManyStatistics_WhenGetAvgReportOfAllTaxisWithFilterOnTs_ThenReturnPartialStats()
            throws IdAlreadyRegisteredException, IdNotFoundException, ReportTypeNotFoundException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);
        loadTaxiStatistics(1, 1, OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(5)));
        TaxiStatisticsDto taxiStatisticsDto = loadTaxiStatistics(1, 2,
                OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(3)));
        loadTaxiStatistics(1, 3, OffsetDateTime.now(ZoneOffset.UTC));

        TaxiStatisticsAvgReportDto report = (TaxiStatisticsAvgReportDto) service
                .getTaxisStatisticsReport(
                        OffsetDateTime.now().minus(Duration.ofDays(4)),
                        OffsetDateTime.now().minus(Duration.ofDays(2)),
                        TaxiStatisticsReportType.AVERAGE);

        assertEquals(taxiStatisticsDto.getBatteryLevel(), report.getAvgBatteryLevel());
        assertEquals(taxiStatisticsDto.getStatsValues().getKmsTraveled(), report.getAvgKmsTraveled());
        assertEquals(taxiStatisticsDto.getStatsValues().getNumRides(), report.getAvgNumRides());
        assertEquals(taxiStatisticsDto.getStatsValues().getPollutionAvgList().get(0), report.getAvgPollutionLevel());
    }

    @Test
    public void GivenManyStatistics_WhenGetAvgReportForAllTaxis_ThenReturnAvg()
            throws IdAlreadyRegisteredException, IdNotFoundException, ReportTypeNotFoundException {
        TaxiInfoDto taxiInfo1 = FakeDtoFactory.createTaxiInfoDto(1);
        service.registerTaxi(taxiInfo1);
        TaxiInfoDto taxiInfo2 = FakeDtoFactory.createTaxiInfoDto(2);
        service.registerTaxi(taxiInfo2);
        loadTaxiStatistics(1, 1, OffsetDateTime.now(ZoneOffset.UTC));
        loadTaxiStatistics(2, 2, OffsetDateTime.now(ZoneOffset.UTC));

        TaxiStatisticsAvgReportDto report = (TaxiStatisticsAvgReportDto) service
                .getTaxisStatisticsReport(
                        OffsetDateTime.now(ZoneOffset.UTC).minus(Duration.ofDays(3)),
                        OffsetDateTime.now(ZoneOffset.UTC),
                        TaxiStatisticsReportType.AVERAGE
                );

        assertEquals(1.5, report.getAvgBatteryLevel());
        assertEquals(1.5, report.getAvgKmsTraveled());
        assertEquals(1.5, report.getAvgNumRides());
        assertEquals(1.5, report.getAvgPollutionLevel());
    }

    private TaxiStatisticsDto loadTaxiStatistics(int taxiId, int seed, OffsetDateTime ts)
            throws IdNotFoundException {
        TaxiStatisticsDto taxiStatisticsDto = FakeDtoFactory.createTaxiStatisticsDtoFromSeed(seed, ts);
        service.loadTaxiStatistics(taxiId, taxiStatisticsDto);

        return taxiStatisticsDto;
    }
}
