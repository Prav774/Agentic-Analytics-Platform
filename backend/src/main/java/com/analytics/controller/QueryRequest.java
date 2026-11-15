package com.analytics.controller;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(
        @NotBlank(message = "SQL query must not be empty.")
        String sql,
        Integer rowLimit
) {}
