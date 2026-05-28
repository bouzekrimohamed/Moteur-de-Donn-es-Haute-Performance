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
}
