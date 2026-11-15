package com.analytics.agent.dto;

public record ExecutionMetadata(
        long executionTimeMs,
        String llmModel,
        String databaseEngine
) {}
