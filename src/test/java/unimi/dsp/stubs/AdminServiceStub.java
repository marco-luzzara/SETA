package unimi.dsp.stubs;

import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.services.TaxiPositionGenerator;
import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.adminServer.services.impl.TaxiServiceImpl;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.model.types.SmartCityPosition;
import unimi.dsp.taxi.AdminServiceBase;

import java.util.ArrayList;
import java.util.HashMap;

public class AdminServiceStub implements AdminServiceBase {
    private final TaxiService taxiService;

    public AdminServiceStub(TaxiPositionGenerator startPositionGenerator) {
        this.taxiService = new TaxiServiceImpl(startPositionGenerator);
    }

    @Override
    public NewTaxiDto registerTaxi(TaxiInfoDto taxiInfo) {
        try {
            return this.taxiService.registerTaxi(taxiInfo);
        } catch (IdAlreadyRegisteredException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void unregisterTaxi(int taxiId) {
        try {
            this.taxiService.removeTaxi(taxiId);
        } catch (IdNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
