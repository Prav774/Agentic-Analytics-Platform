package com.analytics.exception;

public class McpServerUnavailableException extends RuntimeException {
    public McpServerUnavailableException(String message) {
        super(message);
    }

    public McpServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
