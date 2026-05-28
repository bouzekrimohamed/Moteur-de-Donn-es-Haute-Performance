package com.example.engine.storage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DiskRowCursor implements RowCursor {

    private final ObjectInputStream in;
    private final int rowCount;
    private final int columnCount;

    private final List<String> columnNames = new ArrayList<>();

    private int currentRow = 0;

    public DiskRowCursor(Path file) throws IOException {

        this.in = new ObjectInputStream(
                new BufferedInputStream(
                        Files.newInputStream(file)));

        this.rowCount = in.readInt();
        this.columnCount = in.readInt();

        for (int i = 0; i < columnCount; i++) {
            columnNames.add(in.readUTF());
            in.readUTF(); // type ignoré ici (optionnel)
        }
    }

    @Override
    public boolean hasNext() {
        return currentRow < rowCount;
    }

    @Override
    public Row next() {

        try {
            Map<String, Object> row = new LinkedHashMap<>();

            for (int i = 0; i < columnCount; i++) {
                Object value = in.readObject();
                row.put(columnNames.get(i), value);
            }

            currentRow++;

            return new Row(row);

        } catch (Exception e) {
            throw new RuntimeException("Erreur lecture disque", e);
        }
    }

    @Override
    public void close() {
        try {
            in.close();
        } catch (IOException ignored) {}
    }
}