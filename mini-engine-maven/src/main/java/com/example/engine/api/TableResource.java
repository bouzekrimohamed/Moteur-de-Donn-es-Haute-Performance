package com.example.engine.api;

import com.example.engine.core.TableManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/tables")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TableResource {

    @Inject
    TableManager tableManager;

    @POST
    public Response create(CreateTableRequest req) {
        try {
            TableManager.TableSchema table = tableManager.createTable(req.tableName, req.columns);
            return Response.status(Response.Status.CREATED).entity(table).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/load-parquet")
    public Response loadParquet(ParquetLoadRequest request) {
        try {
            TableManager.TableSchema table = tableManager.loadParquetFile(request.filePath);
            return Response.status(Response.Status.CREATED).entity(table).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    @GET
    public List<TableManager.TableSchema> listTables() {
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
            int accepted = tableManager.loadRows(tableName, request.rows);
            LoadDataResponse response = new LoadDataResponse("ACCEPTED", "Rows loaded in memory successfully.", accepted);
            return Response.status(Response.Status.ACCEPTED).entity(response).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{tableName}/rows")
    public Response getRows(@PathParam("tableName") String tableName) {
        try {
            List<Map<String, Object>> rows = tableManager.getRows(tableName);
            return Response.ok(rows).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{tableName}/rows/preview")
    public Response getRowsPreview(@PathParam("tableName") String tableName, @QueryParam("limit") @DefaultValue("5") int limit) {
        try {
            List<Map<String, Object>> rows = tableManager.getRowsPreview(tableName, limit);
            return Response.ok(rows).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
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

    public static class CreateTableRequest {
        public String tableName;
        public List<TableManager.ColumnDef> columns;
    }

    public static class LoadDataRequest {
        public List<Map<String, Object>> rows;
    }

    public static class LoadDataResponse {
        public String status;
        public String message;
        public int acceptedRows;

        public LoadDataResponse(String status, String message, int acceptedRows) {
            this.status = status;
            this.message = message;
            this.acceptedRows = acceptedRows;
        }
    }

    public static class ParquetLoadRequest {
        public String filePath;
    }
}
