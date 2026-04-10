package com.example.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum DataType {
    STRING,
    INT,
    LONG,
    DOUBLE,
    BOOLEAN;

    @JsonCreator
    public static DataType fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Column type is required");
        }

        try {
            return DataType.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported data type: " + rawValue);
        }
    }
}
