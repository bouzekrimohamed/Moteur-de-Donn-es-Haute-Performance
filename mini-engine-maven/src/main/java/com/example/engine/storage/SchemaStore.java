package com.example.engine.storage;

import com.example.engine.model.Table;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SchemaStore {
    private final Map<String, Table> tablesByName = new ConcurrentHashMap<>();

    public boolean exists(String tableName) {
        return tablesByName.containsKey(tableName);
    }

    public void save(Table table) {
        tablesByName.put(table.getTableName(), table);
    }

    public Optional<Table> findByName(String tableName) {
        return Optional.ofNullable(tablesByName.get(tableName));
    }

    public List<Table> findAll() {
        return new ArrayList<>(tablesByName.values());
    }
}
