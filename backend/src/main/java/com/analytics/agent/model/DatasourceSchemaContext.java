package com.analytics.agent.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record DatasourceSchemaContext(
        String datasourceId,
        String datasourceName,
        String databaseEngine, // "PostgreSQL" or "MySQL"
        String dialect,        // "postgresql" or "mysql"
        List<TableMeta> tables,
        List<RelationshipMeta> relationships
) {
    public boolean hasTable(String tableName) {
        if (tableName == null || tableName.isBlank()) return false;
        String unquoted = unquoteIdentifier(tableName);
        return tables.stream().anyMatch(t -> t.tableName().equalsIgnoreCase(unquoted));
    }

    public boolean isValidTableReference(String rawReference) {
        if (rawReference == null || rawReference.isBlank()) return false;
        String clean = rawReference.trim();
        int dotIdx = clean.indexOf('.');
        if (dotIdx < 0) {
            return hasTable(clean);
        }
        String qualifier = unquoteIdentifier(clean.substring(0, dotIdx));
        String tableName = unquoteIdentifier(clean.substring(dotIdx + 1));

        return tables.stream().anyMatch(t ->
                t.tableName().equalsIgnoreCase(tableName) &&
                t.schema() != null && t.schema().equalsIgnoreCase(qualifier)
        );
    }

    public boolean isForeignQualifier(String rawReference) {
        if (rawReference == null || !rawReference.contains(".")) return false;
        String clean = rawReference.trim();
        int dotIdx = clean.indexOf('.');
        String qualifier = unquoteIdentifier(clean.substring(0, dotIdx));
        return tables.stream().noneMatch(t -> t.schema() != null && t.schema().equalsIgnoreCase(qualifier));
    }

    public Set<String> getAuthorizedSchemas() {
        return tables.stream()
                .map(TableMeta::schema)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
    }

    public static String unquoteIdentifier(String identifier) {
        if (identifier == null) return "";
        return identifier.replace("`", "").replace("\"", "").replace("[", "").replace("]", "").trim();
    }

    public List<String> getTableNames() {
        return tables.stream().map(TableMeta::tableName).toList();
    }

    public String toDdlSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Dialect: ").append(databaseEngine).append(" (").append(dialect).append(")\n\n");
        sb.append("-- Available Tables and Columns:\n");
        for (TableMeta table : tables) {
            sb.append("TABLE ").append(table.tableName()).append(" (\n");
            String cols = table.columns().stream()
                    .map(c -> {
                        String def = "    " + c.name() + " " + c.type() + (c.primaryKey() ? " PRIMARY KEY" : "") + (c.nullable() ? "" : " NOT NULL");
                        if (c.distinctValues() != null && !c.distinctValues().isEmpty()) {
                            def += " -- Distinct observed values: " + c.distinctValues().stream()
                                    .map(v -> "'" + v + "'")
                                    .collect(Collectors.joining(", "));
                        }
                        return def;
                    })
                    .collect(Collectors.joining(",\n"));
            sb.append(cols).append("\n);\n\n");
        }

        if (!relationships.isEmpty()) {
            sb.append("-- Foreign Key Relationships:\n");
            for (RelationshipMeta rel : relationships) {
                sb.append("-- ").append(rel.sourceTable()).append(".").append(rel.sourceColumn())
                        .append(" REFERENCES ").append(rel.targetTable()).append(".").append(rel.targetColumn())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    public record TableMeta(
            String tableName,
            String schema,
            List<ColumnMeta> columns
    ) {}

    public record ColumnMeta(
            String name,
            String type,
            boolean primaryKey,
            boolean nullable,
            List<String> distinctValues
    ) {
        public ColumnMeta(String name, String type, boolean primaryKey, boolean nullable) {
            this(name, type, primaryKey, nullable, List.of());
        }
    }

    public record RelationshipMeta(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String constraintName
    ) {}
}
