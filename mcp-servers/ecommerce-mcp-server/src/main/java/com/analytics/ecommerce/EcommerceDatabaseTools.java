package com.analytics.ecommerce;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class EcommerceDatabaseTools {

    private static final String DATABASE = "enterprise_ecommerce";
    private final DataSource dataSource;
    private final int queryTimeoutSeconds;
    private final int maxRows;

    public EcommerceDatabaseTools(
            DataSource dataSource,
            @Value("${analytics.query.timeout-seconds:10}") int queryTimeoutSeconds,
            @Value("${analytics.query.max-rows:500}") int maxRows) {
        if (queryTimeoutSeconds < 1) throw new IllegalArgumentException("Query timeout must be at least one second.");
        if (maxRows < 1 || maxRows >= Integer.MAX_VALUE) throw new IllegalArgumentException("Maximum query rows must be between 1 and 2147483646.");
        this.dataSource = dataSource;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.maxRows = maxRows;
    }

    @McpTool(name = "list_tables", description = "List tables in the E-Commerce database.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> listTables() {
        try (Connection connection = dataSource.getConnection()) {
            List<Map<String, Object>> tables = tableNames(connection).stream()
                    .map(name -> Map.<String, Object>of("schema", DATABASE, "tableName", name))
                    .toList();
            return Map.of("schema", DATABASE, "tables", tables);
        }
        catch (SQLException exception) {
            throw databaseError("Could not list E-Commerce tables", exception);
        }
    }

    @McpTool(name = "get_table_schema", description = "Return columns and keys for an E-Commerce table.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> getTableSchema(
            @McpToolParam(description = "E-Commerce table name", required = true) String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            String actualName = requireTable(connection, tableName);
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            Set<String> primaryKeys = new HashSet<>();
            try (ResultSet keys = metadata.getPrimaryKeys(catalog, null, actualName)) {
                while (keys.next()) primaryKeys.add(keys.getString("COLUMN_NAME"));
            }
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet result = metadata.getColumns(catalog, null, actualName, null)) {
                while (result.next()) {
                    Map<String, Object> column = new LinkedHashMap<>();
                    column.put("name", result.getString("COLUMN_NAME"));
                    column.put("type", result.getString("TYPE_NAME"));
                    column.put("jdbcType", result.getInt("DATA_TYPE"));
                    column.put("nullable", result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                    column.put("default", result.getString("COLUMN_DEF"));
                    column.put("ordinalPosition", result.getInt("ORDINAL_POSITION"));
                    column.put("primaryKey", primaryKeys.contains(result.getString("COLUMN_NAME")));
                    columns.add(column);
                }
            }
            return Map.of("schema", DATABASE, "tableName", actualName, "columns", columns);
        }
        catch (SQLException exception) {
            throw databaseError("Could not read E-Commerce table metadata", exception);
        }
    }

    @McpTool(name = "get_relationships", description = "List foreign-key relationships between E-Commerce tables.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> getRelationships() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            List<Map<String, Object>> relationships = new ArrayList<>();
            for (String table : tableNames(connection)) {
                try (ResultSet keys = metadata.getImportedKeys(catalog, null, table)) {
                    while (keys.next()) {
                        Map<String, Object> relationship = new LinkedHashMap<>();
                        relationship.put("constraintName", keys.getString("FK_NAME"));
                        relationship.put("sourceSchema", DATABASE);
                        relationship.put("sourceTable", keys.getString("FKTABLE_NAME"));
                        relationship.put("sourceColumn", keys.getString("FKCOLUMN_NAME"));
                        relationship.put("targetSchema", DATABASE);
                        relationship.put("targetTable", keys.getString("PKTABLE_NAME"));
                        relationship.put("targetColumn", keys.getString("PKCOLUMN_NAME"));
                        relationship.put("sequence", keys.getShort("KEY_SEQ"));
                        relationships.add(relationship);
                    }
                }
            }
            relationships.sort(Comparator.comparing(row -> row.get("sourceTable") + "." + row.get("sourceColumn")));
            return Map.of("schema", DATABASE, "relationships", relationships);
        }
        catch (SQLException exception) {
            throw databaseError("Could not read E-Commerce relationships", exception);
        }
    }

    @McpTool(name = "execute_readonly_query", description = "Execute one read-only SELECT against E-Commerce tables with a bounded result set.", generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    public Map<String, Object> executeReadonlyQuery(
            @McpToolParam(description = "One SQL SELECT statement", required = true) String sql,
            @McpToolParam(description = "Optional maximum row count for this call; capped by server configuration", required = false) Integer rowLimit) {
        int limit = rowLimit == null ? maxRows : Math.min(rowLimit, maxRows);
        if (rowLimit != null && rowLimit < 1) throw new IllegalArgumentException("rowLimit must be at least one.");
        try (Connection connection = dataSource.getConnection()) {
            validateReadOnlySelect(sql, connection);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            SQLException queryFailure = null;
            try (java.sql.Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(queryTimeoutSeconds);
                statement.setMaxRows(limit + 1);
                try (ResultSet result = statement.executeQuery(sql)) {
                    ResultSetMetaData metadata = result.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) columns.add(metadata.getColumnLabel(index));
                    List<Map<String, Object>> rows = new ArrayList<>();
                    boolean truncated = false;
                    while (result.next()) {
                        if (rows.size() == limit) {
                            truncated = true;
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int index = 1; index <= metadata.getColumnCount(); index++) row.put(columns.get(index - 1), result.getObject(index));
                        rows.add(row);
                    }
                    return Map.of("columns", columns, "rows", rows, "rowCount", rows.size(), "rowLimit", limit, "truncated", truncated);
                }
            }
            catch (SQLException exception) {
                queryFailure = exception;
                throw exception;
            }
            finally {
                try {
                    connection.rollback();
                }
                catch (SQLException rollbackFailure) {
                    if (queryFailure == null) throw rollbackFailure;
                    queryFailure.addSuppressed(rollbackFailure);
                }
            }
        }
        catch (JSQLParserException exception) {
            throw new IllegalArgumentException("SQL could not be parsed as a supported read-only query.");
        }
        catch (SQLException exception) {
            if (isQueryTimeout(exception)) {
                throw new IllegalStateException("E-Commerce query exceeded the configured timeout.");
            }
            throw databaseError("E-Commerce read-only query failed", exception);
        }
    }

    private void validateReadOnlySelect(String sql, Connection connection) throws JSQLParserException, SQLException {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL query must not be empty.");
        Statements statements = CCJSqlParserUtil.parseStatements(sql);
        if (statements.getStatements().size() != 1) throw new IllegalArgumentException("Exactly one SQL statement is allowed.");
        Statement statement = statements.getStatements().get(0);
        if (!(statement instanceof Select select)) throw new IllegalArgumentException("Only SELECT statements are allowed.");
        validateSelectShape(select);

        Set<String> allowed = new HashSet<>();
        for (String table : tableNames(connection)) {
            allowed.add(normalizeIdentifier(table));
            allowed.add(normalizeIdentifier(DATABASE + "." + table));
        }
        for (Object tableReference : new TablesNamesFinder().getTables(statement)) {
            String table = String.valueOf(tableReference);
            if (!allowed.contains(normalizeIdentifier(table))) {
                throw new IllegalArgumentException("Query references a table outside the E-Commerce database: " + table);
            }
        }
    }

    private void validateSelectShape(Select select) {
        if (select.getForClause() != null || select.getForMode() != null || (select.getForUpdateTables() != null && !select.getForUpdateTables().isEmpty())) {
            throw new IllegalArgumentException("Locking SELECT clauses are not allowed.");
        }
        if (select instanceof PlainSelect plainSelect) {
            if ((plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty())
                    || plainSelect.getMySqlSelectIntoClause() != null || plainSelect.getIntoTempTable() != null) {
                throw new IllegalArgumentException("SELECT INTO clauses are not allowed.");
            }
        }
        else if (select instanceof SetOperationList setOperationList) {
            setOperationList.getSelects().forEach(this::validateSelectShape);
        }
        else if (select instanceof ParenthesedSelect parenthesedSelect) {
            validateSelectShape(parenthesedSelect.getSelect());
        }
        else {
            throw new IllegalArgumentException("This SELECT form is not supported for read-only execution.");
        }
        List<WithItem<?>> withItems = select.getWithItemsList();
        if (withItems != null) {
            for (WithItem<?> withItem : withItems) {
                ParenthesedSelect nested = withItem.getSelect();
                if (nested == null) throw new IllegalArgumentException("Only SELECT common-table expressions are allowed.");
                validateSelectShape(nested);
            }
        }
    }

    private List<String> tableNames(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> names = new ArrayList<>();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) names.add(tables.getString("TABLE_NAME"));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private String requireTable(Connection connection, String requestedName) throws SQLException {
        if (requestedName == null || !requestedName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("A valid E-Commerce table name is required.");
        }
        return tableNames(connection).stream().filter(name -> name.equalsIgnoreCase(requestedName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table is not available in the E-Commerce database: " + requestedName));
    }

    private String normalizeIdentifier(String identifier) {
        return identifier.replace("\"", "").replace("`", "").toLowerCase(Locale.ROOT);
    }

    private boolean isQueryTimeout(SQLException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLTimeoutException) return true;
            if (current instanceof SQLException sqlException
                    && ("57014".equals(sqlException.getSQLState()) || "70100".equals(sqlException.getSQLState()) || sqlException.getErrorCode() == 1317)) return true;
            if (current.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout")) return true;
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed instanceof SQLTimeoutException) return true;
                if (suppressed instanceof SQLException sqlException
                        && ("57014".equals(sqlException.getSQLState()) || "70100".equals(sqlException.getSQLState()) || sqlException.getErrorCode() == 1317)) return true;
            }
        }
        return false;
    }

    private IllegalStateException databaseError(String message, SQLException exception) {
        return new IllegalStateException(message + " (SQLState " + exception.getSQLState() + ", vendor code " + exception.getErrorCode() + ", " + exception.getClass().getSimpleName() + ").");
    }
}
