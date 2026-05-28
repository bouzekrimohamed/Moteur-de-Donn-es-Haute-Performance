package com.example.engine.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.nio.file.*;

public class Table {
    private final String name;
    private final List<Column> columns;
    private static final int MAX_ROWS_IN_MEMORY = 2_000_000;
    private final List<DiskSegment> diskSegments = new ArrayList<>();

    public Table(String name) {
        this.name = name;
        this.columns = new ArrayList<>();
    }

    public Table(String name, List<Column> columns) {
        this.name = name;
        this.columns = new ArrayList<>(columns);
    }

    /** Ajoute une colonne vide */
    public boolean addColumn(String name, String type) {
        columns.add(new Column(name, type));
        return true;
    }

    /** Ajoute une valeur dans la colonne nommée */
    public boolean addToColumn(String columnName, Object value) {
        for (Column c : columns) {
            if (c.getName().equals(columnName)) {
                boolean result = c.add(value);
                checkSpillToDisk();
                return result;
            }
        }
        return false;
    }

    /** Supprime une colonne par nom */
    public boolean removeColumn(String name) {
        return columns.removeIf(c -> c.getName().equals(name));
    }

    public int rowCount() {
        if (columns.isEmpty()) return 0;
        return columns.get(0).size();
    }

    public Column getColumn(String name) {
        for (Column c : columns) {
            if (c.getName().equals(name)) return c;
        }
        throw new IllegalArgumentException("Colonne inconnue : " + name);
    }

    /** Retourne les colonnes (copies défensives) */
    public List<Column> getColumns() {
        List<Column> clones = new ArrayList<>();
        for (Column c : columns) clones.add(c.clone());
        return clones;
    }

    /** Accès direct aux colonnes internes (pour le QueryEngine) */
    public List<Column> getColumnsInternal() {
        return columns;
    }

    /** Sérialise toutes les lignes en liste de maps (pour JSON) */
    public List<Map<String, Object>> toRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        int total = rowCount();
        for (int i = 0; i < total; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Column c : columns) {
                row.put(c.getName(), c.get(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private void spillToDisk() {
        try {
            Path tempFile = Files.createTempFile( "sgbd-" + name + "-", ".bin" );
            tempFile.toFile().deleteOnExit();
            try (ObjectOutputStream out = new ObjectOutputStream( new BufferedOutputStream( Files.newOutputStream(tempFile)))) {
                int rows = rowCount();
                out.writeInt(rows);
                out.writeInt(columns.size());
                for (Column column : columns) {
                    out.writeUTF(column.getName());
                    out.writeUTF(column.getType());
                }
                for (int row = 0; row < rows; row++) {
                    for (Column column : columns) {
                        out.writeObject(column.get(row));
                    }
                }
            }
            diskSegments.add( new DiskSegment(tempFile, rowCount()) );
            clearMemoryData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void clearMemoryData() { for (Column column : columns) { column.clear(); } }

    public List<DiskSegment> getDiskSegments() { return diskSegments; }

    private void checkSpillToDisk() {
        if (rowCount() >= MAX_ROWS_IN_MEMORY) {
            spillToDisk();
        }
    }

    public String getName() { return name; }
}
