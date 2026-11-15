package com.analytics.agent;

import com.analytics.agent.agent.IntentAnalyzerAgent;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.service.LlmClientService;
import com.analytics.datasource.DatasourceRegistry;
import com.analytics.exception.DatasourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntentAnalyzerAgentTest {

    @Mock
    private LlmClientService llmClientService;

    @Mock
    private DatasourceRegistry datasourceRegistry;

    private IntentAnalyzerAgent agent;

    @BeforeEach
    void setUp() {
        agent = new IntentAnalyzerAgent(llmClientService, datasourceRegistry, new ObjectMapper());
    }

    @Test
    void rejectsInvalidDatasourceId() {
        when(datasourceRegistry.hasConnector("invalid-db")).thenReturn(false);
        assertThrows(DatasourceNotFoundException.class, () ->
                agent.analyzeIntent("Show revenue", "invalid-db", null));
    }

    @Test
    void handlesAmbiguousQuestionWithoutCallingLlm() {
        QueryIntent intent = agent.analyzeIntent("hello?", null, null);
        assertTrue(intent.ambiguous());
        assertNotNull(intent.clarificationMessage());
        verifyNoInteractions(llmClientService);
    }

    @Test
    void parsesLlmJsonIntentSuccessfully() {
        when(datasourceRegistry.hasConnector("ecommerce")).thenReturn(true);
        String mockLlmResponse = """
                {
                  "datasourceId": "ecommerce",
                  "businessMetric": "revenue",
                  "dimensions": ["month"],
                  "filters": [],
                  "dateRange": "all",
                  "aggregation": "SUM",
                  "analysisType": "TREND",
                  "ambiguous": false,
                  "clarificationMessage": null
                }
                """;
        when(llmClientService.generate(anyString(), anyString())).thenReturn(mockLlmResponse);

        QueryIntent intent = agent.analyzeIntent("What was our monthly revenue trend?", "ecommerce", null);
        assertNotNull(intent);
        assertFalse(intent.ambiguous());
        assertEquals("ecommerce", intent.datasourceId());
        assertEquals("revenue", intent.businessMetric());
        assertEquals("TREND", intent.analysisType());
    }

    @Test
    void fallsBackToRuleBasedWhenLlmOutputIsNonJson() {
        when(llmClientService.generate(anyString(), anyString())).thenReturn("I cannot provide JSON right now.");

        QueryIntent intent = agent.analyzeIntent("Show me the CRM sales pipeline by stage", null, null);
        assertNotNull(intent);
        assertEquals("crm", intent.datasourceId());
        assertFalse(intent.ambiguous());
    }
}
