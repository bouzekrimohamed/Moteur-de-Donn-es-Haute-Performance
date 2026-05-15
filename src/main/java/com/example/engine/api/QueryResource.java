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
     *   "select":  ["col1", "col2"],          // optionnel, null = toutes les colonnes
     *   "where":   {                           // optionnel
     *     "column": "age",
     *     "operator": ">",
     *     "value": 18
     *   },
     *   "groupBy":  "ville",                   // optionnel
     *   "orderBy":  "age",                     // optionnel
     *   "orderAsc": true,                      // optionnel, défaut = true
     *   "limit":    100                        // optionnel, -1 = pas de limite
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

            boolean asc = req.orderAsc == null || req.orderAsc;
            int limit   = req.limit == null ? -1 : req.limit;

            List<Map<String, Object>> rows = tableManager.query(
                    tableName,
                    req.select,
                    where,
                    req.groupBy,
                    req.orderBy,
                    asc,
                    limit);

            QueryResponse response = new QueryResponse(rows.size(), rows);
            return Response.ok(response).build();

        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new TableResource.ErrorResponse(ex.getMessage())).build();
        }
    }

    // --- DTOs ---

    public static class QueryRequest {
        public List<String> select;
        public WhereRequest where;
        public String groupBy;
        public String orderBy;
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
        public List<Map<String, Object>> rows;
        public QueryResponse(int t, List<Map<String, Object>> r) { totalRows=t; rows=r; }
    }
}
