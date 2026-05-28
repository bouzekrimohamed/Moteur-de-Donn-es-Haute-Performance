package com.example.engine.core;

import com.example.engine.storage.Column;
import com.example.engine.storage.Table;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Moteur de requêtes en mémoire.
 * Supporte : SELECT, WHERE (=, !=, <, >, <=, >=, CONTAINS), GROUP BY,
 *            fonctions d'agrégation (COUNT, SUM, AVG, MIN, MAX),
 *            ORDER BY, LIMIT.
 */
public class QueryEngine {

    /**
     * Exécute une requête sur une table.
     *
     * @param table    la table source
     * @param select   colonnes à retourner (null ou vide = toutes)
     * @param where    filtre (null = pas de filtre)
     * @param groupBy  colonne de regroupement (null = pas de group by)
     * @param orderBy  colonne de tri (null = pas de tri)
     * @param orderAsc true = ASC, false = DESC
     * @param limit    nombre max de lignes (-1 = pas de limite)
     * @return résultats sous forme de liste de maps
     */
    public List<Map<String, Object>> query(
            Table table,
            List<String> select,
            WhereClause where,
            String groupBy,
            String orderBy,
            boolean orderAsc,
            int limit) {

        // 1. WHERE : indices des lignes qui passent le filtre
        List<Integer> matchingRows = applyWhere(table, where);

        List<Map<String, Object>> result;

        // 2. GROUP BY
        if (groupBy != null) {
            result = applyGroupBy(table, matchingRows, select, groupBy);
            if (orderBy != null) applyOrderBy(result, orderBy, orderAsc);
            if (limit >= 0 && result.size() > limit) result = result.subList(0, limit);
        } else if (orderBy != null && limit >= 0) {
            // Top-N : min-heap sur les indices — O(n log k) sans matérialiser toutes les lignes
            result = applyTopN(table, matchingRows, select, orderBy, orderAsc, limit);
        } else {
            // SELECT simple : tronquer avant matérialisation si pas de tri
            List<Integer> rowsToMaterialize = (limit >= 0 && matchingRows.size() > limit)
                    ? matchingRows.subList(0, limit)
                    : matchingRows;
            result = applySelect(table, rowsToMaterialize, select);
        }

        return result;
    }

    // WHERE — scan parallèle sur les indices de lignes
    private List<Integer> applyWhere(Table table, WhereClause where) {
        int total = table.rowCount();

        if (where == null) {
            return IntStream.range(0, total).boxed().collect(Collectors.toList());
        }

        Column col = table.getColumn(where.column);
        return IntStream.range(0, total)
                .parallel()
                .filter(i -> matches(col.get(i), where.operator, where.value))
                .boxed()
                .collect(Collectors.toList());
    }

    private boolean matches(Object cellValue, String operator, Object filterValue) {
        if (cellValue == null) return false;

        switch (operator.toUpperCase()) {
            case "=":
            case "==":
                // Comparaison directe pour les types numériques — évite toString()
                if (cellValue instanceof Number cn && filterValue instanceof Number fn)
                    return cn.doubleValue() == fn.doubleValue();
                return cellValue.toString().equals(filterValue.toString());
            case "!=":
            case "<>":
                if (cellValue instanceof Number cn && filterValue instanceof Number fn)
                    return cn.doubleValue() != fn.doubleValue();
                return !cellValue.toString().equals(filterValue.toString());
            case "CONTAINS":
                return cellValue.toString().contains(filterValue.toString());
            default:
                // Extraction numérique sans toString() quand la valeur est déjà typée
                double cell, filter;
                if (cellValue instanceof Number cn) {
                    cell = cn.doubleValue();
                } else {
                    try { cell = Double.parseDouble(cellValue.toString()); }
                    catch (NumberFormatException e) { return false; }
                }
                if (filterValue instanceof Number fn) {
                    filter = fn.doubleValue();
                } else {
                    try { filter = Double.parseDouble(filterValue.toString()); }
                    catch (NumberFormatException e) { return false; }
                }
                return switch (operator) {
                    case "<"  -> cell < filter;
                    case ">"  -> cell > filter;
                    case "<=" -> cell <= filter;
                    case ">=" -> cell >= filter;
                    default   -> false;
                };
        }
    }

    // Top-N via min-heap sur indices — évite de matérialiser toutes les lignes
    private List<Map<String, Object>> applyTopN(
            Table table, List<Integer> rows, List<String> select,
            String orderBy, boolean orderAsc, int limit) {

        Column sortCol = table.getColumn(orderBy);

        // Min-heap (taille k) : garde les k plus grands (DESC) ou k plus petits (ASC)
        // Pour DESC : on veut les k plus grands → heap sur le minimum → pop quand plus petit que le sommet
        PriorityQueue<Integer> heap = new PriorityQueue<>(limit + 1, (a, b) -> {
            double va = toDouble(sortCol.get(a));
            double vb = toDouble(sortCol.get(b));
            // Pour DESC on veut éjecter les petits → comparaison normale (min en tête)
            // Pour ASC on veut éjecter les grands → comparaison inversée (max en tête)
            return orderAsc ? Double.compare(vb, va) : Double.compare(va, vb);
        });

        for (int idx : rows) {
            heap.add(idx);
            if (heap.size() > limit) heap.poll();
        }

        // Récupérer et trier dans le bon ordre
        List<Integer> topIndices = new ArrayList<>(heap);
        topIndices.sort((a, b) -> {
            double va = toDouble(sortCol.get(a));
            double vb = toDouble(sortCol.get(b));
            return orderAsc ? Double.compare(va, vb) : Double.compare(vb, va);
        });

        return applySelect(table, topIndices, select);
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    // SELECT (projection)
    private List<Map<String, Object>> applySelect(
            Table table, List<Integer> rows, List<String> select) {

        List<Column> cols = resolveColumns(table, select);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int rowIdx : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Column c : cols) {
                row.put(c.getName(), c.get(rowIdx));
            }
            result.add(row);
        }
        return result;
    }

    // GROUP BY avec agrégations (COUNT, SUM, AVG, MIN, MAX)
    private List<Map<String, Object>> applyGroupBy(
            Table table, List<Integer> rows, List<String> select, String groupByCol) {

        Column groupCol = table.getColumn(groupByCol);

        // Résoudre les agrégations demandées une seule fois
        record AggSpec(String sel, String agg, Column col) {}
        List<AggSpec> aggSpecs = new ArrayList<>();
        if (select != null) {
            for (String sel : select) {
                String upper = sel.toUpperCase();
                for (String agg : List.of("SUM", "AVG", "MIN", "MAX")) {
                    if (upper.startsWith(agg + "(") && upper.endsWith(")")) {
                        String colName = sel.substring(agg.length() + 1, sel.length() - 1).trim();
                        aggSpecs.add(new AggSpec(sel, agg, table.getColumn(colName)));
                    }
                }
            }
        }

        // Accumulateurs par groupe — pas de List<Double>, on accumule directement
        record Acc(long count, double sum, double min, double max) {
            Acc() { this(0, 0.0, Double.MAX_VALUE, -Double.MAX_VALUE); }
            Acc add(double v) { return new Acc(count + 1, sum + v, Math.min(min, v), Math.max(max, v)); }
        }

        // Une Map<groupKey, Acc[]> — un Acc par agrégation
        Map<Object, long[]>   counts = new LinkedHashMap<>();
        Map<Object, double[]> sums   = new LinkedHashMap<>();
        Map<Object, double[]> mins   = new LinkedHashMap<>();
        Map<Object, double[]> maxs   = new LinkedHashMap<>();

        int nAgg = aggSpecs.size();

        for (int rowIdx : rows) {
            Object key = groupCol.get(rowIdx);
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            if (nAgg > 0) {
                double[] s = sums.computeIfAbsent(key, k -> new double[nAgg]);
                double[] mn = mins.computeIfAbsent(key, k -> { double[] a = new double[nAgg]; Arrays.fill(a, Double.MAX_VALUE); return a; });
                double[] mx = maxs.computeIfAbsent(key, k -> { double[] a = new double[nAgg]; Arrays.fill(a, -Double.MAX_VALUE); return a; });
                for (int i = 0; i < nAgg; i++) {
                    Object v = aggSpecs.get(i).col().get(rowIdx);
                    if (v instanceof Number n) {
                        double d = n.doubleValue();
                        s[i] += d;
                        if (d < mn[i]) mn[i] = d;
                        if (d > mx[i]) mx[i] = d;
                    }
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object key : counts.keySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(groupByCol, key);
            long cnt = counts.get(key)[0];
            row.put("count", cnt);
            for (int i = 0; i < nAgg; i++) {
                AggSpec spec = aggSpecs.get(i);
                double[] s  = sums.getOrDefault(key, new double[nAgg]);
                double[] mn = mins.getOrDefault(key, new double[nAgg]);
                double[] mx = maxs.getOrDefault(key, new double[nAgg]);
                Object val = switch (spec.agg()) {
                    case "SUM" -> s[i];
                    case "AVG" -> cnt > 0 ? s[i] / cnt : 0.0;
                    case "MIN" -> mn[i];
                    case "MAX" -> mx[i];
                    default    -> null;
                };
                row.put(spec.sel(), val);
            }
            result.add(row);
        }
        return result;
    }

    // ORDER BY
    @SuppressWarnings("unchecked")
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

    // ---------------------------------------------------------------------
    // Helpers
    private List<Column> resolveColumns(Table table, List<String> select) {
        if (select == null || select.isEmpty()) return table.getColumnsInternal();
        List<Column> cols = new ArrayList<>();
        for (String s : select) cols.add(table.getColumn(s));
        return cols;
    }

    // WhereClause — structure de filtre
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
