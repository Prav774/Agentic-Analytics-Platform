package com.analytics.agent.dto;

public record KpiItem(
        String label,
        String value,
        Double rawValue,
        String format
) {}
