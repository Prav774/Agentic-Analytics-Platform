package com.analytics.agent.dto;

import java.util.List;

public record QueryIntent(
        String datasourceId,
        String businessMetric,
        List<String> dimensions,
        List<String> filters,
        String dateRange,
        String aggregation,
        String analysisType,
        boolean ambiguous,
        String clarificationMessage
) {
    public static QueryIntent ambiguous(String message) {
        return new QueryIntent(
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                "AMBIGUOUS",
                true,
                message
        );
    }
}
