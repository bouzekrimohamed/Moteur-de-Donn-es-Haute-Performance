package com.example.engine.core;

import com.example.engine.storage.Row;
import com.example.engine.storage.RowCursor;
import com.example.engine.storage.Table;

import java.util.*;

/**
 * Moteur de requêtes en STREAMING.
 *
 * Au lieu de lire les colonnes par index en mémoire, il parcourt la table via
 * un {@link RowCursor} (RAM puis segments disque) ligne par ligne, en un seul
 * passage. Les données "froides" stockées sur disque ne sont donc jamais
 * entièrement rechargées en RAM.
 *
 * Supporte : SELECT, WHERE (=, !=, <, >, <=, >=, CONTAINS), GROUP BY,
 *            agrégations (COUNT, SUM, AVG, MIN, MAX), ORDER BY, LIMIT.
 */
public class QueryEngine {

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

        List<Map<String, Object>> result;
        if (groupBy != null) {
            result = streamGroupBy(table, where, select, groupBy);
        } else {
            result = streamSelect(table, where, select, orderBy, limit);
        }

        // ORDER BY (sur le jeu de résultats, pas sur la source).
        if (orderBy != null) {
            applyOrderBy(result, orderBy, orderAsc);
        }

        // LIMIT final (déjà appliqué en avance dans le SELECT sans ORDER BY).
        if (limit >= 0 && result.size() > limit) {
            result = new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // SELECT (projection) en streaming
    // ---------------------------------------------------------------------
    private List<Map<String, Object>> streamSelect(
            Table table, WhereClause where, List<String> select,
            String orderBy, int limit) {

        boolean selectAll = (select == null || select.isEmpty());
        // Arrêt anticipé possible seulement sans ORDER BY (sinon il faut tout
        // collecter avant de trier).
        boolean canEarlyStop = (orderBy == null && limit >= 0);

        List<Map<String, Object>> result = new ArrayList<>();

        try (RowCursor cursor = table.openCursor()) {
            while (cursor.hasNext()) {
                Row row = cursor.next();
                if (!passesWhere(row, where)) continue;

                Map<String, Object> out;
                if (selectAll) {
                    out = new LinkedHashMap<>(row.values());
                } else {
                    out = new LinkedHashMap<>();
                    for (String col : select) out.put(col, row.get(col));
                }
                result.add(out);

                if (canEarlyStop && result.size() >= limit) break;
            }
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // GROUP BY + agrégations en streaming
    // ---------------------------------------------------------------------
    private List<Map<String, Object>> streamGroupBy(
            Table table, WhereClause where, List<String> select, String groupByCol) {

        List<AggSpec> aggs = parseAggregations(select);

        // valeur de regroupement -> accumulateur
        Map<Object, GroupAcc> groups = new LinkedHashMap<>();

        try (RowCursor cursor = table.openCursor()) {
            while (cursor.hasNext()) {
                Row row = cursor.next();
                if (!passesWhere(row, where)) continue;

                Object key = row.get(groupByCol);
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
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Object, GroupAcc> e : groups.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(groupByCol, e.getKey());
            row.put("count", e.getValue().count);
            for (AggSpec spec : aggs) {
                row.put(spec.raw, e.getValue().result(spec));
            }
            result.add(row);
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // WHERE (évaluation ligne par ligne)
    // ---------------------------------------------------------------------
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

    // ---------------------------------------------------------------------
    // ORDER BY (tri du jeu de résultats déjà matérialisé)
    // ---------------------------------------------------------------------
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
    // Validation du schéma
    // ---------------------------------------------------------------------
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
                    // En mode GROUP BY, un nom simple non agrégé est ignoré
                    // (comportement historique), donc on ne valide pas.
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

    // ---------------------------------------------------------------------
    // Structures internes
    // ---------------------------------------------------------------------

    /** Spécification d'une agrégation, ex : "SUM(salaire)". */
    private static class AggSpec {
        final String raw;       // "SUM(salaire)"
        final String func;      // "SUM"
        final String column;    // "salaire"

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

    /** Accumulateur par groupe : évite de stocker toutes les valeurs. */
    private static class GroupAcc {
        long count = 0;
        final Map<String, double[]> sums = new HashMap<>();  // raw -> [sum]
        final Map<String, long[]>   nums = new HashMap<>();  // raw -> [nbValeursNumeriques]
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

        Object result(AggSpec spec) {
            long n = nums.get(spec.raw)[0];
            if (n == 0) return null;
            return switch (spec.func) {
                case "SUM" -> sums.get(spec.raw)[0];
                case "AVG" -> sums.get(spec.raw)[0] / n;
                case "MIN" -> mins.get(spec.raw);
                case "MAX" -> maxs.get(spec.raw);
                default    -> null;
            };
        }
    }

    // WhereClause — structure de filtre (inchangée, API publique)
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
