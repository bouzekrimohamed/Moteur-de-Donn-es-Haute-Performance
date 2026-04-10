package com.example.engine.model;

import java.util.List;
import java.util.Map;

public class LoadDataRequest {
    private List<Map<String, Object>> rows;

    public LoadDataRequest() {
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }
}
