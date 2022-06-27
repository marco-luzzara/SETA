package unimi.dsp.model;

import java.util.Objects;

public class RechargeRequest {
    private final long ts;
    private final int taxiId;

    public RechargeRequest(long ts, int taxiId) {
        this.ts = ts;
        this.taxiId = taxiId;
    }

    public int getTaxiId() {
        return taxiId;
    }

    public long getTs() {
        return ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RechargeRequest that = (RechargeRequest) o;
        return taxiId == that.taxiId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(taxiId);
    }
}
