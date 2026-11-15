package com.analytics.agent.agent;

import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.agent.service.LlmClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SqlGenerationAgent {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerationAgent.class);
    private static final Pattern SQL_BLOCK_PATTERN = Pattern.compile("(?is)```(?:sql)?\\s*(SELECT\\b.*?)\\s*```");

    private final LlmClientService llmClientService;

    public SqlGenerationAgent(LlmClientService llmClientService) {
        this.llmClientService = llmClientService;
    }

    public String generateSql(
            String question,
            QueryIntent intent,
            DatasourceSchemaContext schemaContext,
            String sessionHistory) {

        String dialect = schemaContext.dialect();
        String systemPrompt = buildSystemPrompt(schemaContext);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Database: ").append(schemaContext.datasourceName())
                .append(" (").append(schemaContext.databaseEngine()).append(")\n");

        if (sessionHistory != null && !sessionHistory.isBlank()) {
            userPrompt.append(sessionHistory).append("\n");
        }

        userPrompt.append("User Question: ").append(question).append("\n");
        if (intent != null) {
            userPrompt.append("Parsed Intent: Metric=").append(intent.businessMetric())
                    .append(", Dimensions=").append(intent.dimensions())
                    .append(", Aggregation=").append(intent.aggregation())
                    .append(", AnalysisType=").append(intent.analysisType()).append("\n");
        }

        userPrompt.append("\nGenerate the exact read-only SQL query for ").append(schemaContext.databaseEngine()).append(":");

        log.debug("Generating SQL for question '{}' on datasource '{}'", question, schemaContext.datasourceId());
        String response = llmClientService.generate(systemPrompt, userPrompt.toString());

        String cleanedSql = extractSql(response);
        log.info("Generated SQL for datasource '{}': {}", schemaContext.datasourceId(), cleanedSql);
        return cleanedSql;
    }

    private String buildSystemPrompt(DatasourceSchemaContext schemaContext) {
        boolean isPostgres = "postgresql".equalsIgnoreCase(schemaContext.dialect());

        return """
                You are an expert SQL Generator for enterprise analytics.
                Your task is to generate a single, highly-optimized, read-only SELECT statement.

                Target Dialect: %s
                Available Schema:
                %s

                CRITICAL RULES:
                1. Output ONLY ONE valid SELECT statement.
                2. Do NOT generate any INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE, or DDL/DML statements.
                3. Use ONLY table and column names explicitly defined in the Available Schema above. Never hallucinate or invent tables or columns.
                4. For joins, use the foreign key relationships provided.
                5. Dialect details:
                   %s
                6. Always provide clean column aliases (e.g. 'AS revenue', 'AS order_count', 'AS month').
                7. Order the results logically (e.g. by date ascending for trends, or by metric descending for rankings).
                8. Exact Categorical Values: When filtering or grouping on columns with '-- Distinct observed values' in the schema, you MUST use the exact string literals and casing from the schema (e.g., if distinct values for stage are 'closed_won', 'closed_lost', write `stage NOT IN ('closed_won', 'closed_lost')`; never invent PascalCase like 'Closed Won' or 'Closed Lost').
                9. Domain Pipeline Rules: For sales pipeline queries ('sales opportunities in the pipeline', 'open pipeline'), the pipeline includes opportunities that are currently active/open. Exclude closed opportunities using the schema's exact values (e.g. `stage NOT IN ('closed_won', 'closed_lost')` or `stage IN ('qualification', 'discovery', 'proposal', 'negotiation')`).
                10. Meaningful Grouping:
                    - When the question asks for individual items or records (e.g. 'Which sales opportunities are currently in the pipeline?'), SELECT the descriptive attributes (e.g. `opportunity_name, stage, amount, expected_close_date`) and order them appropriately (e.g. `ORDER BY amount DESC`). Do NOT add a meaningless `GROUP BY` on unique item names with `COUNT(*)`.
                    - Only use `GROUP BY` when aggregating multiple records per category/group (e.g. `GROUP BY stage`, `GROUP BY category`, `GROUP BY month`).
                11. If calculating total order item revenue in MySQL, note that order_items has quantity and unit_price, so use SUM(quantity * unit_price).
                12. Return ONLY the raw SQL query. Do not wrap in markdown quotes if possible, or use standard ```sql block.
                """.formatted(
                schemaContext.databaseEngine(),
                schemaContext.toDdlSummary(),
                isPostgres
                        ? "- For monthly aggregation, use TO_CHAR(created_at, 'YYYY-MM') AS month.\n   - PostgreSQL identifiers are case-sensitive if quoted; prefer unquoted standard names.\n   - For relative recency filters like 'last 6 months', anchor to the table's latest date: (SELECT MAX(created_at) - INTERVAL '6 MONTH' FROM ...)."
                        : "- For monthly aggregation, use DATE_FORMAT(order_date, '%Y-%m') AS month.\n   - For relative recency filters like 'last 6 months', anchor to the table's latest date: DATE_SUB((SELECT MAX(order_date) FROM orders), INTERVAL 6 MONTH)."
        );
    }

    private String extractSql(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalArgumentException("LLM returned an empty SQL response.");
        }

        String trimmed = rawResponse.trim();

        // 1. Check for markdown code fence ```sql ... ```
        Matcher matcher = SQL_BLOCK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return cleanSqlString(matcher.group(1));
        }

        // 2. Check for generic ``` ... ```
        if (trimmed.startsWith("```")) {
            String stripped = trimmed.replaceFirst("(?s)^```(?:sql)?\\s*(.*?)\\s*```$", "$1").trim();
            if (stripped.toUpperCase().startsWith("SELECT")) {
                return cleanSqlString(stripped);
            }
        }

        // 3. Extract text starting with SELECT
        int selectIdx = trimmed.toUpperCase().indexOf("SELECT");
        if (selectIdx >= 0) {
            String sqlPart = trimmed.substring(selectIdx);
            // Cut off if there is explanatory text after semicolon
            int semiIdx = sqlPart.indexOf(';');
            if (semiIdx >= 0) {
                sqlPart = sqlPart.substring(0, semiIdx + 1);
            }
            return cleanSqlString(sqlPart);
        }

        return cleanSqlString(trimmed);
    }

    private String cleanSqlString(String sql) {
        String clean = sql.trim();
        // Remove trailing semicolons for consistency (or keep single semicolon)
        if (clean.endsWith(";")) {
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        return clean + ";";
    }
}
