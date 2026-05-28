package com.example.engine.storage;

import java.util.List;

/**
 * Curseur qui parcourt toutes les lignes d'une table dans l'ORDRE D'INSERTION :
 * d'abord les segments disque (les plus anciens, dans l'ordre où ils ont été
 * déversés), puis les lignes encore présentes en RAM (les plus récentes).
 *
 * Un seul fichier est ouvert à la fois : on ferme le segment courant avant
 * d'ouvrir le suivant (pas de fuite de descripteurs).
 */
public class CompositeRowCursor implements RowCursor {

    private enum Source { DISK, MEMORY, FINISHED }

    private Source source;
    private RowCursor currentCursor;

    private final List<DiskSegment> segments;
    private final List<Column> columns;
    private final int memoryRowCount;

    private int segmentIndex = -1;

    public CompositeRowCursor(List<Column> columns,
                              int memoryRowCount,
                              List<DiskSegment> segments) {
        this.columns = columns;
        this.memoryRowCount = memoryRowCount;
        this.segments = segments;

        // On commence par les segments disque s'il y en a, sinon directement la RAM.
        if (segments.isEmpty()) {
            source = Source.MEMORY;
            currentCursor = new MemoryRowCursor(columns, memoryRowCount);
        } else {
            source = Source.DISK;
            segmentIndex = 0;
            openSegment(segmentIndex);
        }
    }

    @Override
    public boolean hasNext() {
        // Phase disque : on enchaîne les segments, en sautant les vides.
        while (source == Source.DISK) {
            if (currentCursor != null && currentCursor.hasNext()) return true;
            advanceDiskSegment();
        }
        // Phase mémoire
        if (source == Source.MEMORY) {
            if (currentCursor.hasNext()) return true;
            closeCurrent();
            currentCursor = null;
            source = Source.FINISHED;
        }
        return false;
    }

    @Override
    public Row next() {
        if (currentCursor == null || !currentCursor.hasNext()) {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException("Plus aucune ligne");
            }
        }
        return currentCursor.next();
    }

    /** Passe au segment disque suivant, ou bascule sur la RAM si terminé. */
    private void advanceDiskSegment() {
        segmentIndex++;
        if (segmentIndex >= segments.size()) {
            // Fin du disque -> on lit ce qui reste en mémoire.
            closeCurrent();
            source = Source.MEMORY;
            currentCursor = new MemoryRowCursor(columns, memoryRowCount);
        } else {
            openSegment(segmentIndex);
        }
    }

    private void openSegment(int index) {
        closeCurrent(); // évite la fuite de descripteurs entre segments
        try {
            currentCursor = new DiskRowCursor(segments.get(index).getFile());
        } catch (Exception e) {
            throw new RuntimeException("Echec ouverture segment disque", e);
        }
    }

    private void closeCurrent() {
        if (currentCursor != null) {
            try { currentCursor.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void close() {
        closeCurrent();
        currentCursor = null;
        source = Source.FINISHED;
    }
}
