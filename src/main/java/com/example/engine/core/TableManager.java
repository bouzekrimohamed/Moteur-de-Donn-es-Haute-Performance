package com.example.engine.core;

import data.Column;
import data.Table;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class TableManager {
    private final Map<String, Table> tablesByName = new ConcurrentHashMap<>();
    private final Map<String, LinkedHashMap<String, String>> columnTypesByTable = new ConcurrentHashMap<>();

    public TableSchema createTable(String tableName, List<ColumnDef> columns) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Columns are required");
        }

        String normalizedTableName = tableName.trim();
        if (tablesByName.containsKey(normalizedTableName)) {
            throw new IllegalArgumentException("Table already exists: " + normalizedTableName);
        }

        LinkedHashMap<String, String> types = validateAndBuildTypeMap(columns);
        Table table = new Table(normalizedTableName);
        for (String columnName : types.keySet()) {
            table.addColumn(columnName);
        }

        tablesByName.put(normalizedTableName, table);
        columnTypesByTable.put(normalizedTableName, types);
        return toSchema(normalizedTableName);
    }

    public List<TableSchema> listTables() {
        return tablesByName.keySet().stream()
                .sorted()
                .map(this::toSchema)
                .collect(Collectors.toList());
    }

    public TableSchema getTable(String tableName) {
        Table table = getExistingTable(tableName);
        return toSchema(table.getName());
    }

    public int loadRows(String tableName, List<Map<String, Object>> rows) {
        Table table = getExistingTable(tableName);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Rows are required");
        }

        LinkedHashMap<String, String> types = columnTypesByTable.get(table.getName());
        for (Map<String, Object> row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("Row cannot be null");
            }

            for (String columnName : types.keySet()) {
                if (!row.containsKey(columnName)) {
                    throw new IllegalArgumentException("Missing value for column: " + columnName);
                }
                Object value = row.get(columnName);
                if (!isValueCompatible(types.get(columnName), value)) {
                    throw new IllegalArgumentException("Invalid type for column: " + columnName);
                }
                table.addToColumn(columnName, value);
            }

            for (String payloadColumn : row.keySet()) {
                if (!types.containsKey(payloadColumn)) {
                    throw new IllegalArgumentException("Unknown column in row: " + payloadColumn);
                }
            }
        }
        return rows.size();
    }

    public List<Map<String, Object>> getRows(String tableName) {
        Table table = getExistingTable(tableName);
        List<Column> columns = table.getColumns();
        if (columns.isEmpty()) {
            return List.of();
        }

        int rowCount = columns.get(0).size();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Column column : columns) {
                row.put(column.getName(), column.get(i));
            }
            rows.add(row);
        }
        return rows;
    }

    public QueryResult query(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("Query text is required");
        }
        return new QueryResult("NOT_IMPLEMENTED", "Query engine not implemented yet.", List.of());
    }

    private Table getExistingTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        Table table = tablesByName.get(tableName.trim());
        if (table == null) {
            throw new IllegalArgumentException("Table not found: " + tableName);
        }
        return table;
    }

    private LinkedHashMap<String, String> validateAndBuildTypeMap(List<ColumnDef> columns) {
        LinkedHashMap<String, String> types = new LinkedHashMap<>();
        for (ColumnDef column : columns) {
            if (column == null || column.name == null || column.name.isBlank()) {
                throw new IllegalArgumentException("Column name is required");
            }
            if (column.type == null || column.type.isBlank()) {
                throw new IllegalArgumentException("Column type is required for column: " + column.name);
            }

            String normalizedName = column.name.trim();
            String normalizedType = column.type.trim().toUpperCase(Locale.ROOT);
            if (!List.of("STRING", "INT", "LONG", "DOUBLE", "BOOLEAN").contains(normalizedType)) {
                throw new IllegalArgumentException("Unsupported type: " + column.type);
            }
            if (types.containsKey(normalizedName)) {
                throw new IllegalArgumentException("Column names must be unique");
            }
            types.put(normalizedName, normalizedType);
        }
        return types;
    }

    private boolean isValueCompatible(String type, Object value) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case "STRING" -> value instanceof String;
            case "INT" -> value instanceof Integer;
            case "LONG" -> value instanceof Integer || value instanceof Long;
            case "DOUBLE" -> value instanceof Integer || value instanceof Long || value instanceof Double;
            case "BOOLEAN" -> value instanceof Boolean;
            default -> false;
        };
    }

    private TableSchema toSchema(String tableName) {
        LinkedHashMap<String, String> types = columnTypesByTable.get(tableName);
        List<ColumnDef> columns = types.entrySet().stream()
                .map(entry -> new ColumnDef(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return new TableSchema(tableName, columns);
    }

    public static class ColumnDef {
        public String name;
        public String type;

        public ColumnDef() {
        }

        public ColumnDef(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class TableSchema {
        public String tableName;
        public List<ColumnDef> columns;

        public TableSchema() {
        }

        public TableSchema(String tableName, List<ColumnDef> columns) {
            this.tableName = tableName;
            this.columns = columns;
        }
    }

    public static class QueryResult {
        public String status;
        public String message;
        public List<Map<String, Object>> rows;

        public QueryResult() {
        }

        public QueryResult(String status, String message, List<Map<String, Object>> rows) {
            this.status = status;
            this.message = message;
            this.rows = rows;
        }
    }
}
