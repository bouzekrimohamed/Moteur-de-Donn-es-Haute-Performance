package com.example.engine.api;

import com.example.engine.core.TableManager;
import com.example.engine.loader.CsvLoader;
import com.example.engine.loader.ParquetLoader;
import com.example.engine.storage.Table;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.util.Map;

/**
 * Endpoints pour charger des fichiers CSV ou Parquet dans une table existante.
 *
 * POST /tables/{tableName}/load/csv     → charger un CSV
 * POST /tables/{tableName}/load/parquet → charger un Parquet
 * POST /tables/import/parquet           → créer + charger une table depuis un Parquet
 */
@Path("/tables")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FileLoadResource {

    @Inject
    TableManager tableManager;

    // -----------------------------------------------------------------------
    // Charger un CSV dans une table existante
    // -----------------------------------------------------------------------

    /**
     * Corps JSON :
     * { "filePath": "/chemin/absolu/vers/fichier.csv" }
     */
    @POST
    @Path("/{tableName}/load/csv")
    public Response loadCsv(
            @PathParam("tableName") String tableName,
            FilePathRequest req) {
        try {
            validateFile(req.filePath, ".csv");
            Table table = tableManager.getInternalTable(tableName);
            int count = CsvLoader.load(table, req.filePath);
            return Response.accepted(new FileLoadResponse("ACCEPTED", count, tableName, req.filePath)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new TableResource.ErrorResponse(ex.getMessage())).build();
        } catch (Exception ex) {
            return Response.serverError()
                    .entity(new TableResource.ErrorResponse("Erreur lors du chargement CSV : " + ex.getMessage()))
                    .build();
        }
    }

    // -----------------------------------------------------------------------
    // Charger un Parquet dans une table existante
    // -----------------------------------------------------------------------

    /**
     * Corps JSON :
     * { "filePath": "/chemin/absolu/vers/fichier.parquet" }
     */
    @POST
    @Path("/{tableName}/load/parquet")
    public Response loadParquet(
            @PathParam("tableName") String tableName,
            FilePathRequest req) {
        try {
            validateFile(req.filePath, ".parquet");
            Table table = tableManager.getInternalTable(tableName);
            int count = ParquetLoader.load(table, req.filePath);
            return Response.accepted(new FileLoadResponse("ACCEPTED", count, tableName, req.filePath)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new TableResource.ErrorResponse(ex.getMessage())).build();
        } catch (Exception ex) {
            return Response.serverError()
                    .entity(new TableResource.ErrorResponse("Erreur lors du chargement Parquet : " + ex.getMessage()))
                    .build();
        }
    }

    // -----------------------------------------------------------------------
    // Importer un Parquet : crée la table automatiquement depuis le schéma
    // -----------------------------------------------------------------------

    /**
     * Crée une nouvelle table dont le schéma est déduit du fichier Parquet,
     * puis charge toutes les données.
     *
     * Corps JSON :
     * { "filePath": "/chemin/absolu/vers/fichier.parquet" }
     */
    @POST
    @Path("/import/parquet")
    public Response importParquet(FilePathRequest req) {
        try {
            validateFile(req.filePath, ".parquet");
            Table table = ParquetLoader.loadAsNewTable(req.filePath);
            tableManager.registerTable(table);
            long count = table.totalRowCount();
            return Response.status(Response.Status.CREATED)
                    .entity(new FileLoadResponse("CREATED", count, table.getName(), req.filePath))
                    .build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new TableResource.ErrorResponse(ex.getMessage())).build();
        } catch (Exception ex) {
            return Response.serverError()
                    .entity(new TableResource.ErrorResponse("Erreur import Parquet : " + ex.getMessage()))
                    .build();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void validateFile(String filePath, String expectedExt) {
        if (filePath == null || filePath.isBlank())
            throw new IllegalArgumentException("filePath est requis");
        File f = new File(filePath);
        if (!f.exists())
            throw new IllegalArgumentException("Fichier introuvable : " + filePath);
        if (!filePath.toLowerCase().endsWith(expectedExt))
            throw new IllegalArgumentException(
                    "Extension attendue : " + expectedExt + " — reçu : " + filePath);
    }

    // -----------------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------------

    public static class FilePathRequest {
        public String filePath;
    }

    public static class FileLoadResponse {
        public String status;
        public long rowsLoaded;
        public String tableName;
        public String sourceFile;

        public FileLoadResponse(String status, long rowsLoaded, String tableName, String sourceFile) {
            this.status = status;
            this.rowsLoaded = rowsLoaded;
            this.tableName = tableName;
            this.sourceFile = sourceFile;
        }
    }
}
