package unimi.dsp.adminServer.services.impl;

import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.util.TaxiPositionGenerator;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.adminServer.services.TaxiService;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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
        if (!taxiInfos.containsKey(id))
            throw new IdNotFoundException(id);

        this.taxiInfos.remove(id);
    }

    private class TaxiInfo {
        private String ipAddress;
        private int port;

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
    }
}
