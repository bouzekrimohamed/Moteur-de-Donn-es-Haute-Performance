package com.example.engine.api;

import com.example.engine.core.TableManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/tables")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TableResource {

    @Inject
    TableManager tableManager;

    /** Créer une table */
    @POST
    public Response create(CreateTableRequest req) {
        try {
            TableManager.TableSchema schema = tableManager.createTable(req.tableName, req.columns);
            return Response.status(Response.Status.CREATED).entity(schema).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(ex.getMessage())).build();
        }
    }

    /** Lister toutes les tables */
    @GET
    public List<TableManager.TableSchema> listTables() {
        return tableManager.listTables();
    }

    /** Voir le schéma d'une table */
    @GET
    @Path("/{tableName}")
    public Response getTable(@PathParam("tableName") String tableName) {
        try {
            return Response.ok(tableManager.getTable(tableName)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(ex.getMessage())).build();
        }
    }

    /** Supprimer une table */
    @DELETE
    @Path("/{tableName}")
    public Response dropTable(@PathParam("tableName") String tableName) {
        try {
            tableManager.dropTable(tableName);
            return Response.ok(Map.of("status", "OK", "message", "Table supprimée : " + tableName)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(ex.getMessage())).build();
        }
    }

    /** Charger des lignes en mémoire */
    @POST
    @Path("/{tableName}/load")
    public Response load(@PathParam("tableName") String tableName, LoadDataRequest request) {
        try {
            int count = tableManager.loadRows(tableName, request.rows);
            return Response.status(Response.Status.ACCEPTED)
                    .entity(new LoadDataResponse("ACCEPTED", count + " lignes chargées.", count))
                    .build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(ex.getMessage())).build();
        }
    }

    /** Lire toutes les lignes brutes */
    @GET
    @Path("/{tableName}/rows")
    public Response getRows(@PathParam("tableName") String tableName) {
        try {
            return Response.ok(tableManager.getRows(tableName)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(ex.getMessage())).build();
        }
    }

    // --- DTOs ---

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
        public LoadDataResponse(String s, String m, int r) { status=s; message=m; acceptedRows=r; }
    }

    public static class ErrorResponse {
        public String error;
        public ErrorResponse(String e) { error = e; }
    }
}
