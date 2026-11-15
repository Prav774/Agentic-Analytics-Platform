package com.analytics.agent.orchestrator;

import com.analytics.agent.agent.AnalyticsInsightAgent;
import com.analytics.agent.agent.IntentAnalyzerAgent;
import com.analytics.agent.agent.SchemaContextAgent;
import com.analytics.agent.agent.SqlGenerationAgent;
import com.analytics.agent.agent.SqlValidationAgent;
import com.analytics.agent.agent.VisualizationSelector;
import com.analytics.agent.dto.AnalyticsQueryRequest;
import com.analytics.agent.dto.AnalyticsQueryResponse;
import com.analytics.agent.dto.EvidenceInsight;
import com.analytics.agent.dto.ExecutionMetadata;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.dto.VisualizationConfig;
import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.agent.service.ConversationSessionService;
import com.analytics.agent.service.LlmClientService;
import com.analytics.datasource.DatasourceConnector;
import com.analytics.datasource.DatasourceRegistry;
import com.analytics.exception.DatasourceNotFoundException;
import com.analytics.exception.McpServerUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsOrchestrator.class);

    private final LlmClientService llmClientService;
    private final DatasourceRegistry datasourceRegistry;
    private final ConversationSessionService sessionService;
    private final IntentAnalyzerAgent intentAnalyzerAgent;
    private final SchemaContextAgent schemaContextAgent;
    private final SqlGenerationAgent sqlGenerationAgent;
    private final SqlValidationAgent sqlValidationAgent;
    private final AnalyticsInsightAgent analyticsInsightAgent;
    private final VisualizationSelector visualizationSelector;

    public AnalyticsOrchestrator(
            LlmClientService llmClientService,
            DatasourceRegistry datasourceRegistry,
            ConversationSessionService sessionService,
            IntentAnalyzerAgent intentAnalyzerAgent,
            SchemaContextAgent schemaContextAgent,
            SqlGenerationAgent sqlGenerationAgent,
            SqlValidationAgent sqlValidationAgent,
            AnalyticsInsightAgent analyticsInsightAgent,
            VisualizationSelector visualizationSelector) {
        this.llmClientService = llmClientService;
        this.datasourceRegistry = datasourceRegistry;
        this.sessionService = sessionService;
        this.intentAnalyzerAgent = intentAnalyzerAgent;
        this.schemaContextAgent = schemaContextAgent;
        this.sqlGenerationAgent = sqlGenerationAgent;
        this.sqlValidationAgent = sqlValidationAgent;
        this.analyticsInsightAgent = analyticsInsightAgent;
        this.visualizationSelector = visualizationSelector;
    }

    public AnalyticsQueryResponse processQuery(AnalyticsQueryRequest request) {
        long startTime = System.currentTimeMillis();

        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Question must not be blank.");
        }

        // 1. Resolve Session and Validate Datasource Consistency for Existing Session
        String sessionId = sessionService.resolveSessionId(request.sessionId());

        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            if (request.datasourceId() != null && !request.datasourceId().isBlank()) {
                sessionService.validateDatasourceForSession(sessionId, request.datasourceId());
            }
        }

        // 2. Validate Groq Configuration
        llmClientService.validateConfiguration();

        // 3. Determine and Validate Datasource
        ConversationSessionService.ConversationSession existingSession = sessionService.getSession(sessionId);
        String boundDatasourceId = existingSession != null ? existingSession.getDatasourceId() : null;

        String targetDatasourceId = request.datasourceId();
        if ((targetDatasourceId == null || targetDatasourceId.isBlank()) && boundDatasourceId != null) {
            targetDatasourceId = boundDatasourceId;
        }

        if (targetDatasourceId != null && !targetDatasourceId.isBlank()) {
            if (!datasourceRegistry.hasConnector(targetDatasourceId)) {
                throw new DatasourceNotFoundException("Requested datasource not found: " + targetDatasourceId);
            }
        }

        String sessionHistory = sessionService.formatHistoryForPrompt(sessionId, targetDatasourceId);

        // 4. Intent Analysis
        QueryIntent intent = intentAnalyzerAgent.analyzeIntent(request.question(), targetDatasourceId, sessionHistory);

        // If question is ambiguous, return a clarification response without executing arbitrary SQL
        if (intent.ambiguous()) {
            long duration = System.currentTimeMillis() - startTime;
            ExecutionMetadata metadata = new ExecutionMetadata(duration, llmClientService.getModelName(), "N/A");
            return AnalyticsQueryResponse.clarification(
                    sessionId,
                    request.question(),
                    intent.datasourceId(),
                    intent.datasourceId() != null ? intent.datasourceId().toUpperCase() : "Unknown",
                    intent,
                    intent.clarificationMessage(),
                    metadata
            );
        }

        String finalDatasourceId = intent.datasourceId() != null ? intent.datasourceId().toLowerCase() : "ecommerce";
        if (boundDatasourceId != null && !boundDatasourceId.equalsIgnoreCase(finalDatasourceId)) {
            sessionService.validateDatasourceForSession(sessionId, finalDatasourceId);
        }

        if (!datasourceRegistry.hasConnector(finalDatasourceId)) {
            throw new DatasourceNotFoundException("Datasource '" + finalDatasourceId + "' is not available in the registry.");
        }

        DatasourceConnector connector = datasourceRegistry.getConnector(finalDatasourceId);
        if (!connector.isAvailable()) {
            throw new McpServerUnavailableException("MCP server for datasource '" + finalDatasourceId + "' is currently unavailable.");
        }

        // Bind session to active datasource
        sessionService.getOrCreateSession(sessionId, finalDatasourceId);

        // 5. Retrieve Live Schema Context via MCP
        DatasourceSchemaContext schemaContext = schemaContextAgent.getSchemaContext(finalDatasourceId);

        // 6. Generate Dialect-Specific SQL via LLM
        String rawSql = sqlGenerationAgent.generateSql(request.question(), intent, schemaContext, sessionHistory);

        // 7. Validate SQL strictly with JSqlParser and Whitelists
        SqlValidationAgent.ValidatedSql validated = sqlValidationAgent.validate(rawSql, schemaContext);
        String executableSql = validated.sql();
        int rowLimit = validated.rowLimit();

        // 8. Execute SQL strictly through the existing MCP Connector
        log.info("Executing validated analytics query on MCP datasource '{}': {}", finalDatasourceId, executableSql);
        Map<String, Object> queryResult = connector.executeQuery(executableSql, rowLimit);

        List<String> columns = extractColumns(queryResult);
        List<Map<String, Object>> rows = extractRows(queryResult);
        int rowCount = queryResult.get("rowCount") instanceof Number n ? n.intValue() : rows.size();
        boolean truncated = Boolean.TRUE.equals(queryResult.get("truncated"));

        // 9. Generate Evidence-Based Insights from actual returned rows
        EvidenceInsight insights = analyticsInsightAgent.generateInsights(
                request.question(),
                intent,
                executableSql,
                columns,
                rows
        );

        // 10. Select Optimal Visualization and Compute KPIs
        VisualizationConfig visualization = visualizationSelector.selectVisualization(
                request.question(),
                intent,
                columns,
                rows
        );

        long executionDuration = System.currentTimeMillis() - startTime;
        ExecutionMetadata metadata = new ExecutionMetadata(
                executionDuration,
                llmClientService.getModelName(),
                schemaContext.databaseEngine()
        );

        // 11. Record turn in session memory
        sessionService.recordTurn(
                sessionId,
                request.question(),
                intent,
                executableSql,
                insights.summary(),
                finalDatasourceId
        );

        // 12. Return complete response
        return new AnalyticsQueryResponse(
                sessionId,
                request.question(),
                finalDatasourceId,
                connector.getName(),
                intent,
                executableSql,
                columns,
                rows,
                rowCount,
                truncated,
                visualization,
                insights,
                metadata,
                false,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> extractColumns(Map<String, Object> result) {
        if (result != null && result.get("columns") instanceof List<?> list) {
            List<String> cols = new ArrayList<>();
            for (Object item : list) {
                if (item != null) cols.add(item.toString());
            }
            return cols;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> result) {
        if (result != null && result.get("rows") instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            return rows;
        }
        return Collections.emptyList();
    }
}
