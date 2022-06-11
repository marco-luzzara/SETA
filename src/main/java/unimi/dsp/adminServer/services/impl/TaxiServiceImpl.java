package unimi.dsp.adminServer.services.impl;

import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.exceptions.ReportTypeNotFoundException;
import unimi.dsp.adminServer.util.TaxiPositionGenerator;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsAvgReportDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.model.types.TaxiStatisticsReportType;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.*;

public class TaxiServiceImpl implements TaxiService {
    private TaxiPositionGenerator taxiPositionGenerator;
    private HashMap<Integer, TaxiInfo> taxiInfos = new HashMap<>();

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
        List<TaxiStatisticsDto> nStats = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        reverseIt,
                        Spliterator.ORDERED),
                 false)
            .limit(n)
            .collect(Collectors.toList());

        return new TaxiStatisticsAvgReportDto(
                nStats.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(s -> s.getStats().getKmsTraveled())),
                nStats.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(TaxiStatisticsDto::getBatteryLevel)),
                nStats.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(s -> s.getStats().getPollutionAvgList()
                                .stream().mapToDouble(m -> m).average().orElse(0.0))),
                nStats.stream().collect(Collectors
                        .<TaxiStatisticsDto>averagingDouble(s -> s.getStats().getNumRides()))
        );
    }

    @Override
    public Object getTaxisStatisticsReport(OffsetDateTime tsStart,
                                           OffsetDateTime tsEnd,
                                           TaxiStatisticsReportType type) {
        return null;
    }

    @Override
    public void loadTaxiStatistics(int id, TaxiStatisticsDto taxiStatistics) throws IdNotFoundException {
        checkTaxiIdExists(id);

        TaxiInfo taxiInfo = this.taxiInfos.get(id);
        taxiInfo.addTaxiStatistics(taxiStatistics);
    }

    // the whole method must be synchronized because if I add/remove a taxi while this method is
    // executed by another thread, i could get some inconsistency (for example the id could be
    // overwritten instead of throwing an exception in case of insert)
    @Override
    public synchronized NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo) throws IdAlreadyRegisteredException {
        List<TaxiInfoDto> taxiInfoDtos = this.getAllTaxis();
        if (taxiInfoDtos.stream().anyMatch(tid -> tid.getId() == taxiInfo.getId()))
            throw new IdAlreadyRegisteredException(taxiInfo.getId());
        this.taxiInfos.put(taxiInfo.getId(),
                new TaxiInfo(taxiInfo.getIpAddress(), taxiInfo.getPort()));

        return new NewTaxiDto(this.taxiPositionGenerator.getXCoordinate(),
                this.taxiPositionGenerator.getYCoordinate(),
                taxiInfoDtos);
    }

    @Override
    public void removeTaxi(int id) throws IdNotFoundException {
        checkTaxiIdExists(id);

        this.taxiInfos.remove(id);
    }

    private void checkTaxiIdExists(int id) throws IdNotFoundException {
        if (!taxiInfos.containsKey(id))
            throw new IdNotFoundException(id);
    }

    private class TaxiInfo {
        private String ipAddress;
        private int port;
        private Deque<TaxiStatisticsDto> taxiStatisticsList = new ArrayDeque<>();

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
