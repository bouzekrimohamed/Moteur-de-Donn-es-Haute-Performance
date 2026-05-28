package com.example.engine.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import java.nio.file.*;

public class Table {
    private final String name;
    private final List<Column> columns;

    /** Seuil de bascule mémoire -> disque (en nombre de lignes). */
    public static final int DEFAULT_MAX_ROWS_IN_MEMORY = 2_000_000;

    /**
     * Intervalle de reset de l'ObjectOutputStream pendant le spill.
     *
     * <p>{@link java.io.ObjectOutputStream} maintient une table interne de tous
     * les objets sérialisés pour pouvoir écrire des back-références (éviter les
     * doublons). Sans reset, cette table grossit jusqu'à couvrir la totalité du
     * segment (ex. 2M lignes × 6 colonnes = 12M entrées), et l'
     * {@link java.io.ObjectInputStream} côté lecture doit faire de même.
     * Sur de grands segments lus en parallèle, cela provoque un OOM.
     *
     * <p>Un appel à {@code out.reset()} écrit un token {@code TC_RESET} dans le
     * flux ; l'ObjectInputStream le lit et vide sa propre table. La table est
     * ainsi bornée à {@code SPILL_RESET_INTERVAL × nb_colonnes} entrées, quelle
     * que soit la taille du segment.
     */
    public static final int SPILL_RESET_INTERVAL = 1_000;

    /**
     * Rendu non statique + modifiable pour pouvoir l'abaisser dans les tests
     * (impossible de tester un spill avec 2 millions de lignes en CI).
     */
    private int maxRowsInMemory = DEFAULT_MAX_ROWS_IN_MEMORY;

    /** Segments écrits sur le disque (données "froides"). */
    private final List<DiskSegment> diskSegments = new ArrayList<>();

    /** Nombre total de lignes déjà déversées sur disque (somme des segments). */
    private long diskRowCount = 0;

    public Table(String name) {
        this.name = name;
        this.columns = new ArrayList<>();
    }

    public Table(String name, List<Column> columns) {
        this.name = name;
        this.columns = new ArrayList<>(columns);
    }

    /** Réglage du seuil (utilisé surtout pour les tests). */
    public void setMaxRowsInMemory(int maxRowsInMemory) {
        if (maxRowsInMemory < 1) throw new IllegalArgumentException("Seuil >= 1 requis");
        this.maxRowsInMemory = maxRowsInMemory;
    }

    public int getMaxRowsInMemory() { return maxRowsInMemory; }

    /** Ajoute une colonne vide */
    public boolean addColumn(String name, String type) {
        columns.add(new Column(name, type));
        return true;
    }

    /** Ajoute une valeur dans la colonne nommée (déclenche le spill si besoin) */
    public boolean addToColumn(String columnName, Object value) {
        for (Column c : columns) {
            if (c.getName().equals(columnName)) {
                boolean result = c.add(value);
                checkSpillToDisk();
                return result;
            }
        }
        return false;
    }

    /** Supprime une colonne par nom */
    public boolean removeColumn(String name) {
        return columns.removeIf(c -> c.getName().equals(name));
    }

    /** Lignes actuellement détenues en RAM (= taille de la 1re colonne). */
    public int memoryRowCount() {
        if (columns.isEmpty()) return 0;
        return columns.get(0).size();
    }

    /** Total logique = lignes en mémoire + lignes sur disque. */
    public long totalRowCount() {
        return memoryRowCount() + diskRowCount;
    }

    public Column getColumn(String name) {
        for (Column c : columns) {
            if (c.getName().equals(name)) return c;
        }
        throw new IllegalArgumentException("Colonne inconnue : " + name);
    }

    /** Noms des colonnes dans l'ordre (le schéma survit aux spills). */
    public List<String> getColumnNames() {
        List<String> names = new ArrayList<>(columns.size());
        for (Column c : columns) names.add(c.getName());
        return names;
    }

    public boolean hasColumn(String name) {
        for (Column c : columns) if (c.getName().equals(name)) return true;
        return false;
    }

    /** Retourne les colonnes (copies défensives) */
    public List<Column> getColumns() {
        List<Column> clones = new ArrayList<>();
        for (Column c : columns) clones.add(c.clone());
        return clones;
    }

    /** Accès direct aux colonnes internes (pour le QueryEngine / loaders) */
    public List<Column> getColumnsInternal() {
        return columns;
    }

    public List<DiskSegment> getDiskSegments() { return diskSegments; }

    /**
     * Ouvre un curseur qui parcourt TOUTES les lignes (RAM puis disque),
     * une par une, sans charger les segments froids en mémoire.
     */
    public RowCursor openCursor() {
        return new CompositeRowCursor(columns, memoryRowCount(), new ArrayList<>(diskSegments));
    }

    /**
     * Sérialise toutes les lignes (RAM + disque) en liste de maps.
     * ATTENTION : matérialise tout en mémoire — à éviter sur de très grosses
     * tables. Préférer une requête avec LIMIT pour ces cas.
     */
    public List<Map<String, Object>> toRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (RowCursor cursor = openCursor()) {
            while (cursor.hasNext()) {
                rows.add(new LinkedHashMap<>(cursor.next().values()));
            }
        }
        return rows;
    }

    // ----------------------------------------------------------------------
    // Bascule mémoire -> disque
    // ----------------------------------------------------------------------

    private void checkSpillToDisk() {
        // Garde rapide : tant qu'on n'a pas atteint le seuil, rien à faire.
        if (memoryRowCount() < maxRowsInMemory) return;

        // On ne déverse que sur une frontière de ligne : toutes les colonnes
        // doivent avoir la même taille (sinon la dernière ligne est incomplète,
        // car l'insertion se fait colonne par colonne).
        if (!allColumnsSameSize()) return;

        spillToDisk();
    }

    private boolean allColumnsSameSize() {
        if (columns.isEmpty()) return true;
        int s = columns.get(0).size();
        for (Column c : columns) if (c.size() != s) return false;
        return true;
    }

    private void spillToDisk() {
        try {
            Path tempFile = Files.createTempFile("sgbd-" + name + "-", ".bin");
            tempFile.toFile().deleteOnExit();

            int rows = memoryRowCount();

            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempFile)))) {

                out.writeInt(rows);
                out.writeInt(columns.size());
                for (Column column : columns) {
                    out.writeUTF(column.getName());
                    out.writeUTF(column.getType());
                }
                for (int row = 0; row < rows; row++) {
                    for (Column column : columns) {
                        out.writeObject(column.get(row));
                    }
                    // Reset périodique : vide la table de handles interne de l'OOS.
                    // L'OIS lira le token TC_RESET et videra la sienne en miroir,
                    // bornant la consommation mémoire à SPILL_RESET_INTERVAL × nb_colonnes
                    // objets au lieu de toute la taille du segment.
                    if ((row + 1) % SPILL_RESET_INTERVAL == 0) {
                        out.reset();
                    }
                }
            }

            diskSegments.add(new DiskSegment(tempFile, rows));
            diskRowCount += rows;
            clearMemoryData();

        } catch (IOException e) {
            throw new RuntimeException("Echec du spill vers le disque", e);
        }
    }

    private void clearMemoryData() {
        for (Column column : columns) column.clear();
    }

    /**
     * Supprime immédiatement tous les fichiers de segments de cette table.
     * Appelé lors d'un DROP TABLE et à l'arrêt du serveur.
     */
    public void cleanupDiskSegments() {
        for (DiskSegment seg : diskSegments) {
            try {
                Files.deleteIfExists(seg.getFile());
            } catch (IOException ignored) {
                // Si la suppression échoue, deleteOnExit prendra le relais.
            }
        }
        diskSegments.clear();
        diskRowCount = 0;
    }

    public String getName() { return name; }
}
