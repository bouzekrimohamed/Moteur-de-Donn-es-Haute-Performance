package com.example.engine.api;

import com.example.engine.core.QueryEngine;
import com.example.engine.core.TableManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/tables/{tableName}/query")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class QueryResource {

    @Inject
    TableManager tableManager;

    /**
     * Exécute une requête sur une table.
     *
     * Corps JSON attendu :
     * {
     *   "select":  ["col1", "SUM(col2)"],   // optionnel, null = toutes les colonnes
     *   "where":   { "column": "age", "operator": ">", "value": 18 },  // optionnel
     *   "groupBy":  "ville",                // optionnel
     *   "orderBy":  "age",                  // optionnel
     *   "orderAsc": true,                   // optionnel, défaut = true
     *   "limit":    100                     // optionnel, -1 = pas de limite
     * }
     *
     * Réponse colonnaire :
     * {
     *   "totalRows": 9,
     *   "columns": {
     *     "payment_type":      [1,        2,       3     ],
     *     "count":             [4585922,  653524,  38490 ],
     *     "SUM(total_amount)": [131910597,14624511,431164]
     *   }
     * }
     */
    @POST
    public Response query(
            @PathParam("tableName") String tableName,
            QueryRequest req) {
        try {
            QueryEngine.WhereClause where = null;
            if (req.where != null) {
                where = new QueryEngine.WhereClause(
                        req.where.column,
                        req.where.operator,
                        req.where.value);
            }

            boolean asc   = req.orderAsc == null || req.orderAsc;
            int     limit = req.limit    == null ? -1 : req.limit;

            QueryEngine.QueryResult result = tableManager.query(
                    tableName, req.select, where, req.groupBy, req.orderBy, asc, limit);

            return Response.ok(new QueryResponse(result.totalRows(), result.data())).build();

        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new TableResource.ErrorResponse(ex.getMessage())).build();
        }
    }

    // --- DTOs ---

    public static class QueryRequest {
        public List<String> select;
        public WhereRequest where;
        public String  groupBy;
        public String  orderBy;
        public Boolean orderAsc;
        public Integer limit;
    }

    public static class WhereRequest {
        public String column;
        public String operator;
        public Object value;
    }

    public static class QueryResponse {
        public int totalRows;
        public Map<String, List<Object>> columns;

        public QueryResponse(int totalRows, Map<String, List<Object>> columns) {
            this.totalRows = totalRows;
            this.columns   = columns;
        }
    }
}
