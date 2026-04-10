package com.example.engine.storage;

import com.example.engine.model.Schema;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SchemaStore {
    private final Map<String, Schema> schemasByTableName = new ConcurrentHashMap<>();

    public boolean exists(String tableName) {
        return schemasByTableName.containsKey(tableName);
    }

    public void save(Schema schema) {
        schemasByTableName.put(schema.getTableName(), schema);
    }

    public Optional<Schema> findByName(String tableName) {
        return Optional.ofNullable(schemasByTableName.get(tableName));
    }

    public List<Schema> findAll() {
        return new ArrayList<>(schemasByTableName.values());
    }
}
