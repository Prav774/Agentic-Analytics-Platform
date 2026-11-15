package com.analytics;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnalyticsApplicationTests {

    @Autowired(required = false)
    private List<McpSyncClient> mcpSyncClients;

    @Test
    void contextLoads() {
        assertThat(mcpSyncClients).isNotNull();
        assertThat(mcpSyncClients).hasSize(2);
        for (McpSyncClient client : mcpSyncClients) {
            System.out.println("Discovered client: " + client.getClientInfo());
        }
    }
}
