package com.analytics.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class JwtProperties {

    private final String secret;
    private final long expirationMs;

    public JwtProperties(
            @Value("${security.jwt.secret:}") String secret,
            @Value("${security.jwt.expiration-ms:86400000}") long expirationMs) {
        
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable is missing or blank. Application startup aborted for security.");
        }
        
        byte[] keyBytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits (32 bytes) long. Provided secret length: " + keyBytes.length + " bytes.");
        }

        this.secret = secret.trim();
        this.expirationMs = expirationMs > 0 ? expirationMs : 86400000L;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
