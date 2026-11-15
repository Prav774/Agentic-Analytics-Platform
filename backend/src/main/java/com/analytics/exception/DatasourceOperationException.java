package com.analytics.exception;

public class DatasourceOperationException extends RuntimeException {
    public DatasourceOperationException(String message) {
        super(message);
    }

    public DatasourceOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
