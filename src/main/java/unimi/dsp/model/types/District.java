package unimi.dsp.model.types;

public enum District {
    TOP_LEFT(1),
    TOP_RIGHT(2),
    BOTTOM_LEFT(3),
    BOTTOM_RIGHT(4);

    private int districtValue;
    District(int districtValue) {
        this.districtValue = districtValue;
    }

    @Override
    public String toString() {
        return Integer.toString(this.districtValue);
    }
}
