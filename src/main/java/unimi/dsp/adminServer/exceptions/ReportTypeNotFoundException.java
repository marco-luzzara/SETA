package unimi.dsp.adminServer.exceptions;

import unimi.dsp.model.types.TaxiStatisticsReportType;

public class ReportTypeNotFoundException extends Exception {
    private final TaxiStatisticsReportType reportType;

    public ReportTypeNotFoundException(TaxiStatisticsReportType reportType) {
        this.reportType = reportType;
    }

    @Override
    public String getMessage() {
        return String.format("Report with type %s cannot be retrieved", this.reportType.toString());
    }
}
