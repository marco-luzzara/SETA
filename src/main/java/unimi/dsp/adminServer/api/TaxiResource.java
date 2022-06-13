package unimi.dsp.adminServer.api;

import unimi.dsp.dto.types.SerializableOffsetDateTime;
import unimi.dsp.adminServer.exceptions.IdAlreadyRegisteredException;
import unimi.dsp.adminServer.exceptions.IdNotFoundException;
import unimi.dsp.adminServer.exceptions.ReportTypeNotFoundException;
import unimi.dsp.dto.TaxiInfoDto;
import unimi.dsp.dto.TaxiStatisticsDto;
import unimi.dsp.adminServer.factories.TaxiServiceFactory;
import unimi.dsp.adminServer.services.TaxiService;
import unimi.dsp.model.types.TaxiStatisticsReportType;

import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.*;
import java.util.List;

@Path("/taxis")
public class TaxiResource {
    private final TaxiService service = TaxiServiceFactory.getTaxiService();

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response getAllTaxis() {
        return Response.ok(new GenericEntity<List<TaxiInfoDto>>(service.getAllTaxis()) {}).build();
    }

    @GET
    @Path("/{id}/statistics/report")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response getTaxiStatisticsReport(
            @PathParam("id") Integer id,
            @QueryParam("n") Integer n,
            @QueryParam("type") @DefaultValue("AVERAGE") String type) {
        if (id == null || n == null)
            Response.status(Response.Status.BAD_REQUEST)
                    .entity("id and n cannot be null").build();

        try {
            return Response.ok(service.getTaxiStatisticsReport(id, n, TaxiStatisticsReportType.valueOf(type)))
                    .build();
        } catch (IdNotFoundException e) {
            return buildNotFoundResponse(e);
        } catch (ReportTypeNotFoundException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/statistics/report")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response getTaxisStatisticsReport(
            @QueryParam("tsStart") SerializableOffsetDateTime tsStart,
            @QueryParam("tsEnd") SerializableOffsetDateTime tsEnd,
            @QueryParam("type") @DefaultValue("AVERAGE") String type) {
        if (tsStart == null || tsEnd == null)
            Response.status(Response.Status.BAD_REQUEST)
                    .entity("tsStart and tsEnd cannot be null").build();

        try {
            return Response.ok(service.getTaxisStatisticsReport(tsStart.getOdt(), tsEnd.getOdt(),
                            TaxiStatisticsReportType.valueOf(type))).build();
        } catch (ReportTypeNotFoundException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{id}/statistics")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response loadTaxiStatistics(
            @PathParam("id") Integer id,
            TaxiStatisticsDto taxiStatistics) {
        if (id == null || taxiStatistics == null)
            Response.status(Response.Status.BAD_REQUEST)
                    .entity("id and statistics object cannot be null").build();

        try {
            service.loadTaxiStatistics(id, taxiStatistics);
            return Response.ok().build();
        } catch (IdNotFoundException e) {
            return buildNotFoundResponse(e);
        }
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public Response registerTaxi(
            TaxiInfoDto taxiInfoDto) {
        if (taxiInfoDto == null)
            Response.status(Response.Status.BAD_REQUEST)
                    .entity("taxi info object cannot be null").build();

        try {
            return Response.status(Response.Status.CREATED)
                    .entity(service.registerTaxi(taxiInfoDto)).build();
        } catch (IdAlreadyRegisteredException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response removeTaxi(
            @PathParam("id") Integer id) {
        if (id == null)
            Response.status(Response.Status.BAD_REQUEST)
                    .entity("id cannot be null").build();

        try {
            service.removeTaxi(id);
            return Response.ok().build();
        } catch (IdNotFoundException e) {
            return buildNotFoundResponse(e);
        }
    }

    private Response buildNotFoundResponse(Exception e) {
        return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
    }
}
