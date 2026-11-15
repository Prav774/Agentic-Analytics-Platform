package com.analytics.datasource;

import com.analytics.exception.DatasourceOperationException;
import com.analytics.exception.DatasourceQueryException;
import com.analytics.exception.McpServerUnavailableException;
import com.analytics.exception.QueryTimeoutException;
import com.analytics.exception.TableNotFoundException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class McpDatasourceConnector implements DatasourceConnector {

    private static final Logger log = LoggerFactory.getLogger(McpDatasourceConnector.class);

    private final String id;
    private final String name;
    private final String description;
    private final McpSyncClient client;
    private final ObjectMapper objectMapper;

    public McpDatasourceConnector(
            String id,
            String name,
            String description,
            McpSyncClient client,
            ObjectMapper objectMapper) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isAvailable() {
        try {
            ensureInitialized();
            client.listTools();
            return true;
        } catch (Exception e) {
            log.debug("Datasource '{}' is currently unavailable: {}", id, e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> getAvailableTools() {
        try {
            ensureInitialized();
            McpSchema.ListToolsResult result = client.listTools();
            if (result != null && result.tools() != null) {
                return result.tools().stream()
                        .map(McpSchema.Tool::name)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not list tools for datasource '{}': {}", id, e.getMessage());
        }
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> listTables() {
        return invokeTool("list_tables", Collections.emptyMap());
    }

    @Override
    public Map<String, Object> getTableSchema(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("A valid table name is required.");
        }
        return invokeTool("get_table_schema", Map.of("tableName", tableName));
    }

    @Override
    public Map<String, Object> getRelationships() {
        return invokeTool("get_relationships", Collections.emptyMap());
    }

    @Override
    public Map<String, Object> executeQuery(String sql, Integer rowLimit) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query must not be empty.");
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("sql", sql);
        if (rowLimit != null) {
            arguments.put("rowLimit", rowLimit);
        }
        return invokeTool("execute_readonly_query", arguments);
    }

    public Map<String, Object> invokeTool(String toolName, Map<String, Object> arguments) {
        ensureInitialized();
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder(toolName)
                .arguments(arguments)
                .build();
        McpSchema.CallToolResult result;
        try {
            result = client.callTool(request);
        } catch (Exception ex) {
            handleClientException(ex);
            throw new DatasourceOperationException("Unexpected error during tool invocation: " + ex.getMessage(), ex);
        }

        if (result == null) {
            throw new DatasourceOperationException("MCP server returned null result for tool: " + toolName);
        }

        if (Boolean.TRUE.equals(result.isError())) {
            String errorMsg = extractErrorMessage(result);
            if (isTableNotFoundError(errorMsg)) {
                throw new TableNotFoundException(errorMsg);
            }
            if (isTimeoutError(errorMsg)) {
                throw new QueryTimeoutException(errorMsg);
            }
            throw new DatasourceQueryException(errorMsg);
        }

        return extractResultData(result);
    }

    private synchronized void ensureInitialized() {
        if (!client.isInitialized()) {
            try {
                log.info("Initializing MCP client connection for datasource '{}'...", id);
                client.initialize();
                log.info("MCP client for datasource '{}' initialized successfully.", id);
            } catch (Exception ex) {
                log.warn("Failed to initialize MCP client for datasource '{}': {}", id, ex.getMessage());
                if (isConnectionOrUnavailable(ex)) {
                    throw new McpServerUnavailableException("MCP server for datasource '" + id + "' is unavailable: " + extractRootMessage(ex), ex);
                }
                throw new DatasourceOperationException("Failed to initialize MCP client for datasource '" + id + "': " + ex.getMessage(), ex);
            }
        }
    }

    private Map<String, Object> extractResultData(McpSchema.CallToolResult result) {
        if (result.structuredContent() != null) {
            if (result.structuredContent() instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) map;
                return castMap;
            }
            try {
                return objectMapper.convertValue(result.structuredContent(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                log.debug("Failed to convert structuredContent to Map, attempting fallback: {}", ex.getMessage());
            }
        }

        if (result.content() != null && !result.content().isEmpty()) {
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent tc && tc.text() != null && !tc.text().isBlank()) {
                    try {
                        return objectMapper.readValue(tc.text(), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception ex) {
                        return Map.of("content", tc.text());
                    }
                }
            }
        }

        return Collections.emptyMap();
    }

    private String extractErrorMessage(McpSchema.CallToolResult result) {
        if (result.content() != null) {
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent tc && tc.text() != null && !tc.text().isBlank()) {
                    return tc.text();
                }
            }
        }
        return "MCP tool execution returned an unspecified error.";
    }

    private boolean isTableNotFoundError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("not available in the")
                || lower.contains("table is not available")
                || lower.contains("table not found")
                || lower.contains("valid crm table name is required")
                || lower.contains("valid e-commerce table name is required");
    }

    private boolean isTimeoutError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("timeout") || lower.contains("exceeded the configured timeout");
    }

    private void handleClientException(Exception ex) {
        if (isConnectionOrUnavailable(ex)) {
            throw new McpServerUnavailableException("MCP server for datasource '" + id + "' is unavailable: " + extractRootMessage(ex), ex);
        }
        if (isTimeoutException(ex)) {
            throw new QueryTimeoutException("Query for datasource '" + id + "' timed out: " + extractRootMessage(ex), ex);
        }
        throw new DatasourceOperationException("MCP tool call failed on datasource '" + id + "': " + extractRootMessage(ex), ex);
    }

    private boolean isConnectionOrUnavailable(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof IOException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("connection refused")
                        || lower.contains("unable to establish loopback connection")
                        || lower.contains("service unavailable")
                        || lower.contains("server is not available")
                        || lower.contains("failed to connect")
                        || lower.contains("session not found")
                        || lower.contains("permission denied")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTimeoutException(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            String msg = current.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
