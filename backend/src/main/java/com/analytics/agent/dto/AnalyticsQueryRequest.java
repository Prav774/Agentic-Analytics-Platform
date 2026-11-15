package com.analytics.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalyticsQueryRequest(
        @NotBlank(message = "Question must not be blank")
        String question,

        String datasourceId,

        String sessionId
) {}
