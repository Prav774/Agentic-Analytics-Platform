package com.analytics.agent;

import com.analytics.agent.agent.SqlGenerationAgent;
import com.analytics.agent.dto.QueryIntent;
import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.agent.service.LlmClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlGenerationAgentTest {

    @Mock
    private LlmClientService llmClientService;

    private SqlGenerationAgent agent;
    private DatasourceSchemaContext schemaContext;

    @BeforeEach
    void setUp() {
        agent = new SqlGenerationAgent(llmClientService);
        schemaContext = new DatasourceSchemaContext(
                "ecommerce",
                "E-Commerce",
                "MySQL",
                "mysql",
                List.of(new DatasourceSchemaContext.TableMeta(
                        "orders",
                        "enterprise_ecommerce",
                        List.of(new DatasourceSchemaContext.ColumnMeta("id", "BIGINT", true, false))
                )),
                List.of()
        );
    }

    @Test
    void extractsSqlFromMarkdownFencedResponse() {
        String mockResponse = "Here is the query:\n```sql\nSELECT * FROM orders;\n```\nEnjoy!";
        when(llmClientService.generate(anyString(), anyString())).thenReturn(mockResponse);

        QueryIntent intent = new QueryIntent("ecommerce", "all", List.of(), List.of(), null, "SELECT", "SUMMARY", false, null);
        String sql = agent.generateSql("Show all orders", intent, schemaContext, null);

        assertEquals("SELECT * FROM orders;", sql);
    }

    @Test
    void extractsSqlFromRawTextResponse() {
        String mockResponse = "SELECT order_status, COUNT(*) FROM orders GROUP BY order_status;";
        when(llmClientService.generate(anyString(), anyString())).thenReturn(mockResponse);

        QueryIntent intent = new QueryIntent("ecommerce", "count", List.of("order_status"), List.of(), null, "COUNT", "BREAKDOWN", false, null);
        String sql = agent.generateSql("Count orders by status", intent, schemaContext, null);

        assertEquals("SELECT order_status, COUNT(*) FROM orders GROUP BY order_status;", sql);
    }

    @Test
    void includesDistinctObservedValuesInSystemPrompt() {
        DatasourceSchemaContext crmContext = new DatasourceSchemaContext(
                "crm",
                "CRM",
                "PostgreSQL",
                "postgresql",
                List.of(new DatasourceSchemaContext.TableMeta(
                        "opportunities",
                        "public",
                        List.of(
                                new DatasourceSchemaContext.ColumnMeta("id", "BIGINT", true, false),
                                new DatasourceSchemaContext.ColumnMeta("opportunity_name", "VARCHAR", false, false),
                                new DatasourceSchemaContext.ColumnMeta(
                                        "stage",
                                        "VARCHAR",
                                        false,
                                        false,
                                        List.of("qualification", "discovery", "proposal", "negotiation", "closed_won", "closed_lost")
                                )
                        )
                )),
                List.of()
        );

        when(llmClientService.generate(anyString(), anyString()))
                .thenReturn("SELECT opportunity_name, stage, amount FROM opportunities WHERE stage NOT IN ('closed_won', 'closed_lost') ORDER BY amount DESC;");

        QueryIntent intent = new QueryIntent("crm", "pipeline", List.of("opportunity_name", "stage"), List.of(), null, "NONE", "PIPELINE", false, null);
        agent.generateSql("Which sales opportunities are currently in the pipeline?", intent, crmContext, null);

        ArgumentCaptor<String> sysPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClientService).generate(sysPromptCaptor.capture(), userPromptCaptor.capture());

        String capturedSysPrompt = sysPromptCaptor.getValue();
        assertTrue(capturedSysPrompt.contains("-- Distinct observed values: 'qualification', 'discovery', 'proposal', 'negotiation', 'closed_won', 'closed_lost'"),
                "System prompt must contain distinct observed values for stage");
        assertTrue(capturedSysPrompt.contains("stage NOT IN ('closed_won', 'closed_lost')"),
                "System prompt must contain guidance on open pipeline stage values");
    }

    @Test
    void generatesValidPipelineQueryExcludingClosedStagesWithoutMeaninglessGroupBy() {
        String mockResponse = """
                ```sql
                SELECT opportunity_name, stage, amount, expected_close_date
                FROM opportunities
                WHERE stage NOT IN ('closed_won', 'closed_lost')
                ORDER BY amount DESC;
                ```
                """;
        when(llmClientService.generate(anyString(), anyString())).thenReturn(mockResponse);

        QueryIntent intent = new QueryIntent("crm", "pipeline", List.of("opportunity_name", "stage", "amount"), List.of(), null, "NONE", "PIPELINE", false, null);
        String sql = agent.generateSql("Which sales opportunities are currently in the pipeline?", intent, schemaContext, null);

        assertNotNull(sql);
        assertTrue(sql.contains("WHERE stage NOT IN ('closed_won', 'closed_lost')"), "Must exclude closed_won and closed_lost");
        assertFalse(sql.contains("COUNT(*)"), "Listing query must not contain meaningless COUNT(*)");
        assertFalse(sql.contains("GROUP BY opportunity_name"), "Must not group by unique opportunity names");
    }
}
