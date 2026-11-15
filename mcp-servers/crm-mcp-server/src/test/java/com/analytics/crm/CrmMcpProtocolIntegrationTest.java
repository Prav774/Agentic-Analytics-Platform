package com.analytics.crm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"analytics.query.timeout-seconds=1", "analytics.query.max-rows=100"})
class CrmMcpProtocolIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private String sessionId;
    private String protocolVersion;

    @BeforeEach
    void initializeMcpSession() throws Exception {
        ObjectNode params = JSON.createObjectNode();
        params.put("protocolVersion", "2025-03-26");
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "crm-integration-tests").put("version", "1.0");
        JsonNode response = post("initialize", params, 1);
        assertThat(response.has("error")).isFalse();
        protocolVersion = response.path("result").path("protocolVersion").asText("2025-03-26");
        ObjectNode notification = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", "notifications/initialized");
        HttpRequest initialized = request("notifications/initialized", notification, null).build();
        HttpResponse<String> initializedResponse = http.send(initialized, HttpResponse.BodyHandlers.ofString());
        assertThat(initializedResponse.statusCode()).withFailMessage("MCP initialized notification returned HTTP %s: %s", initializedResponse.statusCode(), initializedResponse.body()).isBetween(200, 299);
    }

    @Test
    void registersToolsAndListsDatabaseTables() throws Exception {
        JsonNode list = post("tools/list", JSON.createObjectNode(), 2).path("result").path("tools");
        assertThat(toolNames(list)).containsExactlyInAnyOrder("list_tables", "get_table_schema", "get_relationships", "execute_readonly_query");
        JsonNode result = call("list_tables", Map.of());
        assertSuccess(result);
        assertThat(toolData(result).path("tables").toString()).contains("customers", "leads", "opportunities", "activities");
    }

    @Test
    void returnsTableColumnsAndForeignKeyRelationships() throws Exception {
        JsonNode schema = call("get_table_schema", Map.of("tableName", "customers"));
        assertSuccess(schema);
        assertThat(toolData(schema).path("columns").toString()).contains("id", "email", "industry");

        JsonNode relationships = call("get_relationships", Map.of());
        assertSuccess(relationships);
        assertThat(toolData(relationships).path("relationships").toString()).contains("leads", "opportunities", "activities", "customers");
    }

    @Test
    void executesSelectAndHonorsRequestedRowLimit() throws Exception {
        JsonNode result = call("execute_readonly_query", Map.of("sql", "SELECT c.id, c.name, c.created_at FROM public.customers c ORDER BY c.id", "rowLimit", 2));
        assertSuccess(result);
        JsonNode data = toolData(result);
        assertThat(data.path("rowCount").asInt()).isEqualTo(2);
        assertThat(data.path("rows").size()).isEqualTo(2);
        assertThat(data.path("truncated").asBoolean()).isTrue();
    }

    @Test
    void rejectsDmlAndMultipleStatements() throws Exception {
        for (String sql : List.of(
                "INSERT INTO customers(name,email,company,industry) VALUES ('x','x@example.test','x','Retail')",
                "UPDATE customers SET name='x' WHERE id=1",
                "DELETE FROM customers WHERE id=1",
                "CREATE TABLE unauthorized_write_test(id INTEGER)",
                "SELECT id FROM customers; SELECT id FROM customers")) {
            assertToolError(call("execute_readonly_query", Map.of("sql", sql, "rowLimit", 10)));
        }
    }

    @Test
    void rejectsUnauthorizedSchemasAndEnforcesQueryTimeout() throws Exception {
        assertToolError(call("execute_readonly_query", Map.of("sql", "SELECT * FROM pg_catalog.pg_tables", "rowLimit", 10)));
        assertTimeoutError(call("execute_readonly_query", Map.of("sql", "SELECT pg_sleep(3)", "rowLimit", 10)));
    }

    private JsonNode call(String tool, Map<String, ?> arguments) throws Exception {
        ObjectNode params = JSON.createObjectNode();
        params.put("name", tool);
        params.set("arguments", JSON.valueToTree(arguments));
        return post("tools/call", params, 3).path("result");
    }

    private JsonNode post(String method, JsonNode params, Integer id) throws Exception {
        ObjectNode body = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method);
        if (id != null) body.put("id", id);
        body.set("params", params);
        HttpResponse<String> response = http.send(request(method, body, id).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).withFailMessage("MCP %s returned HTTP %s: %s", method, response.statusCode(), response.body()).isBetween(200, 299);
        if (method.equals("initialize")) sessionId = response.headers().firstValue("Mcp-Session-Id").orElse(null);
        return response.body().isBlank() ? JSON.createObjectNode() : parseResponse(response.body());
    }

    private HttpRequest.Builder request(String method, JsonNode body, Integer id) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (!method.equals("initialize") && protocolVersion != null) builder.header("Mcp-Protocol-Version", protocolVersion);
        if (!method.equals("initialize") && sessionId != null) builder.header("Mcp-Session-Id", sessionId);
        return builder;
    }

    private JsonNode parseResponse(String body) throws Exception {
        List<String> dataLines = body.lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5).trim()).toList();
        if (dataLines.isEmpty()) return JSON.readTree(body);
        String data = String.join("\n", dataLines);
        return JSON.readTree(data);
    }

    private List<String> toolNames(JsonNode tools) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        tools.forEach(tool -> names.add(tool.path("name").asText()));
        return names;
    }

    private JsonNode toolData(JsonNode result) throws Exception {
        JsonNode structured = result.path("structuredContent");
        if (!structured.isMissingNode() && !structured.isNull()) return structured;
        String text = result.path("content").path(0).path("text").asText();
        return objectMapper.readTree(text);
    }

    private void assertSuccess(JsonNode result) {
        assertThat(result.path("isError").asBoolean(false)).isFalse();
    }

    private void assertToolError(JsonNode result) {
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").toString()).isNotBlank();
    }

    private void assertTimeoutError(JsonNode result) {
        assertToolError(result);
        assertThat(result.path("content").toString()).containsIgnoringCase("timeout");
    }
}
