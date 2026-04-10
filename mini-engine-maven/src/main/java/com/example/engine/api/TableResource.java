package com.example.engine.api;

import com.example.engine.core.TableManager;
import com.example.engine.model.CreateTableRequest;
import com.example.engine.model.LoadDataRequest;
import com.example.engine.model.LoadDataResponse;
import com.example.engine.model.Table;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/tables")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TableResource {

    @Inject
    TableManager tableManager;

    @POST
    public Response create(CreateTableRequest req) {
        try {
            Table table = tableManager.createTable(req);
            return Response.status(Response.Status.CREATED).entity(table).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    @GET
    public List<Table> listTables() {
        return tableManager.listTables();
    }

    @GET
    @Path("/{tableName}")
    public Response getTable(@PathParam("tableName") String tableName) {
        try {
            return Response.ok(tableManager.getTable(tableName)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/{tableName}/load")
    public Response load(@PathParam("tableName") String tableName, LoadDataRequest request) {
        try {
            LoadDataResponse response = tableManager.loadData(tableName, request);
            return Response.status(Response.Status.ACCEPTED).entity(response).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    public static class ErrorResponse {
        public final String error;

        public ErrorResponse(String error) {
           this.error = error;
        }
    }
}


