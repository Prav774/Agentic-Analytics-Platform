package com.analytics.agent.agent;

import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.service.LlmClientService;
import com.analytics.datasource.DatasourceRegistry;
import com.analytics.exception.DatasourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class IntentAnalyzerAgent {

    private static final Logger log = LoggerFactory.getLogger(IntentAnalyzerAgent.class);

    private final LlmClientService llmClientService;
    private final DatasourceRegistry datasourceRegistry;
    private final ObjectMapper objectMapper;

    public IntentAnalyzerAgent(
            LlmClientService llmClientService,
            DatasourceRegistry datasourceRegistry,
            ObjectMapper objectMapper) {
        this.llmClientService = llmClientService;
        this.datasourceRegistry = datasourceRegistry;
        this.objectMapper = objectMapper;
    }

    public QueryIntent analyzeIntent(String question, String requestedDatasourceId, String sessionHistory) {
        if (question == null || question.isBlank()) {
            return QueryIntent.ambiguous("Please provide a question to analyze.");
        }

        // Validate explicit datasource request if provided
        String targetDatasourceId = requestedDatasourceId != null && !requestedDatasourceId.isBlank()
                ? requestedDatasourceId.trim().toLowerCase()
                : null;

        if (targetDatasourceId != null && !datasourceRegistry.hasConnector(targetDatasourceId)) {
            throw new DatasourceNotFoundException("Requested datasource not found: " + targetDatasourceId);
        }

        // Check for trivial / ambiguous questions
        String lowerQuestion = question.trim().toLowerCase();
        if (isTriviallyAmbiguous(lowerQuestion)) {
            return QueryIntent.ambiguous(
                    "Your question is too broad or ambiguous. You can ask business analytics questions like: " +
                    "\"What was our monthly revenue trend?\", " +
                    "\"Which product categories generated the highest revenue?\", " +
                    "\"Show the monthly order count for the last six months\", " +
                    "\"Which sales opportunities are currently in the pipeline?\", or " +
                    "\"Compare the number of orders across order statuses.\""
            );
        }

        // Call Groq LLM for structured intent analysis
        String systemPrompt = """
                You are an enterprise Business Intelligence Intent Analyzer.
                Analyze the user's analytics question and extract structured intent.
                Available datasources:
                - 'ecommerce' (covers: orders, order items, products, revenue, sales, payments, online customers, categories, countries)
                - 'crm' (covers: leads, opportunities, deals, sales pipeline, stages, customer companies, industries, account activities, calls, meetings)

                Respond ONLY with a valid JSON object in this exact format:
                {
                  "datasourceId": "ecommerce" or "crm",
                  "businessMetric": "name of metric (e.g. revenue, order_count, pipeline_value, opportunity_amount)",
                  "dimensions": ["dimension1", "dimension2"],
                  "filters": ["filter description"],
                  "dateRange": "e.g. last 6 months or all",
                  "aggregation": "SUM" or "COUNT" or "AVG" or "MIN" or "MAX" or "NONE",
                  "analysisType": "TREND" or "BREAKDOWN" or "COMPARISON" or "PIPELINE" or "SUMMARY" or "LISTING",
                  "ambiguous": false,
                  "clarificationMessage": null
                }
                Notes on intent fields:
                - Use "aggregation": "NONE" when the user asks to list or inspect specific records rather than aggregate them (e.g., "Which sales opportunities are currently in the pipeline?").
                If the question cannot possibly be mapped to either ecommerce or crm, set "ambiguous": true with a helpful clarificationMessage.
                Return ONLY the raw JSON object without markdown fences or additional text.
                """;

        StringBuilder userPrompt = new StringBuilder();
        if (targetDatasourceId != null) {
            userPrompt.append("Forced Datasource: ").append(targetDatasourceId).append("\n");
        }
        if (sessionHistory != null && !sessionHistory.isBlank()) {
            userPrompt.append(sessionHistory).append("\n");
        }
        userPrompt.append("Current Question: ").append(question);

        try {
            String responseText = llmClientService.generate(systemPrompt, userPrompt.toString());
            QueryIntent parsed = parseIntentJson(responseText, targetDatasourceId);
            if (parsed != null) {
                // If caller specified datasource, preserve caller's selection
                if (targetDatasourceId != null && !targetDatasourceId.equalsIgnoreCase(parsed.datasourceId())) {
                    return new QueryIntent(
                            targetDatasourceId,
                            parsed.businessMetric(),
                            parsed.dimensions(),
                            parsed.filters(),
                            parsed.dateRange(),
                            parsed.aggregation(),
                            parsed.analysisType(),
                            parsed.ambiguous(),
                            parsed.clarificationMessage()
                    );
                }
                return parsed;
            }
        } catch (Exception e) {
            log.warn("LLM intent parsing failed, falling back to rule-based parser: {}", e.getMessage());
            // If LLM threw an explicit configuration error (missing key), rethrow it
            if (e instanceof com.analytics.exception.GroqConfigurationException) {
                throw e;
            }
        }

        return fallbackIntentAnalysis(question, targetDatasourceId);
    }

    private boolean isTriviallyAmbiguous(String q) {
        return q.matches("^(hi|hello|hey|help|test|what|why|who|analytics|data|show me|tell me|give me|info)\\??$")
                || q.length() < 4;
    }

    private QueryIntent parseIntentJson(String jsonText, String fallbackDatasource) {
        try {
            String cleanJson = jsonText.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceFirst("(?s)^```(?:json)?\\s*(.*?)\\s*```$", "$1").trim();
            }
            int startIdx = cleanJson.indexOf('{');
            int endIdx = cleanJson.lastIndexOf('}');
            if (startIdx >= 0 && endIdx > startIdx) {
                cleanJson = cleanJson.substring(startIdx, endIdx + 1);
            }

            JsonNode root = objectMapper.readTree(cleanJson);
            boolean ambiguous = root.path("ambiguous").asBoolean(false);
            String clarification = root.path("clarificationMessage").asText(null);

            if (ambiguous) {
                return QueryIntent.ambiguous(clarification != null ? clarification : "Could you clarify your analytics query?");
            }

            String ds = root.path("datasourceId").asText(fallbackDatasource != null ? fallbackDatasource : "ecommerce").toLowerCase();
            if (!datasourceRegistry.hasConnector(ds)) {
                ds = fallbackDatasource != null ? fallbackDatasource : "ecommerce";
            }

            String metric = root.path("businessMetric").asText("metric");
            List<String> dimensions = new ArrayList<>();
            if (root.path("dimensions").isArray()) {
                root.path("dimensions").forEach(d -> dimensions.add(d.asText()));
            }
            List<String> filters = new ArrayList<>();
            if (root.path("filters").isArray()) {
                root.path("filters").forEach(f -> filters.add(f.asText()));
            }
            String dateRange = root.path("dateRange").asText(null);
            String agg = root.path("aggregation").asText("COUNT");
            String analysis = root.path("analysisType").asText("SUMMARY");

            return new QueryIntent(ds, metric, dimensions, filters, dateRange, agg, analysis, false, null);
        } catch (Exception e) {
            log.debug("Could not parse LLM intent JSON: {}", e.getMessage());
            return null;
        }
    }

    private QueryIntent fallbackIntentAnalysis(String question, String targetDatasourceId) {
        String lower = question.toLowerCase();
        String ds = targetDatasourceId;
        if (ds == null) {
            if (lower.contains("opportunity") || lower.contains("opportunities") || lower.contains("lead")
                    || lower.contains("pipeline") || lower.contains("stage") || lower.contains("activity")
                    || lower.contains("crm")) {
                ds = "crm";
            } else {
                ds = "ecommerce";
            }
        }

        String metric = lower.contains("revenue") ? "revenue" : (lower.contains("order") ? "order_count" : "count");
        List<String> dims = new ArrayList<>();
        if (lower.contains("month") || lower.contains("trend")) dims.add("month");
        if (lower.contains("categor")) dims.add("category");
        if (lower.contains("status")) dims.add("status");
        if (lower.contains("industry")) dims.add("industry");
        if (lower.contains("stage")) dims.add("stage");

        String agg = lower.contains("revenue") || lower.contains("sum") || lower.contains("total") ? "SUM" : "COUNT";
        String analysis = lower.contains("trend") ? "TREND" : (lower.contains("compare") ? "COMPARISON" : "BREAKDOWN");

        return new QueryIntent(ds, metric, dims, List.of(), null, agg, analysis, false, null);
    }
}
