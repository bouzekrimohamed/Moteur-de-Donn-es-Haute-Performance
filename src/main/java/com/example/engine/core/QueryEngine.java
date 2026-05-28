package com.example.engine.core;

import com.example.engine.storage.Column;
import com.example.engine.storage.Table;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Moteur de requêtes en mémoire — résultat colonnaire natif.
 *
 * Retourne un QueryResult colonnaire : Map<colonne, List<valeurs>>.
 * Zéro Map allouée par ligne — cohérent avec le stockage Column[].
 *
 * Supporte : SELECT, WHERE (=, !=, <, >, <=, >=, CONTAINS), GROUP BY,
 *            fonctions d'agrégation (COUNT, SUM, AVG, MIN, MAX),
 *            ORDER BY, LIMIT.
 */
public class QueryEngine {

    // -------------------------------------------------------------------------
    // QueryResult — résultat colonnaire
    // -------------------------------------------------------------------------

    /**
     * Résultat colonnaire : une List<Object> par colonne.
     *
     * Avant : N Maps row-oriented  → N×M Map.Entry allouées pour rien.
     * Après : M Lists columnar     → un seul traversal, zéro conversion.
     *
     * Exemple JSON résultant :
     * {
     *   "columns": {
     *     "payment_type":      [1,        2,       3     ],
     *     "count":             [4585922,  653524,  38490 ],
     *     "SUM(total_amount)": [131910597,14624511,431164]
     *   }
     * }
     */
    public record QueryResult(List<String> columns, Map<String, List<Object>> data, int totalRows) {

        /** Tronque à k lignes — utilisé pour LIMIT */
        public QueryResult truncate(int k) {
            int n = Math.min(k, totalRows);
            Map<String, List<Object>> cut = new LinkedHashMap<>();
            for (String col : columns)
                cut.put(col, new ArrayList<>(data.get(col).subList(0, n)));
            return new QueryResult(columns, cut, n);
        }

        /**
         * Convertit les maxRows premières lignes en List<Map> (apercu, rétrocompat).
         * Usage : benchmark apercu, tests unitaires.
         */
        public List<Map<String, Object>> toRows(int maxRows) {
            int n = Math.min(totalRows, maxRows);
            List<Map<String, Object>> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : columns) row.put(col, data.get(col).get(i));
                rows.add(row);
            }
            return rows;
        }
    }

    // -------------------------------------------------------------------------
    // Point d'entrée
    // -------------------------------------------------------------------------

    public QueryResult query(
            Table table,
            List<String> select,
            WhereClause where,
            String groupBy,
            String orderBy,
            boolean orderAsc,
            int limit) {

        // 1. WHERE : scan parallèle → indices des lignes qui passent le filtre
        List<Integer> matchingRows = applyWhere(table, where);

        QueryResult result;

        // 2. GROUP BY
        if (groupBy != null) {
            result = applyGroupBy(table, matchingRows, select, groupBy);
            if (orderBy != null) applyOrderBy(result, orderBy, orderAsc);
            if (limit >= 0 && result.totalRows() > limit) result = result.truncate(limit);

        // 3. TOP-N : ORDER BY + LIMIT sans GROUP BY → min-heap O(n log k)
        } else if (orderBy != null && limit >= 0) {
            result = applyTopN(table, matchingRows, select, orderBy, orderAsc, limit);

        // 4. SELECT simple
        } else {
            List<Integer> rowsToMaterialize = (limit >= 0 && matchingRows.size() > limit)
                    ? matchingRows.subList(0, limit)
                    : matchingRows;
            result = applySelect(table, rowsToMaterialize, select);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // WHERE — scan parallèle sur les indices de lignes
    // -------------------------------------------------------------------------

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
            case "=": case "==":
                if (cellValue instanceof Number cn && filterValue instanceof Number fn)
                    return cn.doubleValue() == fn.doubleValue();
                return cellValue.toString().equals(filterValue.toString());
            case "!=": case "<>":
                if (cellValue instanceof Number cn && filterValue instanceof Number fn)
                    return cn.doubleValue() != fn.doubleValue();
                return !cellValue.toString().equals(filterValue.toString());
            case "CONTAINS":
                return cellValue.toString().contains(filterValue.toString());
            default:
                double cell, filter;
                if (cellValue instanceof Number cn) cell = cn.doubleValue();
                else { try { cell = Double.parseDouble(cellValue.toString()); } catch (NumberFormatException e) { return false; } }
                if (filterValue instanceof Number fn) filter = fn.doubleValue();
                else { try { filter = Double.parseDouble(filterValue.toString()); } catch (NumberFormatException e) { return false; } }
                return switch (operator) {
                    case "<"  -> cell < filter;
                    case ">"  -> cell > filter;
                    case "<=" -> cell <= filter;
                    case ">=" -> cell >= filter;
                    default   -> false;
                };
        }
    }

    // -------------------------------------------------------------------------
    // SELECT colonnaire — zéro Map par ligne
    // -------------------------------------------------------------------------

    private QueryResult applySelect(Table table, List<Integer> rows, List<String> select) {
        List<Column> cols = resolveColumns(table, select);
        List<String> names = cols.stream().map(Column::getName).collect(Collectors.toList());

        Map<String, List<Object>> data = new LinkedHashMap<>();
        for (Column c : cols) data.put(c.getName(), new ArrayList<>(rows.size()));

        for (int rowIdx : rows) {
            for (Column c : cols) {
                data.get(c.getName()).add(c.get(rowIdx));
            }
        }
        return new QueryResult(names, data, rows.size());
    }

    // -------------------------------------------------------------------------
    // TOP-N — min-heap O(n log k), zéro tri complet
    // -------------------------------------------------------------------------

    private QueryResult applyTopN(
            Table table, List<Integer> rows, List<String> select,
            String orderBy, boolean orderAsc, int limit) {

        Column sortCol = table.getColumn(orderBy);

        PriorityQueue<Integer> heap = new PriorityQueue<>(limit + 1, (a, b) -> {
            double va = toDouble(sortCol.get(a));
            double vb = toDouble(sortCol.get(b));
            return orderAsc ? Double.compare(vb, va) : Double.compare(va, vb);
        });

        for (int idx : rows) {
            heap.add(idx);
            if (heap.size() > limit) heap.poll();
        }

        List<Integer> topIndices = new ArrayList<>(heap);
        topIndices.sort((a, b) -> {
            double va = toDouble(sortCol.get(a));
            double vb = toDouble(sortCol.get(b));
            return orderAsc ? Double.compare(va, vb) : Double.compare(vb, va);
        });

        return applySelect(table, topIndices, select);
    }

    // -------------------------------------------------------------------------
    // GROUP BY + accumulateurs primitifs double[] — zéro boxing
    // -------------------------------------------------------------------------

    private QueryResult applyGroupBy(
            Table table, List<Integer> rows, List<String> select, String groupByCol) {

        Column groupCol = table.getColumn(groupByCol);

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

        int nAgg = aggSpecs.size();
        Map<Object, long[]>   counts = new LinkedHashMap<>();
        Map<Object, double[]> sums   = new LinkedHashMap<>();
        Map<Object, double[]> mins   = new LinkedHashMap<>();
        Map<Object, double[]> maxs   = new LinkedHashMap<>();

        for (int rowIdx : rows) {
            Object key = groupCol.get(rowIdx);
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            if (nAgg > 0) {
                double[] s  = sums.computeIfAbsent(key, k -> new double[nAgg]);
                double[] mn = mins.computeIfAbsent(key, k -> { double[] a = new double[nAgg]; Arrays.fill(a, Double.MAX_VALUE);  return a; });
                double[] mx = maxs.computeIfAbsent(key, k -> { double[] a = new double[nAgg]; Arrays.fill(a, -Double.MAX_VALUE); return a; });
                for (int i = 0; i < nAgg; i++) {
                    Object v = aggSpecs.get(i).col().get(rowIdx);
                    if (v instanceof Number n) {
                        double d = n.doubleValue();
                        s[i]  += d;
                        if (d < mn[i]) mn[i] = d;
                        if (d > mx[i]) mx[i] = d;
                    }
                }
            }
        }

        // Construire résultat colonnaire
        List<String> names = new ArrayList<>();
        names.add(groupByCol);
        names.add("count");
        for (AggSpec spec : aggSpecs) names.add(spec.sel());

        int nGroups = counts.size();
        Map<String, List<Object>> data = new LinkedHashMap<>();
        for (String name : names) data.put(name, new ArrayList<>(nGroups));

        for (Object key : counts.keySet()) {
            long cnt = counts.get(key)[0];
            data.get(groupByCol).add(key);
            data.get("count").add(cnt);
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
                data.get(spec.sel()).add(val);
            }
        }

        return new QueryResult(names, data, nGroups);
    }

    // -------------------------------------------------------------------------
    // ORDER BY sur résultat colonnaire — permutation d'indices
    // -------------------------------------------------------------------------

    private void applyOrderBy(QueryResult result, String col, boolean asc) {
        List<Object> sortCol = result.data().get(col);
        if (sortCol == null) return;
        int n = sortCol.size();

        Integer[] indices = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(indices, (a, b) -> {
            double va = toDouble(sortCol.get(a));
            double vb = toDouble(sortCol.get(b));
            return asc ? Double.compare(va, vb) : Double.compare(vb, va);
        });

        for (String colName : result.columns()) {
            List<Object> old    = result.data().get(colName);
            List<Object> sorted = new ArrayList<>(n);
            for (int idx : indices) sorted.add(old.get(idx));
            result.data().put(colName, sorted);
        }
    }

    // -------------------------------------------------------------------------
    // COUNT — scan parallèle sans matérialiser de résultats
    // -------------------------------------------------------------------------

    public long count(Table table, WhereClause where) {
        return applyWhere(table, where).size();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Column> resolveColumns(Table table, List<String> select) {
        if (select == null || select.isEmpty()) return table.getColumnsInternal();
        List<Column> cols = new ArrayList<>();
        for (String s : select) cols.add(table.getColumn(s));
        return cols;
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    // -------------------------------------------------------------------------
    // WhereClause
    // -------------------------------------------------------------------------

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
