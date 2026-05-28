package com.example.engine.core;

import com.example.engine.storage.Column;
import com.example.engine.storage.DiskRowCursor;
import com.example.engine.storage.DiskSegment;
import com.example.engine.storage.MemoryRowCursor;
import com.example.engine.storage.Row;
import com.example.engine.storage.RowCursor;
import com.example.engine.storage.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;

/**
 * Moteur de requêtes en STREAMING.
 *
 * Parcourt la table via un {@link RowCursor} (RAM puis segments disque)
 * ligne par ligne, en un seul passage, sans jamais recharger les données
 * froides en intégralité.
 *
 * Quand la table possède plusieurs segments (disque et/ou mémoire), le moteur
 * bascule automatiquement en mode <b>parallèle</b> : chaque segment est traité
 * par un thread indépendant dans un {@link ForkJoinPool} à work-stealing.
 * Les résultats partiels sont fusionnés dans le thread principal.
 *
 * Supporte : SELECT, WHERE (=, !=, <, >, <=, >=, CONTAINS), GROUP BY,
 *            agrégations (COUNT, SUM, AVG, MIN, MAX), ORDER BY, LIMIT.
 */
public class QueryEngine {

    // ── Constantes ────────────────────────────────────────────────────────────

    /**
     * Cap défensif appliqué quand ORDER BY est utilisé sans LIMIT.
     * Sans borne, le moteur chargerait toute la table avant de trier.
     */
    private static final int DEFAULT_ORDER_BY_LIMIT = 10_000;

    /**
     * Nombre minimum de tâches pour activer le chemin parallèle.
     * En dessous de ce seuil l'overhead de soumission dépasse le gain.
     */
    private static final int MIN_PARALLEL_TASKS = 2;

    /**
     * Taille d'un chunk de la portion mémoire assigné à un seul thread.
     * ArrayList.get(i) est thread-safe en lecture pure ; on découpe donc la
     * portion mémoire en tranches de cette taille sans copie de données.
     */
    private static final int MEMORY_CHUNK_SIZE = 100_000;

    /**
     * Pool de threads à work-stealing dédié au moteur de requêtes.
     * Un pool dédié (plutôt que le commonPool) évite toute interférence avec
     * les workers Quarkus/Netty et rend les performances reproductibles.
     */
    private static final ExecutorService POOL = Executors.newWorkStealingPool();

    // ── API publique ──────────────────────────────────────────────────────────

    public List<Map<String, Object>> query(
            Table table,
            List<String> select,
            WhereClause where,
            String groupBy,
            String orderBy,
            boolean orderAsc,
            int limit) {

        // Validation du schéma AVANT de lire (messages d'erreur clairs).
        validate(table, select, where, groupBy);

        // ORDER BY sans LIMIT : cap défensif pour éviter un chargement intégral.
        if (orderBy != null && limit < 0) {
            limit = DEFAULT_ORDER_BY_LIMIT;
        }

        boolean parallel = shouldParallelize(table);

        List<Map<String, Object>> result;
        if (groupBy != null) {
            result = parallel
                    ? streamGroupByParallel(table, where, select, groupBy)
                    : streamGroupBy(table, where, select, groupBy);
        } else {
            result = parallel
                    ? streamSelectParallel(table, where, select, orderBy, orderAsc, limit)
                    : streamSelect(table, where, select, orderBy, orderAsc, limit);
        }

        // ORDER BY sur le jeu de résultats matérialisé.
        if (orderBy != null) {
            applyOrderBy(result, orderBy, orderAsc);
        }

        // LIMIT final.
        if (limit >= 0 && result.size() > limit) {
            result = new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    // ── Décision de parallélisation ───────────────────────────────────────────

    private boolean shouldParallelize(Table table) {
        return countTasks(table) >= MIN_PARALLEL_TASKS;
    }

    /**
     * Calcule le nombre de tâches qui seraient créées pour cette table :
     * une par segment disque + une par chunk de la portion mémoire.
     */
    private int countTasks(Table table) {
        int tasks = table.getDiskSegments().size();
        int memRows = table.memoryRowCount();
        if (memRows > 0) {
            tasks += Math.max(1, (memRows + MEMORY_CHUNK_SIZE - 1) / MEMORY_CHUNK_SIZE);
        }
        return tasks;
    }

    // ── Scan d'un curseur (cœur partagé séquentiel / parallèle) ──────────────

    /**
     * Filtre, projette et retourne les lignes produites par {@code cursor}.
     * Applique le heap borné si ORDER BY + LIMIT sont tous les deux présents.
     * Le curseur doit être fermé par l'appelant (try-with-resources).
     */
    private List<Map<String, Object>> scanCursor(
            RowCursor cursor,
            WhereClause where,
            List<String> select,
            String orderBy,
            boolean orderAsc,
            int limit) {

        boolean selectAll   = (select == null || select.isEmpty());
        boolean canEarlyStop = (orderBy == null && limit >= 0);

        // Heap borné : O(limit) mémoire au lieu de O(n).
        //  – ORDER BY col DESC LIMIT K → min-heap de K (expulse le plus petit)
        //  – ORDER BY col ASC  LIMIT K → max-heap de K (expulse le plus grand)
        boolean useBoundedHeap = (orderBy != null && limit >= 0);
        PriorityQueue<Map<String, Object>> heap = null;

        if (useBoundedHeap) {
            final String sortCol = orderBy;
            Comparator<Map<String, Object>> ascending = (a, b) -> {
                Object va = a.get(sortCol);
                Object vb = b.get(sortCol);
                if (va == null && vb == null) return 0;
                if (va == null) return -1;
                if (vb == null) return  1;
                try {
                    return Double.compare(
                            Double.parseDouble(va.toString()),
                            Double.parseDouble(vb.toString()));
                } catch (NumberFormatException e) {
                    return va.toString().compareTo(vb.toString());
                }
            };
            Comparator<Map<String, Object>> heapOrder =
                    orderAsc ? ascending.reversed() : ascending;
            heap = new PriorityQueue<>(Math.max(1, limit), heapOrder);
        }

        List<Map<String, Object>> result = new ArrayList<>();

        while (cursor.hasNext()) {
            Row row = cursor.next();
            if (!passesWhere(row, where)) continue;

            Map<String, Object> out = project(row, select, selectAll);

            if (useBoundedHeap) {
                heap.offer(out);
                if (heap.size() > limit) heap.poll();
            } else {
                result.add(out);
                if (canEarlyStop && result.size() >= limit) break;
            }
        }

        if (useBoundedHeap) result.addAll(heap);
        return result;
    }

    // ── SELECT séquentiel ─────────────────────────────────────────────────────

    private List<Map<String, Object>> streamSelect(
            Table table, WhereClause where, List<String> select,
            String orderBy, boolean orderAsc, int limit) {

        try (RowCursor cursor = table.openCursor()) {
            return scanCursor(cursor, where, select, orderBy, orderAsc, limit);
        }
    }

    // ── SELECT parallèle ──────────────────────────────────────────────────────

    /**
     * Distribue le scan de la table sur le pool à work-stealing :
     * <ul>
     *   <li>Une tâche par segment disque (chaque DiskRowCursor est indépendant).</li>
     *   <li>La portion mémoire est découpée en chunks de {@link #MEMORY_CHUNK_SIZE}
     *       lignes (ArrayList.get est thread-safe en lecture).</li>
     * </ul>
     * Chaque tâche retourne son propre heap partiel (au plus {@code limit} lignes).
     * Le thread principal concatène les résultats partiels ; {@code applyOrderBy}
     * + le LIMIT final sont appliqués ensuite dans {@link #query}.
     */
    private List<Map<String, Object>> streamSelectParallel(
            Table table, WhereClause where, List<String> select,
            String orderBy, boolean orderAsc, int limit) {

        List<Column>      cols     = table.getColumnsInternal();
        List<DiskSegment> segments = new ArrayList<>(table.getDiskSegments());
        int               memRows  = table.memoryRowCount();

        List<Callable<List<Map<String, Object>>>> tasks = new ArrayList<>();

        // Tâches disque
        for (DiskSegment seg : segments) {
            tasks.add(() -> {
                try (RowCursor c = new DiskRowCursor(seg.getFile())) {
                    return scanCursor(c, where, select, orderBy, orderAsc, limit);
                }
            });
        }

        // Tâches mémoire (chunks)
        for (int start = 0; start < memRows; start += MEMORY_CHUNK_SIZE) {
            final int s = start;
            final int e = Math.min(start + MEMORY_CHUNK_SIZE, memRows);
            tasks.add(() -> {
                try (RowCursor c = new MemoryRowCursor(cols, s, e)) {
                    return scanCursor(c, where, select, orderBy, orderAsc, limit);
                }
            });
        }

        return mergeLists(invokeTasks(tasks));
    }

    // ── GROUP BY séquentiel ───────────────────────────────────────────────────

    private List<Map<String, Object>> streamGroupBy(
            Table table, WhereClause where, List<String> select, String groupByCol) {

        List<AggSpec> aggs = parseAggregations(select);

        try (RowCursor cursor = table.openCursor()) {
            Map<Object, GroupAcc> groups = scanGroupBy(cursor, where, groupByCol, aggs);
            return groupAccToResult(groups, groupByCol, aggs);
        }
    }

    // ── GROUP BY parallèle ────────────────────────────────────────────────────

    /**
     * Chaque tâche produit un {@code Map<key, GroupAcc>} partiel.
     * La fusion finale additionne les counts / sums, et prend le min/max global.
     */
    private List<Map<String, Object>> streamGroupByParallel(
            Table table, WhereClause where, List<String> select, String groupByCol) {

        List<AggSpec>     aggs     = parseAggregations(select);
        List<Column>      cols     = table.getColumnsInternal();
        List<DiskSegment> segments = new ArrayList<>(table.getDiskSegments());
        int               memRows  = table.memoryRowCount();

        List<Callable<Map<Object, GroupAcc>>> tasks = new ArrayList<>();

        // Tâches disque
        for (DiskSegment seg : segments) {
            tasks.add(() -> {
                try (RowCursor c = new DiskRowCursor(seg.getFile())) {
                    return scanGroupBy(c, where, groupByCol, aggs);
                }
            });
        }

        // Tâches mémoire (chunks)
        for (int start = 0; start < memRows; start += MEMORY_CHUNK_SIZE) {
            final int s = start;
            final int e = Math.min(start + MEMORY_CHUNK_SIZE, memRows);
            tasks.add(() -> {
                try (RowCursor c = new MemoryRowCursor(cols, s, e)) {
                    return scanGroupBy(c, where, groupByCol, aggs);
                }
            });
        }

        // Fusion des accumulateurs partiels
        Map<Object, GroupAcc> merged = new LinkedHashMap<>();
        for (Map<Object, GroupAcc> partial : invokeTasks(tasks)) {
            partial.forEach((key, acc) ->
                    merged.merge(key, acc, GroupAcc::mergeWith));
        }

        return groupAccToResult(merged, groupByCol, aggs);
    }

    // ── Helpers GROUP BY ──────────────────────────────────────────────────────

    /** Parcourt un curseur et accumule les groupes dans une map locale. */
    private Map<Object, GroupAcc> scanGroupBy(
            RowCursor cursor, WhereClause where,
            String groupByCol, List<AggSpec> aggs) {

        Map<Object, GroupAcc> groups = new LinkedHashMap<>();

        while (cursor.hasNext()) {
            Row row = cursor.next();
            if (!passesWhere(row, where)) continue;

            Object  key = row.get(groupByCol);
            GroupAcc acc = groups.computeIfAbsent(key, k -> new GroupAcc(aggs));
            acc.count++;

            for (AggSpec spec : aggs) {
                Object v = row.get(spec.column);
                if (v != null) {
                    try { acc.update(spec, Double.parseDouble(v.toString())); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return groups;
    }

    /** Convertit la map d'accumulateurs en liste de lignes résultats. */
    private List<Map<String, Object>> groupAccToResult(
            Map<Object, GroupAcc> groups, String groupByCol, List<AggSpec> aggs) {

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Object, GroupAcc> e : groups.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(groupByCol, e.getKey());
            row.put("count", e.getValue().count);
            for (AggSpec spec : aggs) row.put(spec.raw, e.getValue().result(spec));
            result.add(row);
        }
        return result;
    }

    // ── Helpers parallélisme ──────────────────────────────────────────────────

    /**
     * Soumet les tâches au pool, attend leur fin, et retourne la liste
     * des résultats dans l'ordre de soumission.
     * Propage les exceptions en RuntimeException.
     */
    private <T> List<T> invokeTasks(List<Callable<T>> tasks) {
        List<T> results = new ArrayList<>(tasks.size());
        try {
            for (Future<T> f : POOL.invokeAll(tasks)) {
                results.add(f.get());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Requête parallèle interrompue", ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Erreur dans une tâche parallèle", ex.getCause());
        }
        return results;
    }

    /** Concatène les listes partielles en une seule liste. */
    private <T> List<T> mergeLists(List<List<T>> partials) {
        List<T> merged = new ArrayList<>();
        for (List<T> p : partials) merged.addAll(p);
        return merged;
    }

    // ── Projection d'une ligne ────────────────────────────────────────────────

    private Map<String, Object> project(Row row, List<String> select, boolean selectAll) {
        if (selectAll) return new LinkedHashMap<>(row.values());
        Map<String, Object> out = new LinkedHashMap<>();
        for (String col : select) out.put(col, row.get(col));
        return out;
    }

    // ── WHERE (évaluation ligne par ligne) ────────────────────────────────────

    private boolean passesWhere(Row row, WhereClause where) {
        if (where == null) return true;
        return matches(row.get(where.column), where.operator, where.value);
    }

    private boolean matches(Object cellValue, String operator, Object filterValue) {
        if (cellValue == null) return false;
        switch (operator.toUpperCase()) {
            case "=":
            case "==":
                return cellValue.toString().equals(filterValue.toString());
            case "!=":
            case "<>":
                return !cellValue.toString().equals(filterValue.toString());
            case "CONTAINS":
                return cellValue.toString().contains(filterValue.toString());
            default:
                try {
                    double cell   = Double.parseDouble(cellValue.toString());
                    double filter = Double.parseDouble(filterValue.toString());
                    return switch (operator) {
                        case "<"  -> cell < filter;
                        case ">"  -> cell > filter;
                        case "<=" -> cell <= filter;
                        case ">=" -> cell >= filter;
                        default   -> false;
                    };
                } catch (NumberFormatException e) {
                    return false;
                }
        }
    }

    // ── ORDER BY (tri du jeu de résultats matérialisé) ────────────────────────

    private void applyOrderBy(List<Map<String, Object>> rows, String col, boolean asc) {
        rows.sort((a, b) -> {
            Object va = a.get(col);
            Object vb = b.get(col);
            if (va == null && vb == null) return 0;
            if (va == null) return asc ? -1 : 1;
            if (vb == null) return asc ? 1 : -1;
            int cmp;
            try {
                double da = Double.parseDouble(va.toString());
                double db = Double.parseDouble(vb.toString());
                cmp = Double.compare(da, db);
            } catch (NumberFormatException e) {
                cmp = va.toString().compareTo(vb.toString());
            }
            return asc ? cmp : -cmp;
        });
    }

    // ── Validation du schéma ──────────────────────────────────────────────────

    private void validate(Table table, List<String> select, WhereClause where, String groupBy) {
        if (where != null && !table.hasColumn(where.column))
            throw new IllegalArgumentException("Colonne inconnue : " + where.column);
        if (groupBy != null && !table.hasColumn(groupBy))
            throw new IllegalArgumentException("Colonne inconnue : " + groupBy);

        if (select != null) {
            for (String sel : select) {
                AggSpec spec = AggSpec.tryParse(sel);
                if (spec != null) {
                    if (!table.hasColumn(spec.column))
                        throw new IllegalArgumentException("Colonne inconnue : " + spec.column);
                } else if (groupBy == null && !table.hasColumn(sel)) {
                    throw new IllegalArgumentException("Colonne inconnue : " + sel);
                }
            }
        }
    }

    private List<AggSpec> parseAggregations(List<String> select) {
        List<AggSpec> aggs = new ArrayList<>();
        if (select == null) return aggs;
        for (String sel : select) {
            AggSpec spec = AggSpec.tryParse(sel);
            if (spec != null) aggs.add(spec);
        }
        return aggs;
    }

    // ── Structures internes ───────────────────────────────────────────────────

    /** Spécification d'une agrégation, ex : "SUM(salaire)". */
    private static class AggSpec {
        final String raw;    // "SUM(salaire)"
        final String func;   // "SUM"
        final String column; // "salaire"

        AggSpec(String raw, String func, String column) {
            this.raw = raw; this.func = func; this.column = column;
        }

        static AggSpec tryParse(String sel) {
            if (sel == null) return null;
            String upper = sel.toUpperCase();
            for (String agg : List.of("SUM", "AVG", "MIN", "MAX")) {
                if (upper.startsWith(agg + "(") && upper.endsWith(")")) {
                    String col = sel.substring(agg.length() + 1, sel.length() - 1).trim();
                    return new AggSpec(sel, agg, col);
                }
            }
            return null;
        }
    }

    /**
     * Accumulateur par groupe.
     * Thread-safe en écriture uniquement si utilisé dans un seul thread à la
     * fois — c'est garanti ici car chaque tâche parallèle a son propre
     * accumulateur local ; la fusion finale est mono-thread.
     */
    private static class GroupAcc {
        long count = 0;
        final Map<String, double[]> sums = new HashMap<>(); // raw → [sum]
        final Map<String, long[]>   nums = new HashMap<>(); // raw → [n valeurs]
        final Map<String, Double>   mins = new HashMap<>();
        final Map<String, Double>   maxs = new HashMap<>();

        GroupAcc(List<AggSpec> aggs) {
            for (AggSpec a : aggs) {
                sums.put(a.raw, new double[]{0});
                nums.put(a.raw, new long[]{0});
            }
        }

        void update(AggSpec spec, double v) {
            sums.get(spec.raw)[0] += v;
            nums.get(spec.raw)[0] += 1;
            mins.merge(spec.raw, v, Math::min);
            maxs.merge(spec.raw, v, Math::max);
        }

        /**
         * Fusionne {@code other} dans {@code this} et retourne {@code this}.
         * Utilisé par le thread principal pour combiner les accumulateurs
         * partiels produits par les différentes tâches parallèles.
         */
        GroupAcc mergeWith(GroupAcc other) {
            this.count += other.count;
            other.sums.forEach((raw, arr) ->
                    this.sums.computeIfAbsent(raw, k -> new double[]{0})[0] += arr[0]);
            other.nums.forEach((raw, arr) ->
                    this.nums.computeIfAbsent(raw, k -> new long[]{0})[0] += arr[0]);
            other.mins.forEach((raw, v) -> this.mins.merge(raw, v, Math::min));
            other.maxs.forEach((raw, v) -> this.maxs.merge(raw, v, Math::max));
            return this;
        }

        Object result(AggSpec spec) {
            long n = nums.get(spec.raw)[0];
            if (n == 0) return null;
            return switch (spec.func) {
                case "SUM" -> round2(sums.get(spec.raw)[0]);
                case "AVG" -> round2(sums.get(spec.raw)[0] / n);
                case "MIN" -> mins.get(spec.raw);
                case "MAX" -> maxs.get(spec.raw);
                default    -> null;
            };
        }

        private static double round2(double v) {
            if (!Double.isFinite(v)) return v;
            return BigDecimal.valueOf(v)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }

    // ── WhereClause — API publique ────────────────────────────────────────────

    public static class WhereClause {
        public String column;
        public String operator;
        public Object value;

        public WhereClause() {}
        public WhereClause(String column, String operator, Object value) {
            this.column   = column;
            this.operator = operator;
            this.value    = value;
        }
    }
}
