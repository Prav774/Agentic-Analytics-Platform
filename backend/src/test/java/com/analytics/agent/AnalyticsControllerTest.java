package com.analytics.agent;

import com.analytics.agent.dto.AnalyticsQueryResponse;
import com.analytics.agent.dto.EvidenceInsight;
import com.analytics.agent.dto.ExecutionMetadata;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.dto.VisualizationConfig;
import com.analytics.agent.orchestrator.AnalyticsOrchestrator;
import com.analytics.controller.AnalyticsController;
import com.analytics.exception.DatasourceNotFoundException;
import com.analytics.exception.GlobalExceptionHandler;
import com.analytics.exception.GroqConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalyticsOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AnalyticsController(orchestrator))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void queryEndpointReturns200WithFullResponse() throws Exception {
        AnalyticsQueryResponse mockResp = new AnalyticsQueryResponse(
                "session-1",
                "Show monthly revenue",
                "ecommerce",
                "E-Commerce",
                new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null),
                "SELECT * FROM orders;",
                List.of("month", "revenue"),
                List.of(Map.of("month", "2026-01", "revenue", 10000.0)),
                1,
                false,
                new VisualizationConfig("line", "month", "revenue", "Revenue", List.of()),
                new EvidenceInsight("Summary fact", List.of("Jan had $10k"), List.of(), List.of(), List.of("Rec")),
                new ExecutionMetadata(100, "llama-3.3-70b-versatile", "MySQL"),
                false,
                null
        );

        when(orchestrator.processQuery(any())).thenReturn(mockResp);

        mockMvc.perform(post("/api/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Show monthly revenue\", \"datasourceId\": \"ecommerce\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.datasourceId").value("ecommerce"))
                .andExpect(jsonPath("$.visualization.chartType").value("line"))
                .andExpect(jsonPath("$.insights.summary").value("Summary fact"));
    }

    @Test
    void returns400WhenQuestionIsBlank() throws Exception {
        mockMvc.perform(post("/api/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void returns400WhenGroqKeyIsMissing() throws Exception {
        when(orchestrator.processQuery(any()))
                .thenThrow(new GroqConfigurationException("Groq API key is not configured."));

        mockMvc.perform(post("/api/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Show revenue\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Groq API key is not configured."));
    }

    @Test
    void returns404WhenDatasourceNotFound() throws Exception {
        when(orchestrator.processQuery(any()))
                .thenThrow(new DatasourceNotFoundException("Datasource not found: nonexistent"));

        mockMvc.perform(post("/api/analytics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Show revenue\", \"datasourceId\": \"nonexistent\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Datasource not found: nonexistent"));
    }
}
