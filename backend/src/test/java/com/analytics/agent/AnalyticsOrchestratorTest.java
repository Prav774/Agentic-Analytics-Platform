package com.analytics.agent;

import com.analytics.agent.agent.AnalyticsInsightAgent;
import com.analytics.agent.agent.IntentAnalyzerAgent;
import com.analytics.agent.agent.SchemaContextAgent;
import com.analytics.agent.agent.SqlGenerationAgent;
import com.analytics.agent.agent.SqlValidationAgent;
import com.analytics.agent.agent.VisualizationSelector;
import com.analytics.agent.dto.AnalyticsQueryRequest;
import com.analytics.agent.dto.AnalyticsQueryResponse;
import com.analytics.agent.dto.EvidenceInsight;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.dto.VisualizationConfig;
import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.agent.orchestrator.AnalyticsOrchestrator;
import com.analytics.agent.service.ConversationSessionService;
import com.analytics.agent.service.LlmClientService;
import com.analytics.datasource.DatasourceConnector;
import com.analytics.datasource.DatasourceRegistry;
import com.analytics.exception.DatasourceNotFoundException;
import com.analytics.exception.GroqConfigurationException;
import com.analytics.exception.McpServerUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsOrchestratorTest {

    @Mock
    private LlmClientService llmClientService;

    @Mock
    private DatasourceRegistry datasourceRegistry;

    @Mock
    private ConversationSessionService sessionService;

    @Mock
    private IntentAnalyzerAgent intentAnalyzerAgent;

    @Mock
    private SchemaContextAgent schemaContextAgent;

    @Mock
    private SqlGenerationAgent sqlGenerationAgent;

    @Mock
    private SqlValidationAgent sqlValidationAgent;

    @Mock
    private AnalyticsInsightAgent analyticsInsightAgent;

    @Mock
    private VisualizationSelector visualizationSelector;

    @Mock
    private DatasourceConnector connector;

    private AnalyticsOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AnalyticsOrchestrator(
                llmClientService,
                datasourceRegistry,
                sessionService,
                intentAnalyzerAgent,
                schemaContextAgent,
                sqlGenerationAgent,
                sqlValidationAgent,
                analyticsInsightAgent,
                visualizationSelector
        );
    }

    @Test
    void throwsWhenSwitchingDatasourceWithinSameSession() {
        when(sessionService.resolveSessionId("session-1")).thenReturn("session-1");
        doThrow(new IllegalArgumentException("Session 'session-1' is bound to datasource 'ecommerce'. Switching to datasource 'crm' within the same session is not allowed. Please start a new session."))
                .when(sessionService).validateDatasourceForSession("session-1", "crm");

        AnalyticsQueryRequest req = new AnalyticsQueryRequest("Show opportunities", "crm", "session-1");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orchestrator.processQuery(req));
        assertTrue(ex.getMessage().contains("Switching to datasource 'crm' within the same session is not allowed"));
    }

    @Test
    void throwsWhenGroqKeyIsMissing() {
        when(sessionService.resolveSessionId(any())).thenReturn("session-1");
        doThrow(new GroqConfigurationException("Groq API key is not configured."))
                .when(llmClientService).validateConfiguration();

        AnalyticsQueryRequest req = new AnalyticsQueryRequest("What is monthly revenue?", "ecommerce", null);
        assertThrows(GroqConfigurationException.class, () -> orchestrator.processQuery(req));
    }

    @Test
    void throwsWhenDatasourceNotFound() {
        when(sessionService.resolveSessionId(any())).thenReturn("session-1");
        when(datasourceRegistry.hasConnector("invalid")).thenReturn(false);

        AnalyticsQueryRequest req = new AnalyticsQueryRequest("What is revenue?", "invalid", null);
        assertThrows(DatasourceNotFoundException.class, () -> orchestrator.processQuery(req));
    }

    @Test
    void returnsClarificationWhenQueryIsAmbiguous() {
        when(sessionService.resolveSessionId(any())).thenReturn("session-1");
        when(datasourceRegistry.hasConnector("ecommerce")).thenReturn(true);
        when(llmClientService.getModelName()).thenReturn("llama-3.3-70b-versatile");

        QueryIntent ambiguousIntent = QueryIntent.ambiguous("Please specify whether you mean CRM or E-Commerce.");
        when(intentAnalyzerAgent.analyzeIntent(anyString(), any(), any())).thenReturn(ambiguousIntent);

        AnalyticsQueryRequest req = new AnalyticsQueryRequest("Tell me something", "ecommerce", null);
        AnalyticsQueryResponse response = orchestrator.processQuery(req);

        assertNotNull(response);
        assertTrue(response.clarificationNeeded());
        assertEquals("Please specify whether you mean CRM or E-Commerce.", response.clarificationPrompt());
        assertNull(response.sql());
    }

    @Test
    void throwsWhenMcpServerIsUnavailable() {
        when(sessionService.resolveSessionId(any())).thenReturn("session-1");
        when(datasourceRegistry.hasConnector("ecommerce")).thenReturn(true);
        when(datasourceRegistry.getConnector("ecommerce")).thenReturn(connector);
        when(connector.isAvailable()).thenReturn(false);

        QueryIntent intent = new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null);
        when(intentAnalyzerAgent.analyzeIntent(anyString(), any(), any())).thenReturn(intent);

        AnalyticsQueryRequest req = new AnalyticsQueryRequest("What is revenue trend?", "ecommerce", null);
        assertThrows(McpServerUnavailableException.class, () -> orchestrator.processQuery(req));
    }

    @Test
    void completeSuccessfulOrchestrationFlow() {
        when(sessionService.resolveSessionId(any())).thenReturn("session-1");
        when(datasourceRegistry.hasConnector("ecommerce")).thenReturn(true);
        when(datasourceRegistry.getConnector("ecommerce")).thenReturn(connector);
        when(connector.isAvailable()).thenReturn(true);
        when(connector.getName()).thenReturn("E-Commerce");

        QueryIntent intent = new QueryIntent("ecommerce", "revenue", List.of("month"), List.of(), null, "SUM", "TREND", false, null);
        when(intentAnalyzerAgent.analyzeIntent(anyString(), any(), any())).thenReturn(intent);

        DatasourceSchemaContext schemaContext = new DatasourceSchemaContext(
                "ecommerce", "E-Commerce", "MySQL", "mysql", List.of(), List.of());
        when(schemaContextAgent.getSchemaContext("ecommerce")).thenReturn(schemaContext);

        when(sqlGenerationAgent.generateSql(anyString(), any(), any(), any()))
                .thenReturn("SELECT DATE_FORMAT(order_date, '%Y-%m') AS month, SUM(total_amount) AS revenue FROM orders GROUP BY month;");

        when(sqlValidationAgent.validate(anyString(), any()))
                .thenReturn(new SqlValidationAgent.ValidatedSql("SELECT DATE_FORMAT(order_date, '%Y-%m') AS month, SUM(total_amount) AS revenue FROM orders GROUP BY month;", 500));

        when(connector.executeQuery(anyString(), eq(500))).thenReturn(Map.of(
                "columns", List.of("month", "revenue"),
                "rows", List.of(Map.of("month", "2026-01", "revenue", 50000.0)),
                "rowCount", 1,
                "truncated", false
        ));

        when(analyticsInsightAgent.generateInsights(anyString(), any(), any(), any(), any()))
                .thenReturn(new EvidenceInsight("Revenue was $50,000.", List.of("January was $50k"), List.of(), List.of(), List.of("Expand marketing")));

        when(visualizationSelector.selectVisualization(anyString(), any(), any(), any()))
                .thenReturn(new VisualizationConfig("line", "month", "revenue", "Monthly Revenue", List.of()));

        when(llmClientService.getModelName()).thenReturn("llama-3.3-70b-versatile");

        AnalyticsQueryRequest req = new AnalyticsQueryRequest("What was our monthly revenue trend?", "ecommerce", "session-1");
        AnalyticsQueryResponse response = orchestrator.processQuery(req);

        assertNotNull(response);
        assertFalse(response.clarificationNeeded());
        assertEquals("session-1", response.sessionId());
        assertEquals("ecommerce", response.datasourceId());
        assertEquals(1, response.rowCount());
        assertEquals("line", response.visualization().chartType());
        assertEquals("Revenue was $50,000.", response.insights().summary());

        verify(sessionService).recordTurn(eq("session-1"), anyString(), any(), anyString(), anyString(), eq("ecommerce"));
    }
}
