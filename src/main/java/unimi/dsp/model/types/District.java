package unimi.dsp.model.types;

import unimi.dsp.util.ConfigurationManager;

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

    private static final ConfigurationManager configurationManager = ConfigurationManager.getInstance();
    private static final int smartCityMaxWidth = configurationManager.getSmartCityWidth();
    private static final int smartCityMaxHeight = configurationManager.getSmartCityHeight();
    public static District fromPosition(SmartCityPosition position) {
        if (position.x < smartCityMaxWidth / 2) {
            if (position.y < smartCityMaxHeight / 2)
                return District.TOP_LEFT;
            else
                return District.TOP_RIGHT;
        }
        else {
            if (position.y < smartCityMaxHeight / 2)
                return District.BOTTOM_LEFT;
            else
                return District.BOTTOM_RIGHT;
        }
    }
}
