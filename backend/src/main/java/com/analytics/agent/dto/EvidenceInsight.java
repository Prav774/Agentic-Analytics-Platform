package com.analytics.agent.dto;

import java.util.List;

public record EvidenceInsight(
        String summary,
        List<String> observations,
        List<String> trends,
        List<String> comparisons,
        List<String> recommendations
) {
    public static EvidenceInsight empty(String message) {
        return new EvidenceInsight(
                message,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
