package com.example.engine.core;

import com.example.engine.storage.Column;
import com.example.engine.storage.Table;

import java.util.*;

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
        } else {
            // 3. SELECT simple
            result = applySelect(table, matchingRows, select);
        }

        // 4. ORDER BY
        if (orderBy != null) {
            applyOrderBy(result, orderBy, orderAsc);
        }

        // 5. LIMIT
        if (limit >= 0 && result.size() > limit) {
            result = result.subList(0, limit);
        }

        return result;
    }

    // WHERE
    private List<Integer> applyWhere(Table table, WhereClause where) {
        List<Integer> result = new ArrayList<>();
        int total = table.rowCount();

        if (where == null) {
            for (int i = 0; i < total; i++) result.add(i);
            return result;
        }

        Column col = table.getColumn(where.column);
        for (int i = 0; i < total; i++) {
            if (matches(col.get(i), where.operator, where.value)) {
                result.add(i);
            }
        }
        return result;
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

        // Regroupement : valeur → indices
        Map<Object, List<Integer>> groups = new LinkedHashMap<>();
        for (int rowIdx : rows) {
            Object key = groupCol.get(rowIdx);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(rowIdx);
        }

        // Construire le résultat
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Object, List<Integer>> entry : groups.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(groupByCol, entry.getKey());
            row.put("count", entry.getValue().size());

            // Agrégations supplémentaires si demandées
            if (select != null) {
                for (String sel : select) {
                    String upper = sel.toUpperCase();
                    for (String agg : List.of("SUM", "AVG", "MIN", "MAX")) {
                        if (upper.startsWith(agg + "(") && upper.endsWith(")")) {
                            String colName = sel.substring(agg.length() + 1, sel.length() - 1).trim();
                            Column aggCol = table.getColumn(colName);
                            List<Double> values = new ArrayList<>();
                            for (int idx : entry.getValue()) {
                                Object v = aggCol.get(idx);
                                if (v != null) {
                                    try { values.add(Double.parseDouble(v.toString())); }
                                    catch (NumberFormatException ignored) {}
                                }
                            }
                            row.put(sel, computeAgg(agg, values));
                        }
                    }
                }
            }
            result.add(row);
        }
        return result;
    }

    private Object computeAgg(String agg, List<Double> values) {
        if (values.isEmpty()) return null;
        return switch (agg) {
            case "SUM" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "AVG" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "MIN" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "MAX" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            default    -> null;
        };
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
