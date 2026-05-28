package com.example.engine.storage;

import java.nio.file.Path;

public class DiskSegment {
    private final Path file;
    private final long rowCount;

    public DiskSegment(Path file, long rowCount) {
        this.file = file;
        this.rowCount = rowCount;
    }

    public Path getFile() {
        return file;
    }

    public long getRowCount() {
        return rowCount;
    }
}
