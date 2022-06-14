package unimi.dsp.adminServer.services;

import unimi.dsp.model.types.SmartCityPosition;

public interface TaxiPositionGenerator {
    SmartCityPosition getStartingPosition();
}
