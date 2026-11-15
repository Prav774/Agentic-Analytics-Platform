package com.analytics.agent;

import com.analytics.agent.agent.VisualizationSelector;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.dto.VisualizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VisualizationSelectorTest {

    private VisualizationSelector selector;
    private QueryIntent sampleIntent;

    @BeforeEach
    void setUp() {
        selector = new VisualizationSelector();
        sampleIntent = new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null);
    }

    @Test
    void selectsKpiForSingleRowAggregate() {
        List<String> cols = List.of("total_revenue");
        List<Map<String, Object>> rows = List.of(Map.of("total_revenue", 125000.50));

        VisualizationConfig config = selector.selectVisualization("What is total revenue?", sampleIntent, cols, rows);
        assertEquals("kpi", config.chartType());
        assertFalse(config.kpis().isEmpty());
        assertEquals("Total Revenue", config.kpis().get(0).label());
        assertEquals("currency", config.kpis().get(0).format());
    }

    @Test
    void selectsLineChartForTemporalTrend() {
        List<String> cols = List.of("month", "revenue");
        List<Map<String, Object>> rows = List.of(
                Map.of("month", "2026-01", "revenue", 10000),
                Map.of("month", "2026-02", "revenue", 15000),
                Map.of("month", "2026-03", "revenue", 12000)
        );

        VisualizationConfig config = selector.selectVisualization("Monthly trend", sampleIntent, cols, rows);
        assertEquals("line", config.chartType());
        assertEquals("month", config.xAxisColumn());
        assertEquals("revenue", config.yAxisColumn());
        assertFalse(config.kpis().isEmpty());
    }

    @Test
    void selectsPieChartForSmallDistribution() {
        List<String> cols = List.of("order_status", "count");
        List<Map<String, Object>> rows = List.of(
                Map.of("order_status", "delivered", "count", 400),
                Map.of("order_status", "pending", "count", 30),
                Map.of("order_status", "cancelled", "count", 20)
        );

        VisualizationConfig config = selector.selectVisualization("Compare order statuses", sampleIntent, cols, rows);
        assertEquals("pie", config.chartType());
        assertEquals("order_status", config.xAxisColumn());
        assertEquals("count", config.yAxisColumn());
    }

    @Test
    void selectsBarChartForCategoricalRanking() {
        List<String> cols = List.of("category", "revenue");
        List<Map<String, Object>> rows = List.of(
                Map.of("category", "Electronics", "revenue", 80000),
                Map.of("category", "Clothing", "revenue", 50000),
                Map.of("category", "Books", "revenue", 30000),
                Map.of("category", "Home", "revenue", 20000),
                Map.of("category", "Sports", "revenue", 15000),
                Map.of("category", "Toys", "revenue", 12000),
                Map.of("category", "Automotive", "revenue", 9000),
                Map.of("category", "Garden", "revenue", 6000)
        );

        VisualizationConfig config = selector.selectVisualization("Top categories", sampleIntent, cols, rows);
        assertEquals("bar", config.chartType());
        assertEquals("category", config.xAxisColumn());
        assertEquals("revenue", config.yAxisColumn());
    }

    @Test
    void selectsTableForEmptyRows() {
        VisualizationConfig config = selector.selectVisualization("Empty result", sampleIntent, List.of("id"), List.of());
        assertEquals("table", config.chartType());
        assertTrue(config.kpis().isEmpty());
    }
}
