package com.analytics.exception;

public class GroqConfigurationException extends RuntimeException {
    public GroqConfigurationException(String message) {
        super(message);
    }

    public GroqConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
