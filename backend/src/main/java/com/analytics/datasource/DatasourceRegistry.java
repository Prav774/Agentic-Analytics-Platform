package com.analytics.datasource;

import com.analytics.exception.DatasourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DatasourceRegistry {

    private final Map<String, DatasourceConnector> connectors = new LinkedHashMap<>();

    public DatasourceRegistry(List<DatasourceConnector> connectorList) {
        if (connectorList != null) {
            for (DatasourceConnector connector : connectorList) {
                register(connector);
            }
        }
    }

    public synchronized void register(DatasourceConnector connector) {
        connectors.put(connector.getId().toLowerCase(), connector);
    }

    public DatasourceConnector getConnector(String id) {
        if (id == null) {
            throw new DatasourceNotFoundException("Datasource ID must not be null.");
        }
        DatasourceConnector connector = connectors.get(id.toLowerCase());
        if (connector == null) {
            throw new DatasourceNotFoundException("Datasource not found: " + id);
        }
        return connector;
    }

    public Collection<DatasourceConnector> getAllConnectors() {
        return Collections.unmodifiableCollection(connectors.values());
    }

    public boolean hasConnector(String id) {
        return id != null && connectors.containsKey(id.toLowerCase());
    }
}
