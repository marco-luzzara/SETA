package unimi.dsp.model.types;

public class TaxiStats {
    private final double kmsTraveled;
    private final int takenRidesNumber;

    public TaxiStats(double kmsTraveled, int takenRidesNumber) {
        this.kmsTraveled = kmsTraveled;
        this.takenRidesNumber = takenRidesNumber;
    }

    public double getKmsTraveled() {
        return kmsTraveled;
    }

    public int getTakenRidesNumber() {
        return takenRidesNumber;
    }
}
