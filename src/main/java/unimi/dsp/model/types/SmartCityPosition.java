package unimi.dsp.model.types;

import unimi.dsp.util.ConfigurationManager;

import java.util.Objects;

public class SmartCityPosition {
    private static ConfigurationManager configurationManager;
    private static int maxWidth;
    private static int maxHeight;

    static {
        configurationManager = ConfigurationManager.getInstance();
        maxWidth = configurationManager.getSmartCityWidth();
        maxHeight = configurationManager.getSmartCityHeight();
    }

    public final int x;
    public final int y;

    public SmartCityPosition(int x, int y) {
        if (x < 0 || x >= maxWidth || y < 0 || y >= maxHeight)
            throw new IllegalArgumentException(
                    String.format("Validation rule: 0 <= x < %d && 0 <= y < %d, x = %d, y = %d",
                            maxWidth, maxHeight, x, y));
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmartCityPosition that = (SmartCityPosition) o;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "SmartCityPosition{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
