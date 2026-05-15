package com.example.engine.api;

import com.example.engine.core.QueryEngine;
import com.example.engine.core.TableManager;
import com.example.engine.loader.CsvLoader;
import com.example.engine.storage.Column;
import com.example.engine.storage.Table;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.*;

/**
 * Endpoints de benchmark pour mesurer les performances du moteur.
 *
 * GET /benchmark/run             → benchmark complet en mémoire (100k → 4M lignes)
 * GET /benchmark/run?maxRows=N   → benchmark jusqu'à N lignes
 * GET /benchmark/csv?fichier=... → benchmark sur un fichier CSV existant
 *
 * Schéma testé : ventes(id INT, produit STRING, categorie STRING,
 *                        prix DOUBLE, quantite INT, region STRING, annee INT)
 *
 * Mémoire recommandée : JAVA_OPTS="-Xmx3g" .\mvnw.cmd quarkus:dev
 */
@Path("/benchmark")
@Produces(MediaType.APPLICATION_JSON)
public class BenchmarkResource {

    private static final int[] TAILLES = {100_000, 500_000, 1_000_000, 2_000_000, 4_000_000};

    private static final String[] CATEGORIES = {"Electronique", "Vetements", "Alimentation", "Livres", "Sport"};
    private static final String[] REGIONS    = {"Paris", "Lyon", "Marseille", "Bordeaux", "Lille"};
    private static final String[] PRODUITS   = {
        "Laptop", "Telephone", "Tablette", "Montre", "Casque",
        "T-shirt", "Jean", "Veste", "Pain", "Fromage",
        "Roman", "Guide", "Velo", "Raquette", "Ballon"
    };

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @GET
    @Path("/run")
    public Response run(@QueryParam("maxRows") @DefaultValue("4000000") int maxRows) {
        List<BenchmarkResult> resultats = new ArrayList<>();

        for (int taille : TAILLES) {
            if (taille > maxRows) break;
            resultats.add(mesurer(taille));
        }
        if (resultats.isEmpty() || resultats.get(resultats.size() - 1).nbLignes < maxRows) {
            resultats.add(mesurer(maxRows));
        }

        return Response.ok(Map.of("source", "generateur-interne", "resultats", resultats)).build();
    }

    @GET
    @Path("/csv")
    public Response runCsv(@QueryParam("fichier") String fichier) {
        if (fichier == null || fichier.isBlank())
            return Response.status(400)
                .entity(Map.of("erreur", "Parametre 'fichier' requis. Ex: /benchmark/csv?fichier=C:/data/ventes.csv"))
                .build();
        try {
            BenchmarkResult r = mesurerCsv(fichier);
            return Response.ok(Map.of("source", fichier, "resultats", List.of(r))).build();
        } catch (IOException e) {
            return Response.status(500).entity(Map.of("erreur", "Erreur lecture CSV : " + e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(Map.of("erreur", e.getMessage())).build();
        }
    }

    // -------------------------------------------------------------------------
    // Mesure — données générées en mémoire
    // -------------------------------------------------------------------------

    private BenchmarkResult mesurer(int taille) {
        TableManager tm = new TableManager();
        creerTableVentes(tm);

        // Références directes aux colonnes — évite une recherche linéaire à chaque itération
        Table  table   = tm.getInternalTable("ventes");
        Column colId   = table.getColumn("id");
        Column colProd = table.getColumn("produit");
        Column colCat  = table.getColumn("categorie");
        Column colPrix = table.getColumn("prix");
        Column colQte  = table.getColumn("quantite");
        Column colReg  = table.getColumn("region");
        Column colAn   = table.getColumn("annee");

        // LOAD — insertion directe colonne par colonne (même chemin que CsvLoader)
        Random rng = new Random(42);
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < taille; i++) {
            colId.add(i + 1);
            colProd.add(PRODUITS[i % PRODUITS.length]);
            colCat.add(CATEGORIES[i % CATEGORIES.length]);
            colPrix.add(Math.round((10 + rng.nextDouble() * 990) * 100.0) / 100.0);
            colQte.add(1 + rng.nextInt(100));
            colReg.add(REGIONS[i % REGIONS.length]);
            colAn.add(2020 + (i % 5));
        }
        long loadMs = System.currentTimeMillis() - t0;

        // WHERE annee = 2022  →  exactement 20 % des lignes
        QueryEngine.WhereClause filtreAnnee = new QueryEngine.WhereClause("annee", "=", 2022);
        t0 = System.currentTimeMillis();
        int nbWhere = tm.query("ventes", null, filtreAnnee, null, null, true, -1).size();
        long whereMs = System.currentTimeMillis() - t0;

        // GROUP BY categorie  →  5 groupes + COUNT implicite
        t0 = System.currentTimeMillis();
        tm.query("ventes", null, null, "categorie", null, true, -1);
        long groupByMs = System.currentTimeMillis() - t0;

        // GROUP BY region + SUM(prix)  →  agrégation numérique sur 5 groupes
        t0 = System.currentTimeMillis();
        tm.query("ventes", List.of("SUM(prix)"), null, "region", null, true, -1);
        long aggregationMs = System.currentTimeMillis() - t0;

        return new BenchmarkResult(taille, loadMs, whereMs, nbWhere, groupByMs, aggregationMs);
    }

    // -------------------------------------------------------------------------
    // Mesure — fichier CSV existant
    // -------------------------------------------------------------------------

    private BenchmarkResult mesurerCsv(String fichier) throws IOException {
        TableManager tm = new TableManager();
        creerTableVentes(tm);

        long t0 = System.currentTimeMillis();
        int nbLignes = CsvLoader.load(tm.getInternalTable("ventes"), fichier);
        long loadMs = System.currentTimeMillis() - t0;

        QueryEngine.WhereClause filtreAnnee = new QueryEngine.WhereClause("annee", "=", 2022);
        t0 = System.currentTimeMillis();
        int nbWhere = tm.query("ventes", null, filtreAnnee, null, null, true, -1).size();
        long whereMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        tm.query("ventes", null, null, "categorie", null, true, -1);
        long groupByMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        tm.query("ventes", List.of("SUM(prix)"), null, "region", null, true, -1);
        long aggregationMs = System.currentTimeMillis() - t0;

        return new BenchmarkResult(nbLignes, loadMs, whereMs, nbWhere, groupByMs, aggregationMs);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void creerTableVentes(TableManager tm) {
        tm.createTable("ventes", List.of(
            new TableManager.ColumnDef("id",        "INT"),
            new TableManager.ColumnDef("produit",   "STRING"),
            new TableManager.ColumnDef("categorie", "STRING"),
            new TableManager.ColumnDef("prix",      "DOUBLE"),
            new TableManager.ColumnDef("quantite",  "INT"),
            new TableManager.ColumnDef("region",    "STRING"),
            new TableManager.ColumnDef("annee",     "INT")
        ));
    }

    // -------------------------------------------------------------------------
    // DTO
    // -------------------------------------------------------------------------

    public static class BenchmarkResult {
        public int  nbLignes;
        public long loadMs;
        public long whereMs;
        public int  whereNbResultats;
        public long groupByMs;
        public long aggregationMs;
        public long loadLignesParSeconde;

        public BenchmarkResult(int nbLignes, long loadMs, long whereMs, int whereNbResultats,
                               long groupByMs, long aggregationMs) {
            this.nbLignes             = nbLignes;
            this.loadMs               = loadMs;
            this.whereMs              = whereMs;
            this.whereNbResultats     = whereNbResultats;
            this.groupByMs            = groupByMs;
            this.aggregationMs        = aggregationMs;
            this.loadLignesParSeconde = loadMs > 0 ? (nbLignes * 1000L) / loadMs : 0;
        }
    }
}
