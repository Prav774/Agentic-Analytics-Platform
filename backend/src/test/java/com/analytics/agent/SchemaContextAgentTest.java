package com.analytics.agent;

import com.analytics.agent.agent.SchemaContextAgent;
import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.datasource.DatasourceConnector;
import com.analytics.datasource.DatasourceRegistry;
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
class SchemaContextAgentTest {

    @Mock
    private DatasourceRegistry datasourceRegistry;

    @Mock
    private DatasourceConnector connector;

    private SchemaContextAgent schemaContextAgent;

    @BeforeEach
    void setUp() {
        schemaContextAgent = new SchemaContextAgent(datasourceRegistry);
    }

    @Test
    void samplesDistinctValuesForCategoricalColumns() {
        when(datasourceRegistry.getConnector("crm")).thenReturn(connector);
        when(connector.isAvailable()).thenReturn(true);
        when(connector.getId()).thenReturn("crm");
        when(connector.getName()).thenReturn("CRM");

        when(connector.listTables()).thenReturn(Map.of("tables", List.of(Map.of("tableName", "opportunities"))));
        when(connector.getTableSchema("opportunities")).thenReturn(Map.of(
                "schema", "public",
                "tableName", "opportunities",
                "columns", List.of(
                        Map.of("name", "id", "type", "BIGINT", "primaryKey", true, "nullable", false),
                        Map.of("name", "opportunity_name", "type", "VARCHAR", "primaryKey", false, "nullable", false),
                        Map.of("name", "stage", "type", "VARCHAR", "primaryKey", false, "nullable", false)
                )
        ));
        when(connector.getRelationships()).thenReturn(Map.of("relationships", List.of()));

        // Mock distinct stage query
        when(connector.executeQuery(contains("stage"), eq(15))).thenReturn(Map.of(
                "rows", List.of(
                        Map.of("stage", "qualification"),
                        Map.of("stage", "discovery"),
                        Map.of("stage", "proposal"),
                        Map.of("stage", "negotiation"),
                        Map.of("stage", "closed_won"),
                        Map.of("stage", "closed_lost")
                )
        ));

        DatasourceSchemaContext context = schemaContextAgent.getSchemaContext("crm");

        assertNotNull(context);
        DatasourceSchemaContext.TableMeta oppTable = context.tables().get(0);
        DatasourceSchemaContext.ColumnMeta stageCol = oppTable.columns().stream()
                .filter(c -> "stage".equals(c.name()))
                .findFirst().orElseThrow();

        assertEquals(6, stageCol.distinctValues().size());
        assertTrue(stageCol.distinctValues().contains("closed_won"));
        assertTrue(stageCol.distinctValues().contains("closed_lost"));

        String ddl = context.toDdlSummary();
        assertTrue(ddl.contains("-- Distinct observed values: 'qualification', 'discovery', 'proposal', 'negotiation', 'closed_won', 'closed_lost'"));
    }

    @Test
    void skipsSamplingForNonCategoricalColumns() {
        when(datasourceRegistry.getConnector("crm")).thenReturn(connector);
        when(connector.isAvailable()).thenReturn(true);
        when(connector.getId()).thenReturn("crm");
        when(connector.getName()).thenReturn("CRM");

        when(connector.listTables()).thenReturn(Map.of("tables", List.of(Map.of("tableName", "customers"))));
        when(connector.getTableSchema("customers")).thenReturn(Map.of(
                "schema", "public",
                "tableName", "customers",
                "columns", List.of(
                        Map.of("name", "id", "type", "BIGINT", "primaryKey", true, "nullable", false),
                        Map.of("name", "email", "type", "VARCHAR", "primaryKey", false, "nullable", false),
                        Map.of("name", "created_at", "type", "TIMESTAMPTZ", "primaryKey", false, "nullable", false)
                )
        ));
        when(connector.getRelationships()).thenReturn(Map.of("relationships", List.of()));

        DatasourceSchemaContext context = schemaContextAgent.getSchemaContext("crm");

        assertNotNull(context);
        verify(connector, never()).executeQuery(anyString(), anyInt());
    }

    @Test
    void handlesConnectorExceptionGracefullyDuringSampling() {
        when(datasourceRegistry.getConnector("crm")).thenReturn(connector);
        when(connector.isAvailable()).thenReturn(true);
        when(connector.getId()).thenReturn("crm");
        when(connector.getName()).thenReturn("CRM");

        when(connector.listTables()).thenReturn(Map.of("tables", List.of(Map.of("tableName", "opportunities"))));
        when(connector.getTableSchema("opportunities")).thenReturn(Map.of(
                "schema", "public",
                "tableName", "opportunities",
                "columns", List.of(
                        Map.of("name", "stage", "type", "VARCHAR", "primaryKey", false, "nullable", false)
                )
        ));
        when(connector.getRelationships()).thenReturn(Map.of("relationships", List.of()));

        // Simulate error during query
        when(connector.executeQuery(anyString(), anyInt())).thenThrow(new RuntimeException("Database timeout"));

        DatasourceSchemaContext context = schemaContextAgent.getSchemaContext("crm");

        assertNotNull(context);
        DatasourceSchemaContext.ColumnMeta stageCol = context.tables().get(0).columns().get(0);
        assertTrue(stageCol.distinctValues().isEmpty(), "Distinct values must fall back to empty list on error");
    }
}
