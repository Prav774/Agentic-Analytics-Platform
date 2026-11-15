package com.analytics.datasource;

import java.util.List;

public record DatasourceSummary(
        String id,
        String name,
        String description,
        String status,
        List<String> tools
) {}
