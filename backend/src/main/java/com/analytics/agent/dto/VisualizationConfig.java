package com.analytics.agent.dto;

import java.util.List;

public record VisualizationConfig(
        String chartType, // "kpi", "bar", "line", "pie", "table"
        String xAxisColumn,
        String yAxisColumn,
        String title,
        List<KpiItem> kpis
) {}
