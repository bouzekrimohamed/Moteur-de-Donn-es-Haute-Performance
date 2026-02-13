package com.example.engine.model;

import java.util.List;

public class Schema {
    public String tableName;
    public List<ColumnDef> columns;

    public Schema() {}

    public Schema(String tableName, List<ColumnDef> columns) {
        this.tableName = tableName;
        this.columns=columns;
    }
}
