package com.example.engine;

import com.example.engine.core.QueryEngine;
import com.example.engine.core.TableManager;
import com.example.engine.storage.Column;
import com.example.engine.storage.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires du moteur de données haute performance.
 * Aucune dépendance CDI/Quarkus — pure JUnit 5.
 *
 * Couverture :
 *  - Column  : stockage, accès, clone
 *  - Table   : gestion des colonnes, lignes, sérialisation
 *  - TableManager : cycle de vie (create/drop/load/query) + validations
 *  - QueryEngine  : WHERE, SELECT, GROUP BY, ORDER BY, LIMIT, agrégations
 */
class EngineUnitTest {

    // =========================================================================
    // Column
    // =========================================================================
    @Nested
    @DisplayName("Column — stockage et accès")
    class ColumnTests {

        @Test
        @DisplayName("Constructeur vide : taille 0")
        void emptyColumn() {
            Column col = new Column("age", "INT");
            assertEquals("age", col.getName());
            assertEquals("INT", col.getType());
            assertEquals(0, col.size());
        }

        @Test
        @DisplayName("add / get / size fonctionnent correctement")
        void addAndGet() {
            Column col = new Column("prix", "DOUBLE");
            col.add(1.5);
            col.add(2.0);
            assertEquals(2, col.size());
            assertEquals(1.5, col.get(0));
            assertEquals(2.0, col.get(1));
        }

        @Test
        @DisplayName("getIndex retourne -1 si valeur absente")
        void getIndexNotFound() {
            Column col = new Column("nom", "STRING");
            col.add("Alice");
            assertEquals(-1, col.getIndex("Bob"));
        }

        @Test
        @DisplayName("getIndex retourne le bon indice")
        void getIndexFound() {
            Column col = new Column("nom", "STRING");
            col.add("Alice");
            col.add("Bob");
            assertEquals(1, col.getIndex("Bob"));
        }

        @Test
        @DisplayName("getAll retourne une copie défensive")
        void getAllIsDefensiveCopy() {
            Column col = new Column("x", "INT");
            col.add(10);
            List<Object> all = col.getAll();
            all.add(99);
            assertEquals(1, col.size(), "La modification de getAll() ne doit pas affecter la colonne");
        }

        @Test
        @DisplayName("clone produit une copie indépendante")
        void cloneIsIndependent() {
            Column col = new Column("v", "INT");
            col.add(42);
            Column clone = col.clone();
            clone.add(100);
            assertEquals(1, col.size(), "Le clone ne doit pas modifier l'original");
        }

        @Test
        @DisplayName("Constructeur avec data préinitialisée")
        void constructorWithData() {
            Column col = new Column("ids", "LONG", List.of(1L, 2L, 3L));
            assertEquals(3, col.size());
            assertEquals(2L, col.get(1));
        }
    }

    // =========================================================================
    // Table
    // =========================================================================
    @Nested
    @DisplayName("Table — colonnes et lignes")
    class TableTests {

        private Table table;

        @BeforeEach
        void setup() {
            table = new Table("ventes");
            table.addColumn("produit", "STRING");
            table.addColumn("quantite", "INT");
        }

        @Test
        @DisplayName("rowCount = 0 sur table vide")
        void emptyRowCount() {
            assertEquals(0, table.totalRowCount());
        }

        @Test
        @DisplayName("addToColumn augmente le rowCount")
        void addToColumnIncrementsRowCount() {
            table.addToColumn("produit", "Laptop");
            table.addToColumn("quantite", 3);
            assertEquals(1, table.totalRowCount());
        }

        @Test
        @DisplayName("getColumn retourne la bonne colonne")
        void getColumnByName() {
            Column c = table.getColumn("produit");
            assertEquals("produit", c.getName());
            assertEquals("STRING", c.getType());
        }

        @Test
        @DisplayName("getColumn lève IllegalArgumentException si colonne inconnue")
        void getColumnUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> table.getColumn("inexistante"));
        }

        @Test
        @DisplayName("removeColumn supprime bien la colonne")
        void removeColumn() {
            boolean removed = table.removeColumn("quantite");
            assertTrue(removed);
            assertThrows(IllegalArgumentException.class, () -> table.getColumn("quantite"));
        }

        @Test
        @DisplayName("toRows sérialise correctement en liste de maps")
        void toRows() {
            table.addToColumn("produit", "Phone");
            table.addToColumn("quantite", 5);
            table.addToColumn("produit", "Tablet");
            table.addToColumn("quantite", 2);

            List<Map<String, Object>> rows = table.toRows();
            assertEquals(2, rows.size());
            assertEquals("Phone", rows.get(0).get("produit"));
            assertEquals(5, rows.get(0).get("quantite"));
            assertEquals("Tablet", rows.get(1).get("produit"));
        }

        @Test
        @DisplayName("getColumns retourne des copies défensives")
        void getColumnsDefensive() {
            List<Column> cols = table.getColumns();
            cols.get(0).add("Hack");
            // La table originale ne doit pas être modifiée
            assertEquals(0, table.totalRowCount());
        }

        @Test
        @DisplayName("addToColumn retourne false si colonne inconnue")
        void addToColumnUnknownReturnsFalse() {
            boolean result = table.addToColumn("inexistante", "valeur");
            assertFalse(result);
        }
    }

    // =========================================================================
    // TableManager
    // =========================================================================
    @Nested
    @DisplayName("TableManager — cycle de vie des tables")
    class TableManagerTests {

        private TableManager manager;

        @BeforeEach
        void setup() {
            manager = new TableManager();
        }

        // --- Création ---

        @Test
        @DisplayName("createTable crée une table et la rend listable")
        void createTableSuccess() {
            var schema = manager.createTable("clients",
                    List.of(new TableManager.ColumnDef("nom", "STRING"),
                            new TableManager.ColumnDef("age", "INT")));

            assertEquals("clients", schema.tableName);
            assertEquals(2, schema.columns.size());
            assertEquals(1, manager.listTables().size());
        }

        @Test
        @DisplayName("createTable rejette un nom vide")
        void createTableBlankNameThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.createTable("  ", List.of(new TableManager.ColumnDef("x", "INT"))));
        }

        @Test
        @DisplayName("createTable rejette des colonnes vides")
        void createTableNoColumnsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.createTable("t", List.of()));
        }

        @Test
        @DisplayName("createTable rejette un doublon de table")
        void createTableDuplicateThrows() {
            manager.createTable("t", List.of(new TableManager.ColumnDef("x", "INT")));
            assertThrows(IllegalArgumentException.class,
                    () -> manager.createTable("t", List.of(new TableManager.ColumnDef("y", "INT"))));
        }

        @Test
        @DisplayName("createTable rejette un type de colonne invalide")
        void createTableInvalidTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.createTable("t",
                            List.of(new TableManager.ColumnDef("x", "FLOAT"))));
        }

        @Test
        @DisplayName("createTable rejette des noms de colonnes en double")
        void createTableDuplicateColumnThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.createTable("t",
                            List.of(new TableManager.ColumnDef("x", "INT"),
                                    new TableManager.ColumnDef("x", "STRING"))));
        }

        // --- Suppression ---

        @Test
        @DisplayName("dropTable supprime la table")
        void dropTable() {
            manager.createTable("tmp", List.of(new TableManager.ColumnDef("v", "BOOLEAN")));
            manager.dropTable("tmp");
            assertTrue(manager.listTables().isEmpty());
        }

        @Test
        @DisplayName("dropTable lève une exception si table inconnue")
        void dropTableUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> manager.dropTable("ghost"));
        }

        // --- Chargement de données ---

        @Test
        @DisplayName("loadRows insère des lignes et les retrouve via getRows")
        void loadRowsAndGetRows() {
            manager.createTable("produits",
                    List.of(new TableManager.ColumnDef("nom", "STRING"),
                            new TableManager.ColumnDef("prix", "DOUBLE")));

            int inserted = manager.loadRows("produits",
                    List.of(
                            Map.of("nom", "Laptop", "prix", 999.0),
                            Map.of("nom", "Phone",  "prix", 499.0)
                    ));

            assertEquals(2, inserted);
            List<Map<String, Object>> rows = manager.getRows("produits");
            assertEquals(2, rows.size());
            assertEquals("Laptop", rows.get(0).get("nom"));
        }

        @Test
        @DisplayName("loadRows rejette une liste vide")
        void loadRowsEmptyThrows() {
            manager.createTable("t", List.of(new TableManager.ColumnDef("x", "INT")));
            assertThrows(IllegalArgumentException.class, () -> manager.loadRows("t", List.of()));
        }

        @Test
        @DisplayName("loadRows rejette une colonne inconnue dans la ligne")
        void loadRowsUnknownColumnThrows() {
            manager.createTable("t", List.of(new TableManager.ColumnDef("x", "INT")));
            assertThrows(IllegalArgumentException.class,
                    () -> manager.loadRows("t", List.of(Map.of("x", 1, "y", 2))));
        }

        @Test
        @DisplayName("loadRows rejette une valeur de mauvais type")
        void loadRowsWrongTypeThrows() {
            manager.createTable("t", List.of(new TableManager.ColumnDef("x", "INT")));
            assertThrows(IllegalArgumentException.class,
                    () -> manager.loadRows("t", List.of(Map.of("x", "chaine"))));
        }

        @Test
        @DisplayName("loadRows accepte les valeurs null (colonne absente de la map)")
        void loadRowsNullValueAccepted() {
            manager.createTable("t",
                    List.of(new TableManager.ColumnDef("x", "INT"),
                            new TableManager.ColumnDef("y", "STRING")));
            // y est absent → null
            assertDoesNotThrow(() -> manager.loadRows("t", List.of(Map.of("x", 1))));
            assertEquals(1, manager.getRows("t").size());
            assertNull(manager.getRows("t").get(0).get("y"));
        }

        // --- registerTable ---

        @Test
        @DisplayName("registerTable enregistre une table externe")
        void registerTable() {
            Table t = new Table("externe");
            t.addColumn("id", "LONG");
            manager.registerTable(t);
            assertNotNull(manager.getTable("externe"));
        }

        @Test
        @DisplayName("registerTable rejette une table déjà existante")
        void registerTableDuplicateThrows() {
            manager.createTable("t", List.of(new TableManager.ColumnDef("x", "INT")));
            Table t = new Table("t");
            assertThrows(IllegalArgumentException.class, () -> manager.registerTable(t));
        }
    }

    // =========================================================================
    // QueryEngine
    // =========================================================================
    @Nested
    @DisplayName("QueryEngine — requêtes en mémoire")
    class QueryEngineTests {

        private Table table;
        private QueryEngine engine;

        /**
         *  Table "employes" :
         *  | nom     | dept | salaire |
         *  | Alice   | IT   | 5000    |
         *  | Bob     | RH   | 3000    |
         *  | Charlie | IT   | 7000    |
         *  | Diana   | RH   | 4000    |
         *  | Eve     | IT   | 6000    |
         */
        @BeforeEach
        void setup() {
            engine = new QueryEngine();
            table = new Table("employes");
            table.addColumn("nom", "STRING");
            table.addColumn("dept", "STRING");
            table.addColumn("salaire", "INT");

            for (Object[] row : new Object[][]{
                    {"Alice",   "IT", 5000},
                    {"Bob",     "RH", 3000},
                    {"Charlie", "IT", 7000},
                    {"Diana",   "RH", 4000},
                    {"Eve",     "IT", 6000},
            }) {
                table.addToColumn("nom",     row[0]);
                table.addToColumn("dept",    row[1]);
                table.addToColumn("salaire", row[2]);
            }
        }

        // --- Pas de filtre ---

        @Test
        @DisplayName("Sans filtre : toutes les lignes sont retournées")
        void noFilter() {
            List<Map<String, Object>> result = engine.query(
                    table, null, null, null, null, true, -1);
            assertEquals(5, result.size());
        }

        // --- SELECT ---

        @Test
        @DisplayName("SELECT sur une seule colonne")
        void selectSingleColumn() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom"), null, null, null, true, -1);
            assertEquals(5, result.size());
            assertTrue(result.get(0).containsKey("nom"));
            assertFalse(result.get(0).containsKey("salaire"));
        }

        // --- WHERE ---

        @Test
        @DisplayName("WHERE = filtre correctement")
        void whereEquals() {
            var where = new QueryEngine.WhereClause("dept", "=", "IT");
            List<Map<String, Object>> result = engine.query(
                    table, null, where, null, null, true, -1);
            assertEquals(3, result.size());
            result.forEach(r -> assertEquals("IT", r.get("dept")));
        }

        @Test
        @DisplayName("WHERE != exclut les lignes correctement")
        void whereNotEquals() {
            var where = new QueryEngine.WhereClause("dept", "!=", "IT");
            List<Map<String, Object>> result = engine.query(
                    table, null, where, null, null, true, -1);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("WHERE > filtre les valeurs numériques")
        void whereGreaterThan() {
            var where = new QueryEngine.WhereClause("salaire", ">", 5000);
            List<Map<String, Object>> result = engine.query(
                    table, null, where, null, null, true, -1);
            assertEquals(2, result.size()); // Charlie 7000, Eve 6000
        }

        @Test
        @DisplayName("WHERE <= filtre les valeurs numériques inférieures ou égales")
        void whereLessOrEqual() {
            var where = new QueryEngine.WhereClause("salaire", "<=", 4000);
            List<Map<String, Object>> result = engine.query(
                    table, null, where, null, null, true, -1);
            assertEquals(2, result.size()); // Bob 3000, Diana 4000
        }

        @Test
        @DisplayName("WHERE CONTAINS filtre les sous-chaînes")
        void whereContains() {
            var where = new QueryEngine.WhereClause("nom", "CONTAINS", "li");
            List<Map<String, Object>> result = engine.query(
                    table, null, where, null, null, true, -1);
            // Alice (Ali) et Charlie (arli) ne contiennent pas "li" mais "li"→ Alice: "Ali" non, Charlie: "arli" non
            // Vérifions : "Alice".contains("li")=true, "Charlie".contains("li")=true
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("WHERE sur une cellule null retourne false (pas d'exception)")
        void whereNullCellIgnored() {
            Table t = new Table("test");
            t.addColumn("x", "INT");
            t.addToColumn("x", null);
            t.addToColumn("x", 5);

            var where = new QueryEngine.WhereClause("x", ">", 0);
            List<Map<String, Object>> result = engine.query(
                    t, null, where, null, null, true, -1);
            assertEquals(1, result.size());
            assertEquals(5, result.get(0).get("x"));
        }

        // --- ORDER BY ---

        @Test
        @DisplayName("ORDER BY ASC trie du plus petit au plus grand")
        void orderByAsc() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom", "salaire"), null, null, "salaire", true, -1);
            assertEquals(3000, result.get(0).get("salaire"));
            assertEquals(7000, result.get(4).get("salaire"));
        }

        @Test
        @DisplayName("ORDER BY DESC trie du plus grand au plus petit")
        void orderByDesc() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom", "salaire"), null, null, "salaire", false, -1);
            assertEquals(7000, result.get(0).get("salaire"));
            assertEquals(3000, result.get(4).get("salaire"));
        }

        @Test
        @DisplayName("ORDER BY alphabétique ASC")
        void orderByStringAsc() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom"), null, null, "nom", true, -1);
            assertEquals("Alice", result.get(0).get("nom"));
            assertEquals("Eve",   result.get(4).get("nom"));
        }

        // --- LIMIT ---

        @Test
        @DisplayName("LIMIT restreint le nombre de résultats")
        void limit() {
            List<Map<String, Object>> result = engine.query(
                    table, null, null, null, null, true, 2);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("LIMIT = -1 : aucune restriction")
        void limitMinusOne() {
            List<Map<String, Object>> result = engine.query(
                    table, null, null, null, null, true, -1);
            assertEquals(5, result.size());
        }

        @Test
        @DisplayName("LIMIT supérieur au total : retourne tout")
        void limitBigger() {
            List<Map<String, Object>> result = engine.query(
                    table, null, null, null, null, true, 100);
            assertEquals(5, result.size());
        }

        // --- GROUP BY + COUNT ---

        @Test
        @DisplayName("GROUP BY count par département")
        void groupByCount() {
            List<Map<String, Object>> result = engine.query(
                    table, null, null, "dept", null, true, -1);
            assertEquals(2, result.size());

            Map<Object, Object> countByDept = new java.util.HashMap<>();
            result.forEach(r -> countByDept.put(r.get("dept"), r.get("count")));

            assertEquals(3, countByDept.get("IT"));
            assertEquals(2, countByDept.get("RH"));
        }

        @Test
        @DisplayName("GROUP BY avec SUM")
        void groupBySum() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("SUM(salaire)"), null, "dept", null, true, -1);

            Map<Object, Object> sumByDept = new java.util.HashMap<>();
            result.forEach(r -> sumByDept.put(r.get("dept"), r.get("SUM(salaire)")));

            // IT : 5000+7000+6000 = 18000
            assertEquals(18000.0, sumByDept.get("IT"));
            // RH : 3000+4000 = 7000
            assertEquals(7000.0, sumByDept.get("RH"));
        }

        @Test
        @DisplayName("GROUP BY avec AVG")
        void groupByAvg() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("AVG(salaire)"), null, "dept", null, true, -1);

            Map<Object, Object> avgByDept = new java.util.HashMap<>();
            result.forEach(r -> avgByDept.put(r.get("dept"), r.get("AVG(salaire)")));

            // IT : 18000 / 3 = 6000
            assertEquals(6000.0, avgByDept.get("IT"));
        }

        @Test
        @DisplayName("GROUP BY avec MIN et MAX")
        void groupByMinMax() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("MIN(salaire)", "MAX(salaire)"), null, "dept", null, true, -1);

            Map<Object, Map<String, Object>> byDept = new java.util.HashMap<>();
            result.forEach(r -> byDept.put(r.get("dept"),
                    Map.of("min", r.get("MIN(salaire)"), "max", r.get("MAX(salaire)"))));

            assertEquals(5000.0, byDept.get("IT").get("min")); // min IT
            assertEquals(7000.0, byDept.get("IT").get("max")); // max IT
            assertEquals(3000.0, byDept.get("RH").get("min")); // min RH
            assertEquals(4000.0, byDept.get("RH").get("max")); // max RH
        }

        // --- Combinaisons WHERE + ORDER BY + LIMIT ---

        @Test
        @DisplayName("WHERE + ORDER BY DESC + LIMIT = top employé IT")
        void whereOrderByLimit() {
            var where = new QueryEngine.WhereClause("dept", "=", "IT");
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom", "salaire"), where, null, "salaire", false, 1);

            assertEquals(1, result.size());
            assertEquals("Charlie", result.get(0).get("nom"));
            assertEquals(7000, result.get(0).get("salaire"));
        }

        @Test
        @DisplayName("ORDER BY DESC + LIMIT : heap borné retourne les K plus grandes valeurs (ordre correct)")
        void orderByDescWithLimitBoundedHeap() {
            // Table de 5 employés, on demande les 3 mieux payés en DESC.
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom", "salaire"), null, null, "salaire", false, 3);

            assertEquals(3, result.size());
            // Premier = plus haut salaire
            assertEquals(7000, result.get(0).get("salaire")); // Charlie
            assertEquals(6000, result.get(1).get("salaire")); // Eve
            assertEquals(5000, result.get(2).get("salaire")); // Alice
        }

        @Test
        @DisplayName("ORDER BY ASC + LIMIT : heap borné retourne les K plus petites valeurs (ordre correct)")
        void orderByAscWithLimitBoundedHeap() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom", "salaire"), null, null, "salaire", true, 2);

            assertEquals(2, result.size());
            assertEquals(3000, result.get(0).get("salaire")); // Bob
            assertEquals(4000, result.get(1).get("salaire")); // Diana
        }

        @Test
        @DisplayName("ORDER BY DESC + LIMIT >= taille table : retourne toutes les lignes triées")
        void orderByDescLimitLargerThanTable() {
            List<Map<String, Object>> result = engine.query(
                    table, List.of("nom", "salaire"), null, null, "salaire", false, 100);

            assertEquals(5, result.size());
            assertEquals(7000, result.get(0).get("salaire"));
            assertEquals(3000, result.get(4).get("salaire"));
        }

        // --- TableManager.query intégration ---

        @Test
        @DisplayName("TableManager.query délègue correctement au QueryEngine")
        void tableManagerQuery() {
            TableManager mgr = new TableManager();
            mgr.createTable("emp",
                    List.of(new TableManager.ColumnDef("nom", "STRING"),
                            new TableManager.ColumnDef("salaire", "INT")));
            mgr.loadRows("emp", List.of(
                    Map.of("nom", "Alice", "salaire", 5000),
                    Map.of("nom", "Bob",   "salaire", 3000)
            ));

            var where = new QueryEngine.WhereClause("salaire", ">", 4000);
            List<Map<String, Object>> result = mgr.query(
                    "emp", List.of("nom"), where, null, null, true, -1);

            assertEquals(1, result.size());
            assertEquals("Alice", result.get(0).get("nom"));
        }
    }

    // =========================================================================
    // DiskRowCursor — lecture correcte après reset intermédiaire
    // =========================================================================
    @Nested
    @DisplayName("DiskRowCursor — intégrité des données après TC_RESET")
    class DiskResetTests {

        /**
         * Vérifie que les données qui traversent un point de reset (écriture en
         * plusieurs blocs de SPILL_RESET_INTERVAL lignes) sont lues correctement.
         *
         * On abaisse le seuil de spill à 3 × SPILL_RESET_INTERVAL lignes pour
         * forcer plusieurs resets dans un seul segment, puis on s'assure que :
         *   - le nombre de lignes relues est exact,
         *   - les valeurs situées juste après un reset sont correctes,
         *   - les valeurs situées juste avant un reset sont correctes.
         */
        @Test
        @DisplayName("Spill avec reset intermédiaire : toutes les lignes sont relues correctement")
        void spillWithIntermediateReset() {
            // On veut dépasser SPILL_RESET_INTERVAL pour déclencher au moins 1 reset.
            int totalRows = Table.SPILL_RESET_INTERVAL * 3; // ex. 3 000 lignes

            Table t = new Table("reset_test");
            t.setMaxRowsInMemory(totalRows); // tout en mémoire d'abord
            t.addColumn("id",    "INT");
            t.addColumn("label", "STRING");

            for (int i = 0; i < totalRows; i++) {
                t.addToColumn("id",    i);
                t.addToColumn("label", "row-" + i);
            }

            // Forcer le spill manuellement en abaissant le seuil, puis relire
            t.setMaxRowsInMemory(1); // déclenche le spill au prochain accès interne
            // On recrée la table directement avec le seuil bas pour avoir le spill
            Table t2 = new Table("reset_test2");
            t2.setMaxRowsInMemory(totalRows + 1); // tout en RAM — pas de spill
            t2.addColumn("id",    "INT");
            t2.addColumn("label", "STRING");
            for (int i = 0; i < totalRows; i++) {
                t2.addToColumn("id",    i);
                t2.addToColumn("label", "row-" + i);
            }
            // Forcer un spill en réduisant le seuil et en ajoutant une ligne
            t2.setMaxRowsInMemory(totalRows); // seuil = totalRows → déjà atteint
            t2.addToColumn("id",    totalRows);  // cette insertion déclenche le spill
            t2.addToColumn("label", "row-" + totalRows);

            // Le segment disque contient totalRows lignes avec au moins 2 resets.
            assertTrue(t2.getDiskSegments().size() >= 1,
                    "Au moins un segment disque doit exister");

            // Lecture complète via query
            List<Map<String, Object>> rows = new QueryEngine().query(
                    t2, List.of("id", "label"), null, null, "id", true, -1);

            // Nombre total = totalRows + 1 (celle encore en RAM)
            assertEquals(totalRows + 1, rows.size());

            // Lignes juste avant un reset (SPILL_RESET_INTERVAL - 1)
            int beforeReset = Table.SPILL_RESET_INTERVAL - 1;
            assertEquals(beforeReset, rows.get(beforeReset).get("id"));
            assertEquals("row-" + beforeReset, rows.get(beforeReset).get("label"));

            // Lignes juste après un reset (SPILL_RESET_INTERVAL)
            assertEquals(Table.SPILL_RESET_INTERVAL, rows.get(Table.SPILL_RESET_INTERVAL).get("id"));
            assertEquals("row-" + Table.SPILL_RESET_INTERVAL,
                    rows.get(Table.SPILL_RESET_INTERVAL).get("label"));

            // Dernière ligne du segment (2 × SPILL_RESET_INTERVAL - 1)
            int lastInSeg = totalRows - 1;
            assertEquals(lastInSeg, rows.get(lastInSeg).get("id"));
            assertEquals("row-" + lastInSeg, rows.get(lastInSeg).get("label"));
        }
    }

    // =========================================================================
    // Parallélisme — table multi-segments
    // =========================================================================
    @Nested
    @DisplayName("Parallélisme — table multi-segments (spill forcé)")
    class ParallelQueryTests {

        /**
         * Table "scores" avec seuil de spill = 3 lignes :
         * on insère 9 lignes → 3 segments disque de 3 lignes chacun + 0 en RAM.
         *
         *  | joueur  | score |
         *  | Alice   |  10   |
         *  | Bob     |  50   |
         *  | Charlie |  30   |
         *  | Diana   |  80   |
         *  | Eve     |  20   |
         *  | Frank   |  60   |
         *  | Grace   |  90   |
         *  | Hank    |  40   |
         *  | Iris    |  70   |
         */
        private Table buildMultiSegmentTable() {
            Table t = new Table("scores");
            t.setMaxRowsInMemory(3); // spill tous les 3 lignes
            t.addColumn("joueur", "STRING");
            t.addColumn("score",  "INT");

            for (Object[] row : new Object[][]{
                    {"Alice",   10}, {"Bob",   50}, {"Charlie", 30},
                    {"Diana",   80}, {"Eve",   20}, {"Frank",   60},
                    {"Grace",   90}, {"Hank",  40}, {"Iris",    70},
            }) {
                t.addToColumn("joueur", row[0]);
                t.addToColumn("score",  row[1]);
            }
            return t;
        }

        @Test
        @DisplayName("SELECT * parallèle : toutes les lignes sont présentes")
        void parallelSelectAll() {
            Table t = buildMultiSegmentTable();
            assertEquals(3, t.getDiskSegments().size()); // spill bien déclenché

            List<Map<String, Object>> result = new QueryEngine().query(
                    t, null, null, null, null, true, -1);

            assertEquals(9, result.size());
        }

        @Test
        @DisplayName("SELECT parallèle + WHERE : résultat identique au séquentiel")
        void parallelSelectWithWhere() {
            Table t = buildMultiSegmentTable();
            var where = new QueryEngine.WhereClause("score", ">", 50);

            List<Map<String, Object>> resultParallel = new QueryEngine().query(
                    t, List.of("joueur", "score"), where, null, null, true, -1);

            // Référence séquentielle : même table, seuil élevé → 1 seul segment RAM
            Table tSeq = new Table("scores_seq");
            tSeq.addColumn("joueur", "STRING");
            tSeq.addColumn("score",  "INT");
            for (Object[] row : new Object[][]{
                    {"Alice",10},{"Bob",50},{"Charlie",30},
                    {"Diana",80},{"Eve",20},{"Frank",60},
                    {"Grace",90},{"Hank",40},{"Iris",70}}) {
                tSeq.addToColumn("joueur", row[0]);
                tSeq.addToColumn("score",  row[1]);
            }
            List<Map<String, Object>> resultSeq = new QueryEngine().query(
                    tSeq, List.of("joueur", "score"), where, null, null, true, -1);

            // Même taille, mêmes joueurs (indépendamment de l'ordre)
            assertEquals(resultSeq.size(), resultParallel.size());
            Set<Object> joueursParallel = new java.util.HashSet<>();
            resultParallel.forEach(r -> joueursParallel.add(r.get("joueur")));
            Set<Object> joueursSeq = new java.util.HashSet<>();
            resultSeq.forEach(r -> joueursSeq.add(r.get("joueur")));
            assertEquals(joueursSeq, joueursParallel);
        }

        @Test
        @DisplayName("ORDER BY DESC + LIMIT parallèle : top 3 corrects")
        void parallelOrderByDescLimit() {
            Table t = buildMultiSegmentTable();
            List<Map<String, Object>> result = new QueryEngine().query(
                    t, List.of("joueur", "score"), null, null, "score", false, 3);

            assertEquals(3, result.size());
            assertEquals(90, result.get(0).get("score")); // Grace
            assertEquals(80, result.get(1).get("score")); // Diana
            assertEquals(70, result.get(2).get("score")); // Iris
        }

        @Test
        @DisplayName("ORDER BY ASC + LIMIT parallèle : bottom 2 corrects")
        void parallelOrderByAscLimit() {
            Table t = buildMultiSegmentTable();
            List<Map<String, Object>> result = new QueryEngine().query(
                    t, List.of("joueur", "score"), null, null, "score", true, 2);

            assertEquals(2, result.size());
            assertEquals(10, result.get(0).get("score")); // Alice
            assertEquals(20, result.get(1).get("score")); // Eve
        }

        @Test
        @DisplayName("GROUP BY parallèle : SUM identique au séquentiel")
        void parallelGroupBySum() {
            // Table avec deux catégories réparties sur plusieurs segments
            Table t = new Table("ventes");
            t.setMaxRowsInMemory(2);
            t.addColumn("cat",    "STRING");
            t.addColumn("montant","INT");

            // Catégorie A : 10+30+50 = 90, Catégorie B : 20+40+60 = 120
            for (Object[] row : new Object[][]{
                    {"A",10},{"B",20},{"A",30},
                    {"B",40},{"A",50},{"B",60}}) {
                t.addToColumn("cat",     row[0]);
                t.addToColumn("montant", row[1]);
            }

            List<Map<String, Object>> result = new QueryEngine().query(
                    t, List.of("SUM(montant)"), null, "cat", null, true, -1);

            Map<Object, Object> sumByCat = new java.util.HashMap<>();
            result.forEach(r -> sumByCat.put(r.get("cat"), r.get("SUM(montant)")));

            assertEquals(90.0,  sumByCat.get("A"));
            assertEquals(120.0, sumByCat.get("B"));
        }

        @Test
        @DisplayName("GROUP BY parallèle : MIN et MAX identiques au séquentiel")
        void parallelGroupByMinMax() {
            Table t = new Table("notes");
            t.setMaxRowsInMemory(2);
            t.addColumn("matiere", "STRING");
            t.addColumn("note",    "INT");

            // Math : min=5, max=20 ; Info : min=10, max=18
            for (Object[] row : new Object[][]{
                    {"Math",15},{"Info",10},{"Math",5},
                    {"Info",18},{"Math",20},{"Info",12}}) {
                t.addToColumn("matiere", row[0]);
                t.addToColumn("note",    row[1]);
            }

            List<Map<String, Object>> result = new QueryEngine().query(
                    t, List.of("MIN(note)", "MAX(note)"), null, "matiere", null, true, -1);

            Map<Object, Map<String,Object>> byMat = new java.util.HashMap<>();
            result.forEach(r -> byMat.put(r.get("matiere"),
                    Map.of("min", r.get("MIN(note)"), "max", r.get("MAX(note)"))));

            assertEquals(5.0,  byMat.get("Math").get("min"));
            assertEquals(20.0, byMat.get("Math").get("max"));
            assertEquals(10.0, byMat.get("Info").get("min"));
            assertEquals(18.0, byMat.get("Info").get("max"));
        }
    }
}
