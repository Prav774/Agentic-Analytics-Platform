package com.analytics.agent.dto;

import java.util.List;
import java.util.Map;

public record AnalyticsQueryResponse(
        String sessionId,
        String question,
        String datasourceId,
        String datasourceName,
        QueryIntent intent,
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        boolean truncated,
        VisualizationConfig visualization,
        EvidenceInsight insights,
        ExecutionMetadata metadata,
        boolean clarificationNeeded,
        String clarificationPrompt
) {
    public static AnalyticsQueryResponse clarification(
            String sessionId,
            String question,
            String datasourceId,
            String datasourceName,
            QueryIntent intent,
            String clarificationPrompt,
            ExecutionMetadata metadata
    ) {
        return new AnalyticsQueryResponse(
                sessionId,
                question,
                datasourceId,
                datasourceName,
                intent,
                null,
                List.of(),
                List.of(),
                0,
                false,
                new VisualizationConfig("table", null, null, "Clarification Needed", List.of()),
                new EvidenceInsight(clarificationPrompt, List.of(), List.of(), List.of(), List.of()),
                metadata,
                true,
                clarificationPrompt
        );
    }
}
