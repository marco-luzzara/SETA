package unimi.dsp.model.types;

import unimi.dsp.util.ConfigurationManager;

public enum District {
    TOP_LEFT(1, new SmartCityPosition(0, 0)),
    TOP_RIGHT(2, new SmartCityPosition(9, 0)),
    BOTTOM_LEFT(3, new SmartCityPosition(0, 9)),
    BOTTOM_RIGHT(4, new SmartCityPosition(9, 9));

    private final int districtValue;
    private final SmartCityPosition rechargeStationPosition;
    District(int districtValue, SmartCityPosition rechargeStationPosition) {
        this.districtValue = districtValue;
        this.rechargeStationPosition = rechargeStationPosition;
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
                return District.BOTTOM_LEFT;
        }
        else {
            if (position.y < smartCityMaxHeight / 2)
                return District.TOP_RIGHT;
            else
                return District.BOTTOM_RIGHT;
        }
    }

    public SmartCityPosition getRechargeStationPosition() {
        return rechargeStationPosition;
    }
}
