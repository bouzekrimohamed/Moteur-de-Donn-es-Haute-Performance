package com.example.engine.api;

import com.example.engine.core.QueryEngine;
import com.example.engine.core.TableManager;
import com.example.engine.loader.ParquetLoader;
import com.example.engine.storage.Table;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.*;

/**
 * Benchmark sur les vraies données Yellow Taxi NYC (fichiers Parquet).
 *
 * Fichiers attendus dans benchmark/taxi/ :
 *   yellow_tripdata_2025-02.parquet  (~3.5M lignes)
 *   yellow_tripdata_2026-01.parquet  (~3.7M lignes)
 *
 * Endpoints :
 *   GET /benchmark/taxi/run            → un fichier (~3.5M lignes)
 *   GET /benchmark/taxi/run?les2=true  → les deux fichiers fusionnés (~7.3M lignes)
 *
 * Schéma Parquet utilisé :
 *   VendorID (INT), passenger_count (LONG), trip_distance (DOUBLE),
 *   payment_type (LONG), fare_amount (DOUBLE), tip_amount (DOUBLE),
 *   total_amount (DOUBLE), tpep_pickup_datetime (LONG = µs epoch), ...
 *
 * Correspondance payment_type :
 *   1 = Carte bancaire  2 = Espèces  3 = Pas de frais
 *   4 = Litige          5 = Inconnu  6 = Annulé
 */
@Path("/benchmark/taxi")
@Produces(MediaType.APPLICATION_JSON)
public class BenchmarkTaxiResource {

    private static final String DOSSIER_TAXI =
        "C:/Users/hoang/IdeaProjects/Moteur-de-Donn-es-Haute-Performance/benchmark/taxi/";

    private static final String FICHIER_1 = DOSSIER_TAXI + "yellow_tripdata_2025-02.parquet";
    private static final String FICHIER_2 = DOSSIER_TAXI + "yellow_tripdata_2026-01.parquet";

    // Noms de colonnes du dataset Taxi NYC
    private static final String COL_PAYMENT    = "payment_type";
    private static final String COL_TIP        = "tip_amount";
    private static final String COL_TOTAL      = "total_amount";
    private static final String COL_DISTANCE   = "trip_distance";
    private static final String COL_PASSAGERS  = "passenger_count";
    private static final String COL_FARE       = "fare_amount";
    private static final String COL_VENDOR     = "VendorID";

    // -------------------------------------------------------------------------
    // Endpoint principal
    // -------------------------------------------------------------------------

    @GET
    @Path("/run")
    public Response run(@QueryParam("les2") @DefaultValue("false") boolean les2) {
        try {
            long tLoad = System.currentTimeMillis();
            TableManager tm = new TableManager();
            Table table = ParquetLoader.loadAsNewTable(FICHIER_1);

            if (les2) {
                ParquetLoader.load(table, FICHIER_2);
            }

            tm.registerTable(table);
            long loadMs = System.currentTimeMillis() - tLoad;
            String tableName = table.getName();
            int nbLignes = table.rowCount();

            List<RequeteResult> requetes = new ArrayList<>();

            // Q1 — Revenus par mode de paiement
            requetes.add(mesurer(tm, tableName,
                "Revenus par mode de paiement",
                "GROUP BY payment_type + SUM(total_amount) — Quel mode de paiement génère le plus de revenus ?",
                null,
                List.of("SUM(" + COL_TOTAL + ")"),
                COL_PAYMENT, null, true, -1));

            // Q2 — Pourboires supérieurs à 10$
            requetes.add(mesurer(tm, tableName,
                "Trajets avec pourboire > 10$",
                "WHERE tip_amount > 10 — Quels trajets sont les plus rentables pour les chauffeurs ?",
                new QueryEngine.WhereClause(COL_TIP, ">", 10.0),
                null, null, null, false, 10));

            // Q3 — Tarif moyen par nombre de passagers
            requetes.add(mesurer(tm, tableName,
                "Tarif moyen par nombre de passagers",
                "GROUP BY passenger_count + AVG(fare_amount) — Le prix varie-t-il selon le nombre de passagers ?",
                null,
                List.of("AVG(" + COL_FARE + ")"),
                COL_PASSAGERS, null, true, -1));

            // Q4 — Longues courses (> 20 miles)
            requetes.add(mesurer(tm, tableName,
                "Longues courses (> 20 miles)",
                "WHERE trip_distance > 20 — Combien de trajets longue distance existent ?",
                new QueryEngine.WhereClause(COL_DISTANCE, ">", 20.0),
                null, null, null, false, 10));

            // Q5 — Top 10 courses les plus chères
            requetes.add(mesurer(tm, tableName,
                "Top 10 courses les plus chères",
                "SELECT * ORDER BY total_amount DESC LIMIT 10 — Quelles sont les courses les plus chères ?",
                null,
                List.of(COL_TOTAL, COL_DISTANCE, COL_TIP, COL_PASSAGERS, COL_PAYMENT),
                null, COL_TOTAL, false, 10));

            // Q6 — Courses gratuites ou litiges (payment_type = 3 ou 4)
            requetes.add(mesurer(tm, tableName,
                "Courses gratuites ou en litige",
                "WHERE payment_type = 3 — Combien de courses sans frais ou annulées ?",
                new QueryEngine.WhereClause(COL_PAYMENT, "=", 3L),
                null, null, null, true, 10));

            RapportTaxi rapport = new RapportTaxi(
                nbLignes, loadMs, les2 ? "2025-02 + 2026-01" : "2025-02", requetes
            );
            return Response.ok(rapport).build();

        } catch (IOException e) {
            return Response.status(500)
                .entity(Map.of("erreur", "Impossible de lire le fichier Parquet : " + e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(500)
                .entity(Map.of("erreur", e.getMessage()))
                .build();
        }
    }

    // -------------------------------------------------------------------------
    // Mesure d'une requête
    // -------------------------------------------------------------------------

    private RequeteResult mesurer(
            TableManager tm, String tableName,
            String titre, String description,
            QueryEngine.WhereClause where,
            List<String> select,
            String groupBy, String orderBy,
            boolean orderAsc, int limit) {

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> rows = tm.query(
            tableName, select, where, groupBy, orderBy, orderAsc, limit
        );
        long dureeMs = System.currentTimeMillis() - t0;

        // On retourne au plus 5 lignes en aperçu pour ne pas surcharger la réponse
        List<Map<String, Object>> apercu = rows.size() > 5 ? rows.subList(0, 5) : rows;

        return new RequeteResult(titre, description, dureeMs, rows.size(), apercu);
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    public static class RapportTaxi {
        public int    nbLignes;
        public long   loadMs;
        public long   loadLignesParSeconde;
        public String source;
        public List<RequeteResult> requetes;

        public RapportTaxi(int nbLignes, long loadMs, String source, List<RequeteResult> requetes) {
            this.nbLignes             = nbLignes;
            this.loadMs               = loadMs;
            this.loadLignesParSeconde = loadMs > 0 ? (nbLignes * 1000L) / loadMs : 0;
            this.source               = source;
            this.requetes             = requetes;
        }
    }

    public static class RequeteResult {
        public String titre;
        public String description;
        public long   dureeMs;
        public int    nbResultats;
        public List<Map<String, Object>> apercu;

        public RequeteResult(String titre, String description, long dureeMs,
                             int nbResultats, List<Map<String, Object>> apercu) {
            this.titre       = titre;
            this.description = description;
            this.dureeMs     = dureeMs;
            this.nbResultats = nbResultats;
            this.apercu      = apercu;
        }
    }
}
