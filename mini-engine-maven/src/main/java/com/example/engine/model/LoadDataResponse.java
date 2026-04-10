package com.example.engine.model;

public class LoadDataResponse {
    private String status;
    private String message;
    private int acceptedRows;

    public LoadDataResponse() {
    }

    public LoadDataResponse(String status, String message, int acceptedRows) {
        this.status = status;
        this.message = message;
        this.acceptedRows = acceptedRows;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public int getAcceptedRows() {
        return acceptedRows;
    }
}
