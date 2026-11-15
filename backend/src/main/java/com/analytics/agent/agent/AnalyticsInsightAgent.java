package com.analytics.agent.agent;

import com.analytics.agent.dto.EvidenceInsight;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.service.LlmClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AnalyticsInsightAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsInsightAgent.class);

    private final LlmClientService llmClientService;
    private final ObjectMapper objectMapper;

    public AnalyticsInsightAgent(LlmClientService llmClientService, ObjectMapper objectMapper) {
        this.llmClientService = llmClientService;
        this.objectMapper = objectMapper;
    }

    public EvidenceInsight generateInsights(
            String question,
            QueryIntent intent,
            String sql,
            List<String> columns,
            List<Map<String, Object>> rows) {

        if (rows == null || rows.isEmpty()) {
            return EvidenceInsight.empty("No data records were returned for this query. Try adjusting filters or date ranges.");
        }

        String systemPrompt = """
                You are an enterprise Business Intelligence Analytics Insights Agent.
                Analyze the ACTUAL query results returned from the business database.

                STRICT EVIDENCE RULES:
                1. Cite EXACT numbers, categories, and metrics from the provided data rows.
                2. NEVER fabricate, extrapolate, or invent figures, percentages, or causes not present in the data.
                3. Clearly distinguish measured facts from recommendations.

                Output format must be a single valid JSON object:
                {
                  "summary": "1-2 sentence executive summary of the finding with actual numbers",
                  "observations": [
                    "Fact 1 with exact numbers from the data",
                    "Fact 2 with exact numbers from the data"
                  ],
                  "trends": [
                    "Observed trend across dates/sequence (if applicable, else empty array)"
                  ],
                  "comparisons": [
                    "Comparison between categories or stages (if applicable, else empty array)"
                  ],
                  "recommendations": [
                    "Strategic recommendation derived from the evidence"
                  ]
                }
                Return ONLY the raw JSON object.
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("User Question: ").append(question).append("\n");
        userPrompt.append("Executed SQL: ").append(sql).append("\n");
        userPrompt.append("Total Rows Returned: ").append(rows.size()).append("\n");
        userPrompt.append("Columns: ").append(columns).append("\n\n");
        userPrompt.append("Actual Returned Data (First ").append(Math.min(rows.size(), 30)).append(" rows):\n");

        for (int i = 0; i < Math.min(rows.size(), 30); i++) {
            userPrompt.append(rows.get(i)).append("\n");
        }

        try {
            String response = llmClientService.generate(systemPrompt, userPrompt.toString());
            EvidenceInsight parsed = parseInsightsJson(response);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("LLM insights generation failed, using rule-based synthesis: {}", e.getMessage());
            if (e instanceof com.analytics.exception.GroqConfigurationException) {
                throw e;
            }
        }

        return fallbackInsights(columns, rows);
    }

    private EvidenceInsight parseInsightsJson(String jsonText) {
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
            String summary = root.path("summary").asText("Analysis completed based on returned query records.");

            List<String> observations = extractStringList(root.path("observations"));
            List<String> trends = extractStringList(root.path("trends"));
            List<String> comparisons = extractStringList(root.path("comparisons"));
            List<String> recommendations = extractStringList(root.path("recommendations"));

            return new EvidenceInsight(summary, observations, trends, comparisons, recommendations);
        } catch (Exception e) {
            log.debug("Failed to parse insights JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<String> extractStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String txt = item.asText();
                if (txt != null && !txt.isBlank()) {
                    list.add(txt.trim());
                }
            });
        }
        return list;
    }

    private EvidenceInsight fallbackInsights(List<String> columns, List<Map<String, Object>> rows) {
        List<String> observations = new ArrayList<>();
        observations.add("Query returned " + rows.size() + " data records across columns: " + String.join(", ", columns));

        if (!rows.isEmpty()) {
            Map<String, Object> firstRow = rows.get(0);
            observations.add("Primary record: " + firstRow);
        }

        String summary = "Retrieved " + rows.size() + " records from the database.";
        List<String> recommendations = List.of("Review the detailed metrics in the data table below.");

        return new EvidenceInsight(summary, observations, List.of(), List.of(), recommendations);
    }
}
