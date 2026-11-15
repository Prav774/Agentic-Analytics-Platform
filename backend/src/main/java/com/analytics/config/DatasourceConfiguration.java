package com.analytics.config;

import com.analytics.datasource.DatasourceConnector;
import com.analytics.datasource.McpDatasourceConnector;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class DatasourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConfiguration.class);

    @Bean
    public List<DatasourceConnector> datasourceConnectors(
            List<McpSyncClient> mcpSyncClients,
            ObjectMapper objectMapper) {
        List<DatasourceConnector> connectors = new ArrayList<>();

        if (mcpSyncClients != null) {
            for (McpSyncClient client : mcpSyncClients) {
                String title = client.getClientInfo() != null ? client.getClientInfo().title() : null;
                if (title == null && client.getClientInfo() != null && client.getClientInfo().name() != null) {
                    // Extract name suffix if title not present
                    String clientName = client.getClientInfo().name();
                    int dashIdx = clientName.lastIndexOf('-');
                    title = dashIdx >= 0 ? clientName.substring(dashIdx + 1).trim() : clientName;
                }
                if (title == null) {
                    log.warn("Discovered MCP client with no title or name; skipping connector registration.");
                    continue;
                }

                String id = title.toLowerCase();
                String name;
                String description;

                switch (id) {
                    case "crm" -> {
                        name = "CRM";
                        description = "Enterprise CRM PostgreSQL datasource";
                    }
                    case "ecommerce" -> {
                        name = "E-Commerce";
                        description = "Enterprise E-Commerce MySQL datasource";
                    }
                    default -> {
                        name = title.substring(0, 1).toUpperCase() + title.substring(1);
                        description = name + " MCP datasource";
                    }
                }

                log.info("Registering MCP datasource connector: id='{}', name='{}'", id, name);
                connectors.add(new McpDatasourceConnector(id, name, description, client, objectMapper));
            }
        }

        return connectors;
    }
}
