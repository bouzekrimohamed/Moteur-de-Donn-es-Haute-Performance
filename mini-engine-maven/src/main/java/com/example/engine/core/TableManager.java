package com.example.engine.core;

import com.example.engine.model.CreateTableRequest;
import com.example.engine.model.Schema;
import com.example.engine.storage.SchemaStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class TableManager {

    private final Path dbRoot = Path.of("data", "db");

    @Inject
    SchemaStore schemaStore;

    public void createTable(CreateTableRequest req) throws IOException {
        // validations (minimum)
        if (req == null || req.name == null || req.name.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }
        if (req.columns == null || req.columns.isEmpty()) {
            throw new IllegalArgumentException("Columns are required");
        }

        Path tableDir = dbRoot.resolve(req.name);

        if (Files.exists(tableDir)) {
            throw new IllegalArgumentException("Table already exists: " + req.name);
        }

        // create folders
        Files.createDirectories(tableDir.resolve("cols"));

        // save schema
        Schema schema = new Schema(req.name, req.columns);
        schemaStore.save(tableDir, schema);
    }
}
