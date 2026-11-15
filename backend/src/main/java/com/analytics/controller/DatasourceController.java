package com.analytics.controller;

import com.analytics.datasource.DatasourceConnector;
import com.analytics.datasource.DatasourceRegistry;
import com.analytics.datasource.DatasourceSummary;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {

    private static final Logger log = LoggerFactory.getLogger(DatasourceController.class);

    private final DatasourceRegistry datasourceRegistry;

    public DatasourceController(DatasourceRegistry datasourceRegistry) {
        this.datasourceRegistry = datasourceRegistry;
    }

    @GetMapping
    public ResponseEntity<List<DatasourceSummary>> listDatasources() {
        log.debug("Listing all registered datasources");
        List<DatasourceSummary> summaries = new ArrayList<>();
        for (DatasourceConnector connector : datasourceRegistry.getAllConnectors()) {
            boolean available = connector.isAvailable();
            String status = available ? "CONNECTED" : "UNAVAILABLE";
            List<String> tools = available ? connector.getAvailableTools() : List.of();
            summaries.add(new DatasourceSummary(
                    connector.getId(),
                    connector.getName(),
                    connector.getDescription(),
                    status,
                    tools
            ));
        }
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}/tables")
    public ResponseEntity<Map<String, Object>> listTables(@PathVariable("id") String id) {
        log.debug("Listing tables for datasource '{}'", id);
        DatasourceConnector connector = datasourceRegistry.getConnector(id);
        return ResponseEntity.ok(connector.listTables());
    }

    @GetMapping("/{id}/tables/{tableName}")
    public ResponseEntity<Map<String, Object>> getTableSchema(
            @PathVariable("id") String id,
            @PathVariable("tableName") String tableName) {
        log.debug("Fetching schema for table '{}' in datasource '{}'", tableName, id);
        DatasourceConnector connector = datasourceRegistry.getConnector(id);
        return ResponseEntity.ok(connector.getTableSchema(tableName));
    }

    @GetMapping("/{id}/relationships")
    public ResponseEntity<Map<String, Object>> getRelationships(@PathVariable("id") String id) {
        log.debug("Fetching relationships for datasource '{}'", id);
        DatasourceConnector connector = datasourceRegistry.getConnector(id);
        return ResponseEntity.ok(connector.getRelationships());
    }

    @PostMapping("/{id}/query")
    public ResponseEntity<Map<String, Object>> executeQuery(
            @PathVariable("id") String id,
            @Valid @RequestBody QueryRequest request) {
        log.debug("Executing read-only query on datasource '{}'", id);
        DatasourceConnector connector = datasourceRegistry.getConnector(id);
        return ResponseEntity.ok(connector.executeQuery(request.sql(), request.rowLimit()));
    }
}
