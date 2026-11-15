package com.analytics.controller;

import com.analytics.datasource.DatasourceConnector;
import com.analytics.datasource.DatasourceRegistry;
import com.analytics.exception.DatasourceNotFoundException;
import com.analytics.exception.DatasourceQueryException;
import com.analytics.exception.GlobalExceptionHandler;
import com.analytics.exception.McpServerUnavailableException;
import com.analytics.exception.QueryTimeoutException;
import com.analytics.exception.TableNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DatasourceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DatasourceRegistry datasourceRegistry;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DatasourceController(datasourceRegistry))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listDatasourcesReturnsAllSummaries() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(crm.getId()).thenReturn("crm");
        when(crm.getName()).thenReturn("CRM");
        when(crm.getDescription()).thenReturn("Enterprise CRM PostgreSQL datasource");
        when(crm.isAvailable()).thenReturn(true);
        when(crm.getAvailableTools()).thenReturn(List.of("list_tables", "get_table_schema", "get_relationships", "execute_readonly_query"));

        DatasourceConnector ecommerce = mock(DatasourceConnector.class);
        when(ecommerce.getId()).thenReturn("ecommerce");
        when(ecommerce.getName()).thenReturn("E-Commerce");
        when(ecommerce.getDescription()).thenReturn("Enterprise E-Commerce MySQL datasource");
        when(ecommerce.isAvailable()).thenReturn(false);

        when(datasourceRegistry.getAllConnectors()).thenReturn(List.of(crm, ecommerce));

        mockMvc.perform(get("/api/datasources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("crm"))
                .andExpect(jsonPath("$[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$[0].tools.length()").value(4))
                .andExpect(jsonPath("$[1].id").value("ecommerce"))
                .andExpect(jsonPath("$[1].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$[1].tools.length()").value(0));
    }

    @Test
    void listTablesReturnsStructuredTables() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.listTables()).thenReturn(Map.of(
                "schema", "public",
                "tables", List.of(Map.of("schema", "public", "tableName", "customers"))
        ));

        mockMvc.perform(get("/api/datasources/crm/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schema").value("public"))
                .andExpect(jsonPath("$.tables[0].tableName").value("customers"));
    }

    @Test
    void getTableSchemaReturnsTableColumns() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.getTableSchema("customers")).thenReturn(Map.of(
                "schema", "public",
                "tableName", "customers",
                "columns", List.of(Map.of("name", "id", "type", "int4", "primaryKey", true))
        ));

        mockMvc.perform(get("/api/datasources/crm/tables/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("customers"))
                .andExpect(jsonPath("$.columns[0].name").value("id"));
    }

    @Test
    void getRelationshipsReturnsForeignKeys() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.getRelationships()).thenReturn(Map.of(
                "schema", "public",
                "relationships", List.of(Map.of(
                        "constraintName", "fk_leads_customer",
                        "sourceTable", "leads",
                        "targetTable", "customers"
                ))
        ));

        mockMvc.perform(get("/api/datasources/crm/relationships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships[0].sourceTable").value("leads"));
    }

    @Test
    void executeQueryReturnsQueryResults() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.executeQuery("SELECT * FROM customers", 5)).thenReturn(Map.of(
                "columns", List.of("id", "name"),
                "rows", List.of(Map.of("id", 1, "name", "Acme")),
                "rowCount", 1,
                "truncated", false
        ));

        mockMvc.perform(post("/api/datasources/crm/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\": \"SELECT * FROM customers\", \"rowLimit\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.rows[0].name").value("Acme"));
    }

    @Test
    void returns404WhenDatasourceNotFound() throws Exception {
        when(datasourceRegistry.getConnector("unknown"))
                .thenThrow(new DatasourceNotFoundException("Datasource not found: unknown"));

        mockMvc.perform(get("/api/datasources/unknown/tables"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Datasource not found: unknown"));
    }

    @Test
    void returns404WhenTableNotFound() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.getTableSchema("nonexistent"))
                .thenThrow(new TableNotFoundException("Table is not available in the CRM public schema: nonexistent"));

        mockMvc.perform(get("/api/datasources/crm/tables/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Table is not available in the CRM public schema: nonexistent"));
    }

    @Test
    void returns400WhenQueryIsBlank() throws Exception {
        mockMvc.perform(post("/api/datasources/crm/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void returns400WhenQueryViolatesProtections() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.executeQuery(eq("DELETE FROM customers"), any()))
                .thenThrow(new DatasourceQueryException("Only SELECT statements are allowed."));

        mockMvc.perform(post("/api/datasources/crm/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\": \"DELETE FROM customers\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Only SELECT statements are allowed."));
    }

    @Test
    void returns503WhenServerIsUnavailable() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.listTables())
                .thenThrow(new McpServerUnavailableException("MCP server for datasource 'crm' is unavailable: Connection refused"));

        mockMvc.perform(get("/api/datasources/crm/tables"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("MCP server for datasource 'crm' is unavailable: Connection refused"));
    }

    @Test
    void returns504WhenQueryTimesOut() throws Exception {
        DatasourceConnector crm = mock(DatasourceConnector.class);
        when(datasourceRegistry.getConnector("crm")).thenReturn(crm);
        when(crm.executeQuery(eq("SELECT pg_sleep(20)"), any()))
                .thenThrow(new QueryTimeoutException("Query for datasource 'crm' timed out."));

        mockMvc.perform(post("/api/datasources/crm/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\": \"SELECT pg_sleep(20)\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.message").value("Query for datasource 'crm' timed out."));
    }
}
