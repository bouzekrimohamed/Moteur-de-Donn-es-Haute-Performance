package com.example.engine.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Table {
    private final String name;
    private final List<Column> columns;

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
                return c.add(value);
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

    public String getName() { return name; }
}
