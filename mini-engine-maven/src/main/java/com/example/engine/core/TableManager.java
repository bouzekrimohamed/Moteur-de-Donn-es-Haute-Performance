package com.example.engine.core;

import com.example.engine.model.Column;
import com.example.engine.model.CreateTableRequest;
import com.example.engine.model.LoadDataRequest;
import com.example.engine.model.LoadDataResponse;
import com.example.engine.model.QueryRequest;
import com.example.engine.model.QueryResponse;
import com.example.engine.model.Table;
import com.example.engine.storage.SchemaStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TableManager {
    @Inject
    SchemaStore schemaStore;

    public Table createTable(CreateTableRequest req) {
        if (req == null || req.getTableName() == null || req.getTableName().isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }

        if (req.getColumns() == null || req.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Columns are required");
        }

        List<Column> columns = req.getColumns();
        validateColumns(columns);

        String tableName = req.getTableName().trim();
        if (schemaStore.exists(tableName)) {
            throw new IllegalStateException("Table already exists: " + tableName);
        }

        Table table = new Table(tableName, columns);
        schemaStore.save(table);
        return table;
    }

    public List<Table> listTables() {
        return schemaStore.findAll().stream()
                .sorted(Comparator.comparing(Table::getTableName))
                .toList();
    }

    public Table getTable(String tableName) {
        validateTableName(tableName);
        return schemaStore.findByName(tableName.trim())
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));
    }

    public LoadDataResponse loadData(String tableName, LoadDataRequest request) {
        getTable(tableName);
        int acceptedRows = request != null && request.getRows() != null ? request.getRows().size() : 0;
        return new LoadDataResponse(
                "ACCEPTED",
                "Data loading is prepared but ingestion engine is not implemented yet.",
                acceptedRows
        );
    }

    public QueryResponse executeQuery(QueryRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Query text is required");
        }

        return new QueryResponse(
                "NOT_IMPLEMENTED",
                "Query engine foundation is ready, but execution logic is not implemented yet.",
                List.of()
        );
    }

    private void validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
    }

    private void validateColumns(List<Column> columns) {
        for (Column column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("Column definition cannot be null");
            }
            if (column.getName() == null || column.getName().isBlank()) {
                throw new IllegalArgumentException("Column name is required");
            }
            if (column.getType() == null) {
                throw new IllegalArgumentException("Column type is required for column: " + column.getName());
            }
        }

        Set<String> uniqueNames = columns.stream()
                .map(Column::getName)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (uniqueNames.size() != columns.size()) {
            throw new IllegalArgumentException("Column names must be unique inside a table");
        }
    }
}
