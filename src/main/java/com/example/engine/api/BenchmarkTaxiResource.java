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
 * Benchmark multi-niveaux sur les vraies données Yellow Taxi NYC (fichiers Parquet).
 *
 * Fichiers attendus dans benchmark/taxi/ :
 *   yellow_tripdata_2025-02.parquet  (~3.5M lignes)
 *   yellow_tripdata_2026-01.parquet  (~3.7M lignes)
 *
 * Endpoints :
 *   GET /benchmark/taxi/run            → un fichier (~3.5M lignes)
 *   GET /benchmark/taxi/run?les2=true  → les deux fichiers fusionnés (~7.3M lignes)
 *
 * Structure de la réponse :
 *   - requetes  : 6 requêtes business (Q1–Q6)
 *   - sections  : benchmarks détaillés par catégorie
 *     · WHERE      — sélectivité (4 filtres, de 70% à 1% des lignes)
 *     · GROUP BY   — cardinalité (2, 6, 9 groupes distincts)
 *     · AGRÉGATION — type de fonction (SUM, AVG, MIN, MAX sur même colonne)
 *     · TOP-N      — taille de N (10, 100, 1000)
 */
@Path("/benchmark/taxi")
@Produces(MediaType.APPLICATION_JSON)
public class BenchmarkTaxiResource {

    private static final String DOSSIER_TAXI =
        "C:/Users/hoang/IdeaProjects/Moteur-de-Donn-es-Haute-Performance/benchmark/taxi/";

    private static final String FICHIER_1 = DOSSIER_TAXI + "yellow_tripdata_2025-02.parquet";
    private static final String FICHIER_2 = DOSSIER_TAXI + "yellow_tripdata_2026-01.parquet";

    private static final String COL_PAYMENT   = "payment_type";
    private static final String COL_TIP       = "tip_amount";
    private static final String COL_TOTAL     = "total_amount";
    private static final String COL_DISTANCE  = "trip_distance";
    private static final String COL_PASSAGERS = "passenger_count";
    private static final String COL_FARE      = "fare_amount";
    private static final String COL_VENDOR    = "VendorID";

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
            if (les2) ParquetLoader.load(table, FICHIER_2);
            tm.registerTable(table);

            long   loadMs    = System.currentTimeMillis() - tLoad;
            String tableName = table.getName();
            int    nbLignes  = table.rowCount();

            // ── 6 requêtes business ──────────────────────────────────────────
            List<RequeteResult> requetes = new ArrayList<>();

            requetes.add(mesurer(tm, tableName,
                "Q1 – Revenus par mode de paiement",
                "GROUP BY payment_type + SUM(total_amount)",
                null, List.of("SUM(" + COL_TOTAL + ")"), COL_PAYMENT, null, true, -1));

            requetes.add(mesurerWhere(tm, tableName,
                "Q2 – Trajets avec pourboire > 10$",
                "WHERE tip_amount > 10",
                new QueryEngine.WhereClause(COL_TIP, ">", 10.0),
                List.of(COL_TIP, COL_TOTAL, COL_DISTANCE, COL_PAYMENT)));

            requetes.add(mesurer(tm, tableName,
                "Q3 – Tarif moyen par nombre de passagers",
                "GROUP BY passenger_count + AVG(fare_amount)",
                null, List.of("AVG(" + COL_FARE + ")"), COL_PASSAGERS, null, true, -1));

            requetes.add(mesurerWhere(tm, tableName,
                "Q4 – Longues courses (> 20 miles)",
                "WHERE trip_distance > 20",
                new QueryEngine.WhereClause(COL_DISTANCE, ">", 20.0),
                List.of(COL_DISTANCE, COL_TOTAL, COL_TIP, COL_PASSAGERS)));

            requetes.add(mesurer(tm, tableName,
                "Q5 – Top 10 courses les plus chères",
                "ORDER BY total_amount DESC LIMIT 10",
                null, List.of(COL_TOTAL, COL_DISTANCE, COL_TIP, COL_PASSAGERS, COL_PAYMENT),
                null, COL_TOTAL, false, 10));

            requetes.add(mesurerWhere(tm, tableName,
                "Q6 – Courses gratuites / litiges (payment_type = 3)",
                "WHERE payment_type = 3",
                new QueryEngine.WhereClause(COL_PAYMENT, "=", 3L), null));

            // ── Sections de benchmark détaillé ──────────────────────────────
            List<BenchmarkSection> sections = new ArrayList<>();
            sections.add(sectionWhere(tm, tableName));
            sections.add(sectionGroupByCardinalite(tm, tableName));
            sections.add(sectionAggregations(tm, tableName));
            sections.add(sectionTopN(tm, tableName));

            return Response.ok(new RapportTaxi(
                nbLignes, loadMs,
                les2 ? "2025-02 + 2026-01" : "2025-02",
                requetes, sections
            )).build();

        } catch (IOException e) {
            return Response.status(500)
                .entity(Map.of("erreur", "Impossible de lire le fichier Parquet : " + e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(500)
                .entity(Map.of("erreur", e.getClass().getSimpleName() + " : " + e.getMessage()))
                .build();
        }
    }

    // =========================================================================
    // Sections de benchmark
    // =========================================================================

    /**
     * Section 1 — WHERE : sélectivité
     * Montre que le temps de scan est quasi constant quelle que soit la sélectivité,
     * car le parallel scan parcourt toujours tout le dataset.
     */
    private BenchmarkSection sectionWhere(TableManager tm, String tableName) {
        List<RequeteResult> mesures = new ArrayList<>();

        mesures.add(mesurerWhere(tm, tableName,
            "payment_type = 1  (sélectivité haute ~70%)",
            "Filtre peu sélectif — retourne la majorité des lignes",
            new QueryEngine.WhereClause(COL_PAYMENT, "=", 1L), null));

        mesures.add(mesurerWhere(tm, tableName,
            "tip_amount > 10  (sélectivité moyenne ~15%)",
            "Filtre modéré — 1 ligne sur 7 environ",
            new QueryEngine.WhereClause(COL_TIP, ">", 10.0), null));

        mesures.add(mesurerWhere(tm, tableName,
            "trip_distance > 20  (sélectivité faible ~2%)",
            "Filtre sélectif — très peu de résultats",
            new QueryEngine.WhereClause(COL_DISTANCE, ">", 20.0), null));

        mesures.add(mesurerWhere(tm, tableName,
            "total_amount < 5  (sélectivité très faible ~1%)",
            "Filtre très sélectif — courses quasiment gratuites",
            new QueryEngine.WhereClause(COL_TOTAL, "<", 5.0), null));

        return new BenchmarkSection(
            "WHERE — Sélectivité",
            "Le temps reste quasi constant : le scan parallèle parcourt tout le dataset " +
            "indépendamment du % de lignes retournées. " +
            "nbResultats = vrai compte (sans matérialisation des Maps).",
            mesures
        );
    }

    /**
     * Section 2 — GROUP BY : cardinalité
     * Montre l'impact du nombre de groupes distincts sur le temps d'agrégation.
     */
    private BenchmarkSection sectionGroupByCardinalite(TableManager tm, String tableName) {
        List<RequeteResult> mesures = new ArrayList<>();

        mesures.add(mesurer(tm, tableName,
            "GROUP BY VendorID  (2 groupes distincts)",
            "Cardinalité très faible",
            null, null, COL_VENDOR, null, true, -1));

        mesures.add(mesurer(tm, tableName,
            "GROUP BY payment_type  (6 groupes distincts)",
            "Cardinalité faible",
            null, null, COL_PAYMENT, null, true, -1));

        mesures.add(mesurer(tm, tableName,
            "GROUP BY passenger_count  (9 groupes distincts)",
            "Cardinalité faible — valeurs 0 à 9",
            null, null, COL_PASSAGERS, null, true, -1));

        return new BenchmarkSection(
            "GROUP BY — Cardinalité (nombre de groupes distincts)",
            "Avec les accumulateurs double[], l'impact de la cardinalité est minime. " +
            "Le coût dominant est le parcours des lignes, pas la gestion des groupes.",
            mesures
        );
    }

    /**
     * Section 3 — Agrégation : SUM vs AVG vs MIN vs MAX
     * Même colonne, même GROUP BY — seule la fonction change.
     */
    private BenchmarkSection sectionAggregations(TableManager tm, String tableName) {
        List<RequeteResult> mesures = new ArrayList<>();

        mesures.add(mesurer(tm, tableName,
            "GROUP BY payment_type + SUM(total_amount)",
            "Addition cumulative à chaque ligne",
            null, List.of("SUM(" + COL_TOTAL + ")"), COL_PAYMENT, null, true, -1));

        mesures.add(mesurer(tm, tableName,
            "GROUP BY payment_type + AVG(total_amount)",
            "SUM + division finale par groupe",
            null, List.of("AVG(" + COL_TOTAL + ")"), COL_PAYMENT, null, true, -1));

        mesures.add(mesurer(tm, tableName,
            "GROUP BY payment_type + MIN(total_amount)",
            "Comparaison min à chaque ligne",
            null, List.of("MIN(" + COL_TOTAL + ")"), COL_PAYMENT, null, true, -1));

        mesures.add(mesurer(tm, tableName,
            "GROUP BY payment_type + MAX(total_amount)",
            "Comparaison max à chaque ligne",
            null, List.of("MAX(" + COL_TOTAL + ")"), COL_PAYMENT, null, true, -1));

        return new BenchmarkSection(
            "GROUP BY — Type d'agrégation (SUM / AVG / MIN / MAX)",
            "Avec des accumulateurs primitifs double[], SUM/AVG/MIN/MAX ont le même coût. " +
            "Aucun boxing, aucune allocation d'objets en cours de route.",
            mesures
        );
    }

    /**
     * Section 4 — TOP-N : impact de la taille de N
     * Démontre que le min-heap O(n log k) maintient des temps faibles même pour k=1000.
     */
    private BenchmarkSection sectionTopN(TableManager tm, String tableName) {
        List<RequeteResult> mesures = new ArrayList<>();

        mesures.add(mesurer(tm, tableName,
            "Top 10   ORDER BY total_amount DESC",
            "Heap de taille 10 — log(10) ≈ 3 comparaisons par ligne",
            null, List.of(COL_TOTAL, COL_DISTANCE, COL_TIP),
            null, COL_TOTAL, false, 10));

        mesures.add(mesurer(tm, tableName,
            "Top 100  ORDER BY total_amount DESC",
            "Heap de taille 100 — log(100) ≈ 7 comparaisons par ligne",
            null, List.of(COL_TOTAL, COL_DISTANCE, COL_TIP),
            null, COL_TOTAL, false, 100));

        mesures.add(mesurer(tm, tableName,
            "Top 1000 ORDER BY total_amount DESC",
            "Heap de taille 1000 — log(1000) ≈ 10 comparaisons par ligne",
            null, List.of(COL_TOTAL, COL_DISTANCE, COL_TIP),
            null, COL_TOTAL, false, 1000));

        return new BenchmarkSection(
            "TOP-N — Taille de N (algorithme min-heap O(n log k))",
            "On ne trie jamais tout le dataset. " +
            "k=10 vs k=1000 : différence marginale car log(10)≈3 et log(1000)≈10. " +
            "Sans ce heap, trier 3.5M lignes = OOM.",
            mesures
        );
    }

    // =========================================================================
    // Helpers de mesure
    // =========================================================================

    /** Mesure générale : GROUP BY, SELECT, TOP-N */
    private RequeteResult mesurer(
            TableManager tm, String tableName,
            String titre, String description,
            QueryEngine.WhereClause where,
            List<String> select,
            String groupBy, String orderBy,
            boolean orderAsc, int limit) {

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> rows = tm.query(
            tableName, select, where, groupBy, orderBy, orderAsc, limit);
        long dureeMs = System.currentTimeMillis() - t0;

        List<Map<String, Object>> apercu = rows.size() > 5 ? rows.subList(0, 5) : rows;
        return new RequeteResult(titre, description, dureeMs, rows.size(), apercu);
    }

    /**
     * Mesure spécialisée WHERE :
     * - nbResultats = vrai compte (via count() parallèle, sans créer de Maps)
     * - apercu = 5 lignes seulement
     * - timing = temps du scan complet
     */
    private RequeteResult mesurerWhere(
            TableManager tm, String tableName,
            String titre, String description,
            QueryEngine.WhereClause where,
            List<String> selectApercu) {

        long t0 = System.currentTimeMillis();
        long nbTotal = tm.count(tableName, where);
        long dureeMs = System.currentTimeMillis() - t0;

        List<Map<String, Object>> apercu = tm.query(
            tableName, selectApercu, where, null, null, false, 5);

        return new RequeteResult(titre, description, dureeMs, (int) nbTotal, apercu);
    }

    // =========================================================================
    // DTOs
    // =========================================================================

    public static class RapportTaxi {
        public int    nbLignes;
        public long   loadMs;
        public long   loadLignesParSeconde;
        public String source;
        public List<RequeteResult>    requetes;
        public List<BenchmarkSection> sections;

        public RapportTaxi(int nbLignes, long loadMs, String source,
                           List<RequeteResult> requetes, List<BenchmarkSection> sections) {
            this.nbLignes             = nbLignes;
            this.loadMs               = loadMs;
            this.loadLignesParSeconde = loadMs > 0 ? (nbLignes * 1000L) / loadMs : 0;
            this.source               = source;
            this.requetes             = requetes;
            this.sections             = sections;
        }
    }

    public static class BenchmarkSection {
        public String categorie;
        public String description;
        public List<RequeteResult> mesures;

        public BenchmarkSection(String categorie, String description, List<RequeteResult> mesures) {
            this.categorie   = categorie;
            this.description = description;
            this.mesures     = mesures;
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
