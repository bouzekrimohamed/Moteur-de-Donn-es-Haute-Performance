package com.example.engine.core;

import com.example.engine.storage.Column;
import com.example.engine.storage.Table;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class TableManager {

    /** Tables en mémoire : nom → Table */
    private final Map<String, Table> tablesByName = new ConcurrentHashMap<>();

    /** Types des colonnes : nom de table → (nom colonne → type) */
    private final Map<String, LinkedHashMap<String, String>> columnTypesByTable = new ConcurrentHashMap<>();

    private final QueryEngine queryEngine = new QueryEngine();

    // Création de table
    public TableSchema createTable(String tableName, List<ColumnDef> columns) {
        if (tableName == null || tableName.isBlank())
            throw new IllegalArgumentException("Le nom de la table est requis");
        if (columns == null || columns.isEmpty())
            throw new IllegalArgumentException("Au moins une colonne est requise");

        String name = tableName.trim();
        if (tablesByName.containsKey(name))
            throw new IllegalArgumentException("La table existe déjà : " + name);

        LinkedHashMap<String, String> types = validateAndBuildTypeMap(columns);
        Table table = new Table(name);
        for (Map.Entry<String, String> entry : types.entrySet()) {
            table.addColumn(entry.getKey(), entry.getValue());
        }

        tablesByName.put(name, table);
        columnTypesByTable.put(name, types);
        return toSchema(name);
    }

    // Lecture de tables
    public List<TableSchema> listTables() {
        return tablesByName.keySet().stream()
                .sorted()
                .map(this::toSchema)
                .collect(Collectors.toList());
    }

    public TableSchema getTable(String tableName) {
        return toSchema(getExistingTable(tableName).getName());
    }

    // Accès interne à la Table (pour les loaders fichiers)

    /** Retourne la Table interne (pas une copie) — réservé aux loaders */
    public Table getInternalTable(String tableName) {
        return getExistingTable(tableName);
    }

    /**
     * Enregistre une Table déjà construite (ex : importée depuis un Parquet).
     * Construit le columnTypesByTable à partir des colonnes de la Table.
     */
    public void registerTable(Table table) {
        String name = table.getName();
        if (tablesByName.containsKey(name))
            throw new IllegalArgumentException("La table existe déjà : " + name);

        LinkedHashMap<String, String> types = new LinkedHashMap<>();
        for (com.example.engine.storage.Column col : table.getColumnsInternal()) {
            types.put(col.getName(), col.getType());
        }
        tablesByName.put(name, table);
        columnTypesByTable.put(name, types);
    }

    // Suppression de table
    public void dropTable(String tableName) {
        Table table = getExistingTable(tableName);
        tablesByName.remove(table.getName());
        columnTypesByTable.remove(table.getName());
    }

    // Chargement de données
    public int loadRows(String tableName, List<Map<String, Object>> rows) {
        Table table = getExistingTable(tableName);
        if (rows == null || rows.isEmpty())
            throw new IllegalArgumentException("La liste de lignes est vide");

        LinkedHashMap<String, String> types = columnTypesByTable.get(table.getName());

        for (Map<String, Object> row : rows) {
            if (row == null) throw new IllegalArgumentException("Une ligne ne peut pas être null");

            // Vérifier colonnes inconnues
            for (String key : row.keySet()) {
                if (!types.containsKey(key))
                    throw new IllegalArgumentException("Colonne inconnue dans la ligne : " + key);
            }

            // Insérer colonne par colonne
            for (Map.Entry<String, String> entry : types.entrySet()) {
                String colName = entry.getKey();
                String colType = entry.getValue();
                Object value = row.get(colName); // null si absent

                if (value != null && !isValueCompatible(colType, value))
                    throw new IllegalArgumentException(
                            "Type invalide pour la colonne '" + colName + "' (attendu " + colType + ")");

                table.addToColumn(colName, value);
            }
        }
        return rows.size();
    }

    // Lecture brute des lignes
    public List<Map<String, Object>> getRows(String tableName) {
        return getExistingTable(tableName).toRows();
    }

    // Exécution de requête le passe a l'aute fichier
    public List<Map<String, Object>> query(
            String tableName,
            List<String> select,
            QueryEngine.WhereClause where,
            String groupBy,
            String orderBy,
            boolean orderAsc,
            int limit) {

        Table table = getExistingTable(tableName);
        return queryEngine.query(table, select, where, groupBy, orderBy, orderAsc, limit);
    }

    // Helpers internes
    private Table getExistingTable(String tableName) {
        if (tableName == null || tableName.isBlank())
            throw new IllegalArgumentException("Le nom de la table est requis");
        Table t = tablesByName.get(tableName.trim());
        if (t == null)
            throw new IllegalArgumentException("Table introuvable : " + tableName);
        return t;
    }

    private LinkedHashMap<String, String> validateAndBuildTypeMap(List<ColumnDef> columns) {
        LinkedHashMap<String, String> types = new LinkedHashMap<>();
        Set<String> allowed = Set.of("STRING", "INT", "LONG", "DOUBLE", "BOOLEAN");
        for (ColumnDef col : columns) {
            if (col == null || col.name == null || col.name.isBlank())
                throw new IllegalArgumentException("Le nom de colonne est requis");
            if (col.type == null || col.type.isBlank())
                throw new IllegalArgumentException("Le type de colonne est requis pour : " + col.name);

            String n = col.name.trim();
            String t = col.type.trim().toUpperCase(Locale.ROOT);
            if (!allowed.contains(t))
                throw new IllegalArgumentException("Type non supporté : " + col.type
                        + ". Types valides : " + allowed);
            if (types.containsKey(n))
                throw new IllegalArgumentException("Nom de colonne en double : " + n);
            types.put(n, t);
        }
        return types;
    }

    private boolean isValueCompatible(String type, Object value) {
        if (value == null) return true;
        return switch (type) {
            case "STRING"  -> value instanceof String;
            case "INT"     -> value instanceof Integer;
            case "LONG"    -> value instanceof Integer || value instanceof Long;
            case "DOUBLE"  -> value instanceof Integer || value instanceof Long || value instanceof Double;
            case "BOOLEAN" -> value instanceof Boolean;
            default        -> false;
        };
    }

    private TableSchema toSchema(String tableName) {
        LinkedHashMap<String, String> types = columnTypesByTable.get(tableName);
        List<ColumnDef> cols = types.entrySet().stream()
                .map(e -> new ColumnDef(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        return new TableSchema(tableName, cols);
    }

    // DTOs imbriqués
    public static class ColumnDef {
        public String name;
        public String type;
        public ColumnDef() {}
        public ColumnDef(String name, String type) { this.name = name; this.type = type; }
    }

    public static class TableSchema {
        public String tableName;
        public List<ColumnDef> columns;
        public TableSchema() {}
        public TableSchema(String tableName, List<ColumnDef> columns) {
            this.tableName = tableName;
            this.columns   = columns;
        }
    }
}
