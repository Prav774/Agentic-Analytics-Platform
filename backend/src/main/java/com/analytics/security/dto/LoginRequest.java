package com.analytics.security.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username or email is required")
        @JsonAlias({"usernameOrEmail", "email"})
        String username,

        @NotBlank(message = "Password is required")
        String password
) {
    public String getIdentifier() {
        return username != null ? username.trim() : "";
    }
}
