package com.example.engine.model;

import java.util.List;

public class CreateTableRequest {
    private String tableName;
    private List<Column> columns;

    public CreateTableRequest() {
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public void setColumns(List<Column> columns) {
        this.columns = columns;
    }
}
