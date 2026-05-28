package com.example.engine.storage;

import java.util.Map;

public class Row {
    private final Map<String, Object> values;

    public Row(Map<String, Object> values) {
        this.values = values;
    }

    public Object get(String column) {
        return values.get(column);
    }

    public Map<String, Object> values() {
        return values;
    }
}
