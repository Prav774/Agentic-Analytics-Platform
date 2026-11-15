package com.analytics.datasource;

import com.analytics.exception.DatasourceNotFoundException;
import com.analytics.exception.DatasourceQueryException;
import com.analytics.exception.McpServerUnavailableException;
import com.analytics.exception.QueryTimeoutException;
import com.analytics.exception.TableNotFoundException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpDatasourceConnectorTest {

    @Mock
    private McpSyncClient client;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private McpDatasourceConnector connector;

    @BeforeEach
    void setUp() {
        connector = new McpDatasourceConnector("crm", "CRM", "Test CRM", client, objectMapper);
    }

    @Test
    void discoversToolsDynamically() {
        when(client.isInitialized()).thenReturn(true);
        McpSchema.Tool t1 = McpSchema.Tool.builder("list_tables").build();
        McpSchema.Tool t2 = McpSchema.Tool.builder("execute_readonly_query").build();
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(t1, t2), null, null));

        List<String> tools = connector.getAvailableTools();
        assertThat(tools).containsExactly("list_tables", "execute_readonly_query");
    }

    @Test
    void executesListTablesSuccessfullyWithStructuredContent() {
        when(client.isInitialized()).thenReturn(true);
        Map<String, Object> tableData = Map.of(
                "schema", "public",
                "tables", List.of(Map.of("schema", "public", "tableName", "customers"))
        );
        McpSchema.CallToolResult callResult = McpSchema.CallToolResult.builder()
                .structuredContent(tableData)
                .isError(false)
                .build();
        when(client.callTool(any())).thenReturn(callResult);

        Map<String, Object> result = connector.listTables();
        assertThat(result).isEqualTo(tableData);
    }

    @Test
    void executesListTablesSuccessfullyWithTextContent() {
        when(client.isInitialized()).thenReturn(true);
        String jsonText = "{\"schema\":\"public\",\"tables\":[{\"tableName\":\"leads\"}]}";
        McpSchema.CallToolResult callResult = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(jsonText)))
                .isError(false)
                .build();
        when(client.callTool(any())).thenReturn(callResult);

        Map<String, Object> result = connector.listTables();
        assertThat(result).containsKey("schema");
        assertThat(result.get("schema")).isEqualTo("public");
    }

    @Test
    void translatesTableNotFoundErrorToTableNotFoundException() {
        when(client.isInitialized()).thenReturn(true);
        McpSchema.CallToolResult callResult = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("Table is not available in the CRM public schema: nonexistent")))
                .isError(true)
                .build();
        when(client.callTool(any())).thenReturn(callResult);

        assertThatThrownBy(() -> connector.getTableSchema("nonexistent"))
                .isInstanceOf(TableNotFoundException.class)
                .hasMessageContaining("not available in the CRM public schema");
    }

    @Test
    void translatesQueryTimeoutErrorToQueryTimeoutException() {
        when(client.isInitialized()).thenReturn(true);
        McpSchema.CallToolResult callResult = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("CRM query exceeded the configured timeout.")))
                .isError(true)
                .build();
        when(client.callTool(any())).thenReturn(callResult);

        assertThatThrownBy(() -> connector.executeQuery("SELECT pg_sleep(5)", 10))
                .isInstanceOf(QueryTimeoutException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void translatesQueryRejectionToDatasourceQueryException() {
        when(client.isInitialized()).thenReturn(true);
        McpSchema.CallToolResult callResult = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("Only SELECT statements are allowed.")))
                .isError(true)
                .build();
        when(client.callTool(any())).thenReturn(callResult);

        assertThatThrownBy(() -> connector.executeQuery("DELETE FROM customers", null))
                .isInstanceOf(DatasourceQueryException.class)
                .hasMessageContaining("Only SELECT statements are allowed.");
    }

    @Test
    void handlesServerOutageGracefullyWithoutCrashing() {
        when(client.isInitialized()).thenReturn(false);
        when(client.initialize()).thenThrow(new RuntimeException("Connection refused", new ConnectException("Connection refused")));

        assertThat(connector.isAvailable()).isFalse();

        assertThatThrownBy(() -> connector.listTables())
                .isInstanceOf(McpServerUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void validatesBlankTableNameAndQuery() {
        assertThatThrownBy(() -> connector.getTableSchema(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.executeQuery("", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
