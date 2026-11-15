package com.analytics.datasource;

import java.util.List;
import java.util.Map;

public interface DatasourceConnector {
    String getId();
    String getName();
    String getDescription();
    boolean isAvailable();
    List<String> getAvailableTools();
    Map<String, Object> listTables();
    Map<String, Object> getTableSchema(String tableName);
    Map<String, Object> getRelationships();
    Map<String, Object> executeQuery(String sql, Integer rowLimit);
}
