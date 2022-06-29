package unimi.dsp.adminServer.services.impl;

import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.exceptions.ReportTypeNotFoundException;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsAvgReportDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.model.types.TaxiStatisticsReportType;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.*;

public class TaxiServiceImpl implements TaxiService {
    private final TaxiPositionGenerator taxiPositionGenerator;
    private final HashMap<Integer, TaxiInfo> taxiInfos = new HashMap<>();

    public TaxiServiceImpl(TaxiPositionGenerator taxiPositionGenerator) {
        this.taxiPositionGenerator = taxiPositionGenerator;
    }

    @Override
    public List<TaxiInfoDto> getAllTaxis() {
        return taxiInfos.entrySet().stream().map(e ->
                new TaxiInfoDto(
                        e.getKey(),
                        e.getValue().getIpAddress(),
                        e.getValue().getPort()))
            .collect(Collectors.toList());
    }

    @Override
    public Object getTaxiStatisticsReport(int id, int n, TaxiStatisticsReportType type)
            throws IdNotFoundException, ReportTypeNotFoundException {
        checkTaxiIdExists(id);

        if (type == TaxiStatisticsReportType.AVERAGE) {
            return getTaxiStatisticsAvgReport(id, n);
        }
        throw new ReportTypeNotFoundException(type);
    }

    private TaxiStatisticsAvgReportDto getTaxiStatisticsAvgReport(int id, int n) {
        // reverse iterator to stream
        // https://mkyong.com/java8/java-8-how-to-convert-iterator-to-stream/
        Iterator<TaxiStatisticsDto> reverseIt = this.taxiInfos.get(id).taxiStatisticsList.descendingIterator();
        List<TaxiStatisticsDto> statsList = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        reverseIt,
                        Spliterator.ORDERED),
                 false)
            .limit(n)
            .collect(Collectors.toList());

        return createAvgReportFromListOfStatistics(statsList);
    }

    @Override
    public Object getTaxisStatisticsReport(OffsetDateTime tsStart,
                                           OffsetDateTime tsEnd,
                                           TaxiStatisticsReportType type) throws ReportTypeNotFoundException {
        if (tsEnd.isBefore(tsStart))
            throw new IllegalArgumentException("tsStart must come before tsEnd");

        if (type == TaxiStatisticsReportType.AVERAGE) {
            return getTaxisStatisticsAvgReport(tsStart, tsEnd);
        }
        throw new ReportTypeNotFoundException(type);
    }

    private TaxiStatisticsAvgReportDto getTaxisStatisticsAvgReport(OffsetDateTime tsStart, OffsetDateTime tsEnd) {
        List<TaxiStatisticsDto> statsList = this.taxiInfos.values().stream()
                .flatMap(ti -> ti.getTaxiStatisticsList().stream())
                .filter(tsDto -> !tsDto.getTs().isBefore(tsStart) && !tsDto.getTs().isAfter(tsEnd))
                .collect(Collectors.toList());

        return createAvgReportFromListOfStatistics(statsList);
    }

    private TaxiStatisticsAvgReportDto createAvgReportFromListOfStatistics(List<TaxiStatisticsDto> statsList) {
        return new TaxiStatisticsAvgReportDto(
                statsList.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(s -> s.getStatsValues().getKmsTraveled())),
                statsList.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(TaxiStatisticsDto::getBatteryLevel)),
                statsList.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(s -> s.getStatsValues().getPollutionAvgList()
                                .stream().mapToDouble(m -> m).average().orElse(0.0))),
                statsList.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(s -> s.getStatsValues().getNumRides()))
        );
    }

    @Override
    public void loadTaxiStatistics(int id, TaxiStatisticsDto taxiStatistics) throws IdNotFoundException {
        checkTaxiIdExists(id);

        TaxiInfo taxiInfo = this.taxiInfos.get(id);
        taxiInfo.addTaxiStatistics(taxiStatistics);
    }

    // the whole method must be synchronized because if I add/remove a taxi while this method is
    // executed by another thread, I could get some inconsistency (for example the id could be
    // overwritten instead of throwing an exception in case of insert or the taxiInfo list is
    // incomplete)
    @Override
    public NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo) throws IdAlreadyRegisteredException {
        List<TaxiInfoDto> taxiInfoDtos;
        synchronized (this) {
            taxiInfoDtos = this.getAllTaxis();
            if (taxiInfoDtos.stream().anyMatch(tid -> tid.getId() == taxiInfo.getId()))
                throw new IdAlreadyRegisteredException(taxiInfo.getId());
            this.taxiInfos.put(taxiInfo.getId(),
                    new TaxiInfo(taxiInfo.getIpAddress(), taxiInfo.getPort()));
        }
        SmartCityPosition newTaxiPosition = this.taxiPositionGenerator.getStartingPosition();

        return new NewTaxiDto(newTaxiPosition.x, newTaxiPosition.y, taxiInfoDtos);
    }

    @Override
    public synchronized void removeTaxi(int id) throws IdNotFoundException {
        checkTaxiIdExists(id);

        this.taxiInfos.remove(id);
    }

    private void checkTaxiIdExists(int id) throws IdNotFoundException {
        if (!taxiInfos.containsKey(id))
            throw new IdNotFoundException(id);
    }

    private static class TaxiInfo {
        private final String ipAddress;
        private final int port;
        private final Deque<TaxiStatisticsDto> taxiStatisticsList = new ArrayDeque<>();

        public TaxiInfo(String ipAddress, int port) {
            this.ipAddress = ipAddress;
            this.port = port;
        }

        public String getIpAddress() {
            return ipAddress;
        }
        public int getPort() {
            return port;
        }
        public List<TaxiStatisticsDto> getTaxiStatisticsList() {
            return new ArrayList<>(taxiStatisticsList);
        }

        public void addTaxiStatistics(TaxiStatisticsDto taxiStatistics) {
            this.taxiStatisticsList.add(taxiStatistics);
        }
    }
}
