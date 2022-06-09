package unimi.dsp.adminServer.api;

import com.sun.jersey.api.NotFoundException;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.adminServer.factories.TaxiServiceFactory;
import unimi.dsp.adminServer.services.TaxiService;
import java.time.OffsetDateTime;
import javax.ws.rs.core.Response;
import javax.ws.rs.*;

@Path("/taxis")
public class TaxiResource {
    private final TaxiService service = TaxiServiceFactory.getTaxiService();

    @GET
    @Produces({ "application/json" })
    public Response getAllTaxis() throws NotFoundException {
        return Response.ok().build(); //service .getAllTaxis();
    }

    @GET
    @Path("/{id}/statistics/report")
    @Produces({ "application/json" })
    public Response getTaxiStatisticsReport(
            @PathParam("id") Integer id,
            @QueryParam("n") Integer n,
            @QueryParam("type") @DefaultValue("average") String type)
            throws NotFoundException {
        return Response.ok().build(); //service.getTaxiStatisticsReport(id,n,type);
    }

    @GET
    @Path("/statistics/report")
    @Produces({ "application/json" })
    public Response getTaxisStatisticsReport(
            @QueryParam("tsStart") OffsetDateTime tsStart,
            @QueryParam("tsEnd") OffsetDateTime tsEnd,
            @QueryParam("type") @DefaultValue("average") String type)
            throws NotFoundException {
        return Response.ok().build(); //service.getTaxisStatisticsReport(tsStart,tsEnd,type);
    }

    @POST
    @Path("/{id}/statistics")
    @Consumes({ "application/json" })
    public Response loadTaxiStatistics(
            @PathParam("id") Integer id,
            TaxiStatisticsDto taxiStatistics)
            throws NotFoundException {
        return Response.ok().build(); // service.loadTaxiStatistics(id,taxiStatistics);
    }

    @POST
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    public Response registerTaxi(
            TaxiInfoDto taxiInfoDto)
            throws NotFoundException {
        return Response.ok().build(); // service.registerTaxi(taxiInfo);
    }

    @DELETE
    @Path("/{id}")
    public Response removeTaxi(
            @PathParam("id") Integer id)
            throws NotFoundException {
        return Response.ok().build(); // service.removeTaxi(id);
    }
}
