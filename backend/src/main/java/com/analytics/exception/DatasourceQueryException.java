package com.analytics.exception;

public class DatasourceQueryException extends RuntimeException {
    public DatasourceQueryException(String message) {
        super(message);
    }

    public DatasourceQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
