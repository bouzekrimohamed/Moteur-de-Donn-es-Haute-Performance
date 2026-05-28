package com.example.engine.storage;

import java.util.List;
import java.util.Map;

public class MemoryRowCursor implements RowCursor {

    private final List<Column> columns;
    private final int rowCount;

    private int currentRow = 0;

    public MemoryRowCursor(List<Column> columns, int rowCount) {
        this.columns = columns;
        this.rowCount = rowCount;
    }

    @Override
    public boolean hasNext() {
        return currentRow < rowCount;
    }

    @Override
    public Row next() {

        Map<String, Object> row = new java.util.LinkedHashMap<>();

        for (Column column : columns) {
            row.put(column.getName(), column.get(currentRow));
        }

        currentRow++;

        return new Row(row);
    }

    @Override
    public void close() {
    }
}
