package com.analytics.exception;

public class DatasourceNotFoundException extends RuntimeException {
    public DatasourceNotFoundException(String message) {
        super(message);
    }
}
