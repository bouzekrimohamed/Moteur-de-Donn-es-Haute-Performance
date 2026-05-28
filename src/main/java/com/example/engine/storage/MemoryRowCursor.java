package com.example.engine.storage;

import java.util.List;
import java.util.Map;

public class MemoryRowCursor implements RowCursor {

    private final List<Column> columns;
    private final int endRow;   // exclusif

    private int currentRow;

    /** Parcourt toutes les lignes (0 à rowCount). */
    public MemoryRowCursor(List<Column> columns, int rowCount) {
        this(columns, 0, rowCount);
    }

    /**
     * Parcourt la tranche [startRow, endRow) — permet d'assigner
     * une portion de la mémoire à un thread différent.
     */
    public MemoryRowCursor(List<Column> columns, int startRow, int endRow) {
        this.columns    = columns;
        this.currentRow = startRow;
        this.endRow     = endRow;
    }

    @Override
    public boolean hasNext() {
        return currentRow < endRow;
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
