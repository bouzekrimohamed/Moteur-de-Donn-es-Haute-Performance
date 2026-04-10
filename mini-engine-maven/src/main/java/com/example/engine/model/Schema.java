package com.example.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Schema {
    private String tableName;
    private List<Column> columns;

    public Schema() {
    }

    public Schema(String tableName, List<Column> columns) {
        this.tableName = tableName;
        this.columns = new ArrayList<>(columns);
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<Column> getColumns() {
        if (columns == null) {
            return List.of();
        }
        return Collections.unmodifiableList(columns);
    }

    public void setColumns(List<Column> columns) {
        this.columns = new ArrayList<>(columns);
    }
}
