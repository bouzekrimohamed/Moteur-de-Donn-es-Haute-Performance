package com.example.engine.storage;

import com.example.engine.model.Schema;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class SchemaStore {

    private final ObjectMapper mapper = new ObjectMapper();

    public void save(Path tableDir, Schema schema) throws IOException {
        Files.createDirectories(tableDir);
        Path schemaPath = tableDir.resolve("schema.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(schemaPath.toFile(), schema);
    }
}
