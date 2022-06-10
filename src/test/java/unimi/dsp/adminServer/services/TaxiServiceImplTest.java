package unimi.dsp.adminServer.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.services.impl.TaxiServiceImpl;
import unimi.dsp.adminServer.util.TaxiPositionGenerator;
import unimi.dsp.dto.NewTaxiDto;
import unimi.dsp.dto.TaxiInfoDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaxiServiceImplTest {
    private TaxiPositionGenerator taxiPositionGenerator = mock(TaxiPositionGenerator.class);
    private TaxiService service = new TaxiServiceImpl(taxiPositionGenerator);

    @Test
    public void Given0RegisteredTaxi_WhenGetAllTaxis_ThenReturn0Taxis() {
        List<TaxiInfoDto> taxiInfos = service.getAllTaxis();

        assertEquals(0, taxiInfos.size());
    }

    @Test
    public void GivenManyRegisteredTaxi_WhenGetAllTaxis_ThenReturnManyTaxis()
            throws IdAlreadyRegisteredException {
        service.registerTaxi(new TaxiInfoDto(1, "ip1", 1111));
        service.registerTaxi(new TaxiInfoDto(2, "ip2", 2222));

        List<TaxiInfoDto> taxiInfos = service.getAllTaxis();

        assertEquals(2, taxiInfos.size());
    }

    @Test
    public void GivenANewTaxi_WhenItIsTheFirstToBeRegistered_ThenThereIsNoTaxiInfo()
            throws IdAlreadyRegisteredException {
        when(taxiPositionGenerator.getXCoordinate()).thenReturn(0);
        when(taxiPositionGenerator.getYCoordinate()).thenReturn(9);
        TaxiInfoDto taxiInfo = new TaxiInfoDto(1, "ip1", 1111);

        NewTaxiDto newTaxiDto = service.registerTaxi(taxiInfo);

        assertEquals(0, newTaxiDto.getX());
        assertEquals(9, newTaxiDto.getY());
        assertEquals(0, newTaxiDto.getTaxiInfos().size());
    }

    @Test
    public void GivenANewTaxi_WhenManyTaxisAreRegistered_ThenThereAreManyTaxiInfo()
            throws IdAlreadyRegisteredException {
        TaxiInfoDto taxiInfo1 = new TaxiInfoDto(1, "ip1", 1111);
        TaxiInfoDto taxiInfo2 = new TaxiInfoDto(2, "ip2", 2222);
        TaxiInfoDto taxiInfo3 = new TaxiInfoDto(3, "ip3", 3333);
        service.registerTaxi(taxiInfo1);
        service.registerTaxi(taxiInfo2);

        NewTaxiDto newTaxiDto = service.registerTaxi(taxiInfo3);

        assertEquals(2, newTaxiDto.getTaxiInfos().size());
    }

    @Test
    public void GivenANewTaxi_WhenAnotherTaxiHasTheSameId_ThenThrow()
            throws IdAlreadyRegisteredException {
        TaxiInfoDto taxiInfo1 = new TaxiInfoDto(1, "ip1", 1111);
        TaxiInfoDto taxiInfo2 = new TaxiInfoDto(1, "ip2", 2222);
        service.registerTaxi(taxiInfo1);

        assertThrows(IdAlreadyRegisteredException.class,
                () -> service.registerTaxi(taxiInfo2));

        assertEquals(1, service.getAllTaxis().size());
    }

    @Test
    public void GivenARegisteredTaxi_WhenItIsRemoved_ThenItDoesNotAppearAnymore()
            throws IdAlreadyRegisteredException, IdNotFoundException {
        TaxiInfoDto taxiInfo1 = new TaxiInfoDto(1, "ip1", 1111);
        service.registerTaxi(taxiInfo1);

        service.removeTaxi(taxiInfo1.getId());

        assertEquals(0, service.getAllTaxis().size());
    }

    @Test
    public void GivenANonExistingId_WhenTaxiIsRemoved_ThenThrow()
            throws IdAlreadyRegisteredException, IdNotFoundException {
        TaxiInfoDto taxiInfo1 = new TaxiInfoDto(1, "ip1", 1111);
        service.registerTaxi(taxiInfo1);

        assertThrows(IdNotFoundException.class,
                () -> service.removeTaxi(2));
    }
}
