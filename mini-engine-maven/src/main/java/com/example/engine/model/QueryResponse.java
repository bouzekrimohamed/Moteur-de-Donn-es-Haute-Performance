package com.example.engine.model;

import java.util.List;
import java.util.Map;

public class QueryResponse {
    private String status;
    private String message;
    private List<Map<String, Object>> rows;

    public QueryResponse() {
    }

    public QueryResponse(String status, String message, List<Map<String, Object>> rows) {
        this.status = status;
        this.message = message;
        this.rows = rows;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }
}
