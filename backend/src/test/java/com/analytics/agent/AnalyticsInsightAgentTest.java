package com.analytics.agent;

import com.analytics.agent.agent.AnalyticsInsightAgent;
import com.analytics.agent.dto.EvidenceInsight;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.service.LlmClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsInsightAgentTest {

    @Mock
    private LlmClientService llmClientService;

    private AnalyticsInsightAgent agent;
    private QueryIntent sampleIntent;

    @BeforeEach
    void setUp() {
        agent = new AnalyticsInsightAgent(llmClientService, new ObjectMapper());
        sampleIntent = new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null);
    }

    @Test
    void returnsEmptyInsightWithoutCallingLlmWhenRowsAreEmpty() {
        EvidenceInsight insight = agent.generateInsights("Show revenue", sampleIntent, "SELECT * FROM orders", List.of("id"), List.of());
        assertNotNull(insight);
        assertTrue(insight.summary().contains("No data records were returned"));
        verifyNoInteractions(llmClientService);
    }

    @Test
    void parsesLlmInsightsJson() {
        String mockJson = """
                {
                  "summary": "Total revenue reached $150,000 across the observed period.",
                  "observations": [
                    "Peak month was March with $60,000 in revenue.",
                    "Lowest month was January with $40,000."
                  ],
                  "trends": [
                    "Steady 25% month-over-month increase."
                  ],
                  "comparisons": [
                    "Q1 exceeded expectations."
                  ],
                  "recommendations": [
                    "Continue promoting high-performing campaigns."
                  ]
                }
                """;
        when(llmClientService.generate(anyString(), anyString())).thenReturn(mockJson);

        List<Map<String, Object>> rows = List.of(
                Map.of("month", "2026-01", "revenue", 40000),
                Map.of("month", "2026-02", "revenue", 50000),
                Map.of("month", "2026-03", "revenue", 60000)
        );

        EvidenceInsight insight = agent.generateInsights("Monthly trend", sampleIntent, "SELECT * FROM orders", List.of("month", "revenue"), rows);
        assertNotNull(insight);
        assertEquals("Total revenue reached $150,000 across the observed period.", insight.summary());
        assertEquals(2, insight.observations().size());
        assertEquals(1, insight.trends().size());
        assertEquals(1, insight.recommendations().size());
    }
}
