package com.example.engine.storage;

public interface RowCursor extends AutoCloseable {
    boolean hasNext();

    Row next();

    @Override void close();
}
