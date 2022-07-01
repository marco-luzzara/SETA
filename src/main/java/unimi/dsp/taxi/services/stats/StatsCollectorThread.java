package unimi.dsp.taxi.services.stats;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.dto.types.SerializableOffsetDateTime;
import unimi.dsp.sensors.Buffer;
import unimi.dsp.sensors.Measurement;
import unimi.dsp.taxi.AdminServiceBase;
import unimi.dsp.taxi.Taxi;
import unimi.dsp.util.DateTimeUtil;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class StatsCollectorThread extends Thread {
    private static final Logger logger = LogManager.getLogger(StatsCollectorThread.class.getName());

    private final Buffer buffer;
    private final int statsLoadingDelay;
    private final Thread pollutionDataAggregator;
    private final List<Double> pollutionAverages = new ArrayList<>();
    private final Taxi taxi;
    private final AdminServiceBase adminService;

    public StatsCollectorThread(Buffer buffer,
                                int statsLoadingDelay,
                                Taxi taxi,
                                AdminServiceBase adminService) {
        this.buffer = buffer;
        this.statsLoadingDelay = statsLoadingDelay;
        this.taxi = taxi;
        this.adminService = adminService;
        this.pollutionDataAggregator = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                double measurementsAvg = this.buffer.readAllAndClean()
                        .stream().mapToDouble(Measurement::getValue).average()
                        .orElse(0);

                synchronized (this.pollutionAverages) {
                    this.pollutionAverages.add(measurementsAvg);
                }
            }
        });
    }

    @Override
    public void run() {
        this.pollutionDataAggregator.start();

        try {
            while (!this.isInterrupted()) {
                Thread.sleep(statsLoadingDelay);
                this.sendStatisticsToAdminServer();
            }
        } catch (InterruptedException e) {
            // if the thread has been stopped while sleeping, then collect the last data
            // and sends them to server
            this.sendStatisticsToAdminServer();
        }
    }

    private void sendStatisticsToAdminServer() {
        logger.info("sending statistics to the server for taxi {}", this.taxi.getId());
        List<Double> pollutionAvgsToSend;
        synchronized (this.pollutionAverages) {
            pollutionAvgsToSend = new ArrayList<>(this.pollutionAverages);
            this.pollutionAverages.clear();
        }

        TaxiStatisticsDto taxiStatistics = new TaxiStatisticsDto(
                new SerializableOffsetDateTime(
                        DateTimeUtil.getStringFromOffsetDateTime(OffsetDateTime.now(ZoneOffset.UTC))),
                this.taxi.getBatteryLevel(),
                new TaxiStatisticsDto.TaxiStatisticsValues(this.taxi.getKmsTraveled(), this.taxi.getTakenRides().size(),
                        pollutionAvgsToSend));
        this.taxi.clearStatistics();
        this.adminService.loadTaxiStatistics(this.taxi.getId(), taxiStatistics);
    }

    @Override
    public void interrupt() {
        super.interrupt();
        try {
            this.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        pollutionDataAggregator.interrupt();
    }
}
