package com.analytics.controller;

import com.analytics.agent.dto.AnalyticsQueryRequest;
import com.analytics.agent.dto.AnalyticsQueryResponse;
import com.analytics.agent.orchestrator.AnalyticsOrchestrator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsOrchestrator analyticsOrchestrator;

    public AnalyticsController(AnalyticsOrchestrator analyticsOrchestrator) {
        this.analyticsOrchestrator = analyticsOrchestrator;
    }

    @PostMapping("/query")
    public ResponseEntity<AnalyticsQueryResponse> executeAgenticQuery(
            @Valid @RequestBody AnalyticsQueryRequest request) {
        log.info("Received natural-language analytics question: '{}' [datasource='{}', session='{}']",
                request.question(), request.datasourceId(), request.sessionId());

        AnalyticsQueryResponse response = analyticsOrchestrator.processQuery(request);
        return ResponseEntity.ok(response);
    }
}
