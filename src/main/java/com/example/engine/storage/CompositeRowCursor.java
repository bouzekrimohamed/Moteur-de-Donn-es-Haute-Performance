package com.example.engine.storage;

import java.util.Iterator;
import java.util.List;

public class CompositeRowCursor implements RowCursor {

    private enum Source {
        MEMORY,
        DISK,
        FINISHED
    }

    private Source source = Source.MEMORY;

    private RowCursor currentCursor;

    private final List<DiskSegment> segments;
    private final List<Column> columns;
    private final int memoryRowCount;

    private int segmentIndex = 0;

    public CompositeRowCursor(List<Column> columns,
                              int memoryRowCount,
                              List<DiskSegment> segments) {

        this.columns = columns;
        this.memoryRowCount = memoryRowCount;
        this.segments = segments;

        this.currentCursor = new MemoryRowCursor(columns, memoryRowCount);
    }

    @Override
    public boolean hasNext() {

        if (source == Source.MEMORY) {
            if (currentCursor.hasNext()) {
                return true;
            } else {
                moveToNextSource();
            }
        }

        if (source == Source.DISK) {
            if (currentCursor != null && currentCursor.hasNext()) {
                return true;
            } else {
                moveToNextDiskSegment();
                return currentCursor != null && currentCursor.hasNext();
            }
        }

        return false;
    }

    @Override
    public Row next() {
        return currentCursor.next();
    }

    private void moveToNextSource() {

        if (!segments.isEmpty()) {
            source = Source.DISK;
            segmentIndex = 0;
            openNextSegment();
        } else {
            source = Source.FINISHED;
        }
    }

    private void moveToNextDiskSegment() {

        segmentIndex++;

        if (segmentIndex >= segments.size()) {
            source = Source.FINISHED;
            currentCursor = null;
            return;
        }

        openNextSegment();
    }

    private void openNextSegment() {
        try {
            currentCursor = new DiskRowCursor(
                    segments.get(segmentIndex).getFile()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {

        if (currentCursor != null) {
            currentCursor.close();
        }
    }
}