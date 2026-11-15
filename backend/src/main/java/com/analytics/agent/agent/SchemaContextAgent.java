package com.analytics.agent.agent;

import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.datasource.DatasourceConnector;
import com.analytics.datasource.DatasourceRegistry;
import com.analytics.exception.McpServerUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SchemaContextAgent {

    private static final Logger log = LoggerFactory.getLogger(SchemaContextAgent.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final DatasourceRegistry datasourceRegistry;
    private final Map<String, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    public SchemaContextAgent(DatasourceRegistry datasourceRegistry) {
        this.datasourceRegistry = datasourceRegistry;
    }

    public DatasourceSchemaContext getSchemaContext(String datasourceId) {
        CachedSchema cached = schemaCache.get(datasourceId.toLowerCase());
        if (cached != null && Instant.now().isBefore(cached.timestamp().plus(CACHE_TTL))) {
            return cached.context();
        }

        DatasourceConnector connector = datasourceRegistry.getConnector(datasourceId);
        if (!connector.isAvailable()) {
            throw new McpServerUnavailableException("MCP server for datasource '" + datasourceId + "' is currently unavailable.");
        }

        log.info("Introspecting live database schema for datasource '{}' via MCP...", datasourceId);

        // 1. Discover Tables
        Map<String, Object> tablesResponse = connector.listTables();
        List<String> tableNames = extractTableNames(tablesResponse);

        // 2. Discover Column Schemas for each table
        List<DatasourceSchemaContext.TableMeta> tableMetas = new ArrayList<>();
        for (String tableName : tableNames) {
            Map<String, Object> schemaResponse = connector.getTableSchema(tableName);
            DatasourceSchemaContext.TableMeta rawMeta = parseTableMeta(tableName, schemaResponse);
            List<DatasourceSchemaContext.ColumnMeta> enrichedCols = new ArrayList<>();
            for (DatasourceSchemaContext.ColumnMeta col : rawMeta.columns()) {
                List<String> distinctValues = sampleDistinctValuesIfCategorical(connector, tableName, col);
                enrichedCols.add(new DatasourceSchemaContext.ColumnMeta(
                        col.name(),
                        col.type(),
                        col.primaryKey(),
                        col.nullable(),
                        distinctValues
                ));
            }
            tableMetas.add(new DatasourceSchemaContext.TableMeta(rawMeta.tableName(), rawMeta.schema(), enrichedCols));
        }

        // 3. Discover Relationships
        Map<String, Object> relResponse = connector.getRelationships();
        List<DatasourceSchemaContext.RelationshipMeta> relationships = parseRelationships(relResponse);

        String engine = "crm".equalsIgnoreCase(datasourceId) ? "PostgreSQL" : "MySQL";
        String dialect = "crm".equalsIgnoreCase(datasourceId) ? "postgresql" : "mysql";

        DatasourceSchemaContext context = new DatasourceSchemaContext(
                connector.getId(),
                connector.getName(),
                engine,
                dialect,
                tableMetas,
                relationships
        );

        schemaCache.put(datasourceId.toLowerCase(), new CachedSchema(context, Instant.now()));
        log.info("Discovered {} tables and {} relationships for datasource '{}'",
                tableMetas.size(), relationships.size(), datasourceId);

        return context;
    }

    public void clearCache(String datasourceId) {
        if (datasourceId != null) {
            schemaCache.remove(datasourceId.toLowerCase());
        } else {
            schemaCache.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractTableNames(Map<String, Object> response) {
        List<String> names = new ArrayList<>();
        if (response != null && response.get("tables") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object tName = map.get("tableName");
                    if (tName != null) {
                        names.add(tName.toString());
                    }
                } else if (item != null) {
                    names.add(item.toString());
                }
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private DatasourceSchemaContext.TableMeta parseTableMeta(String tableName, Map<String, Object> response) {
        String schemaName = response.get("schema") != null ? response.get("schema").toString() : "public";
        List<DatasourceSchemaContext.ColumnMeta> columns = new ArrayList<>();

        if (response.get("columns") instanceof List<?> colList) {
            for (Object colObj : colList) {
                if (colObj instanceof Map<?, ?> colMap) {
                    String colName = colMap.get("name") != null ? colMap.get("name").toString() : "";
                    String type = colMap.get("type") != null ? colMap.get("type").toString() : "VARCHAR";
                    boolean pk = Boolean.TRUE.equals(colMap.get("primaryKey"));
                    boolean nullable = !Boolean.FALSE.equals(colMap.get("nullable"));
                    columns.add(new DatasourceSchemaContext.ColumnMeta(colName, type, pk, nullable));
                }
            }
        }

        return new DatasourceSchemaContext.TableMeta(tableName, schemaName, columns);
    }

    @SuppressWarnings("unchecked")
    private List<DatasourceSchemaContext.RelationshipMeta> parseRelationships(Map<String, Object> response) {
        List<DatasourceSchemaContext.RelationshipMeta> relationships = new ArrayList<>();
        if (response != null && response.get("relationships") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String srcTable = map.get("sourceTable") != null ? map.get("sourceTable").toString() : "";
                    String srcCol = map.get("sourceColumn") != null ? map.get("sourceColumn").toString() : "";
                    String tgtTable = map.get("targetTable") != null ? map.get("targetTable").toString() : "";
                    String tgtCol = map.get("targetColumn") != null ? map.get("targetColumn").toString() : "";
                    String constraint = map.get("constraintName") != null ? map.get("constraintName").toString() : "";
                    relationships.add(new DatasourceSchemaContext.RelationshipMeta(srcTable, srcCol, tgtTable, tgtCol, constraint));
                }
            }
        }
        return relationships;
    }

    private List<String> sampleDistinctValuesIfCategorical(
            DatasourceConnector connector,
            String tableName,
            DatasourceSchemaContext.ColumnMeta col) {

        if (col.primaryKey()) {
            return List.of();
        }

        String colName = col.name().toLowerCase();
        String colType = col.type() != null ? col.type().toLowerCase() : "";

        // Only sample string / varchar / char / enum types
        boolean isStringType = colType.contains("char") || colType.contains("text")
                || colType.contains("string") || colType.contains("enum");
        if (!isStringType) {
            return List.of();
        }

        // Heuristic: only sample columns whose name suggests categorical/status data
        boolean isCategoricalName = colName.matches(".*(status|stage|category|type|source|method|industry|state|priority).*");
        if (!isCategoricalName) {
            return List.of();
        }

        try {
            String sql = "SELECT DISTINCT " + col.name() + " FROM " + tableName +
                    " WHERE " + col.name() + " IS NOT NULL ORDER BY " + col.name() + " LIMIT 15";
            Map<String, Object> queryResult = connector.executeQuery(sql, 15);
            if (queryResult != null && queryResult.get("rows") instanceof List<?> rows) {
                List<String> distinctList = new ArrayList<>();
                for (Object rowObj : rows) {
                    if (rowObj instanceof Map<?, ?> rowMap) {
                        for (Map.Entry<?, ?> entry : rowMap.entrySet()) {
                            if (col.name().equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                                Object val = entry.getValue();
                                if (val != null && !val.toString().isBlank()) {
                                    distinctList.add(val.toString().trim());
                                }
                                break;
                            }
                        }
                    }
                }
                if (!distinctList.isEmpty() && distinctList.size() <= 15) {
                    return distinctList;
                }
            }
        } catch (Exception e) {
            log.debug("Could not sample distinct values for {}.{}: {}", tableName, col.name(), e.getMessage());
        }

        return List.of();
    }

    private record CachedSchema(DatasourceSchemaContext context, Instant timestamp) {}
}
