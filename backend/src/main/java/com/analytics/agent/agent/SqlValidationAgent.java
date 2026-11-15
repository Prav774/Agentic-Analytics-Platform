package com.analytics.agent.agent;

import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.exception.DatasourceQueryException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SqlValidationAgent {

    private static final Logger log = LoggerFactory.getLogger(SqlValidationAgent.class);

    private static final Set<String> DANGEROUS_PATTERNS = Set.of(
            "INTO OUTFILE",
            "INTO DUMPFILE",
            "PG_READ_FILE",
            "PG_WRITE_FILE",
            "PG_SLEEP",
            "LOAD_FILE",
            "SYS_EVAL",
            "BENCHMARK",
            "XP_CMDSHELL"
    );

    private static final Pattern LOCKING_CLAUSE_PATTERN = Pattern.compile(
            "(?i)\\bFOR\\s+(?:UPDATE|SHARE|KEY\\s+SHARE|NO\\s+KEY\\s+UPDATE)\\b|\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b"
    );

    private final int defaultMaxRows;

    public SqlValidationAgent(@Value("${analytics.query.max-rows:500}") int defaultMaxRows) {
        this.defaultMaxRows = defaultMaxRows;
    }

    public ValidatedSql validate(String rawSql, DatasourceSchemaContext schemaContext) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new DatasourceQueryException("Generated SQL query is empty.");
        }

        String sql = rawSql.trim();

        // 1. Check for dangerous constructs in raw SQL string
        String upperSql = sql.toUpperCase(Locale.ROOT);
        for (String pattern : DANGEROUS_PATTERNS) {
            if (upperSql.contains(pattern)) {
                throw new DatasourceQueryException("SQL rejected: Dangerous operation or function detected: " + pattern);
            }
        }

        // 2. Reject locking clauses via regex pattern matching
        if (LOCKING_CLAUSE_PATTERN.matcher(sql).find()) {
            throw new DatasourceQueryException("Locking SELECT clauses (e.g. FOR UPDATE, FOR SHARE) are not permitted.");
        }

        // 3. Parse statements to ensure single statement
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            throw new DatasourceQueryException("SQL syntax error: " + e.getMessage(), e);
        }

        if (statements == null || statements.getStatements() == null || statements.getStatements().isEmpty()) {
            throw new DatasourceQueryException("No valid SQL statement found.");
        }

        if (statements.getStatements().size() > 1) {
            throw new DatasourceQueryException("Multiple SQL statements detected. Only a single SELECT query is permitted.");
        }

        Statement statement = statements.getStatements().get(0);

        // 4. Ensure strictly a SELECT statement
        if (!(statement instanceof Select select)) {
            throw new DatasourceQueryException("Only SELECT queries are allowed. Attempted operation: " + statement.getClass().getSimpleName());
        }

        // 5. Inspect AST properties to reject locking SELECT statements (including in subqueries & CTEs)
        checkForLockingClauses(select);

        // 6. Validate referenced physical tables against datasource schema (respecting CTE scopes)
        validateTablesInSelect(select, schemaContext, Collections.emptySet());

        // 7. Enforce row limit by modifying the AST directly
        int rowLimit = enforceRowLimit(select);

        String finalSql = statement.toString().trim();
        if (!finalSql.endsWith(";")) {
            finalSql = finalSql + ";";
        }

        return new ValidatedSql(finalSql, rowLimit);
    }

    private void checkForLockingClauses(Select select) {
        if (select == null) return;

        if (select.getForClause() != null
                || select.getForMode() != null
                || select.getForUpdate() != null
                || (select.getForUpdateTables() != null && !select.getForUpdateTables().isEmpty())) {
            throw new DatasourceQueryException("Locking SELECT clauses (e.g. FOR UPDATE, FOR SHARE) are not permitted.");
        }

        if (select instanceof PlainSelect plainSelect) {
            if ((plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty())
                    || plainSelect.getMySqlSelectIntoClause() != null
                    || plainSelect.getIntoTempTable() != null) {
                throw new DatasourceQueryException("SELECT INTO clauses are not permitted.");
            }
            if (plainSelect.getFromItem() instanceof ParenthesedSelect ps && ps.getSelect() != null) {
                checkForLockingClauses(ps.getSelect());
            }
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    if (join.getFromItem() instanceof ParenthesedSelect ps && ps.getSelect() != null) {
                        checkForLockingClauses(ps.getSelect());
                    }
                }
            }
            if (plainSelect.getWithItemsList() != null) {
                for (WithItem<?> withItem : plainSelect.getWithItemsList()) {
                    ParenthesedSelect nested = withItem.getSelect();
                    if (nested != null && nested.getSelect() != null) {
                        checkForLockingClauses(nested.getSelect());
                    }
                }
            }
        } else if (select instanceof SetOperationList setOperationList) {
            if (setOperationList.getSelects() != null) {
                for (Select child : setOperationList.getSelects()) {
                    checkForLockingClauses(child);
                }
            }
        } else if (select instanceof ParenthesedSelect parenthesedSelect && parenthesedSelect.getSelect() != null) {
            checkForLockingClauses(parenthesedSelect.getSelect());
        }
    }

    private void validateTablesInSelect(Select select, DatasourceSchemaContext schemaContext, Set<String> inheritedCteAliases) {
        if (select == null) return;

        Set<String> inScopeCteAliases = new HashSet<>(inheritedCteAliases);

        List<WithItem<?>> withItems = select.getWithItemsList();
        if (withItems != null && !withItems.isEmpty()) {
            for (WithItem<?> withItem : withItems) {
                String cteName = extractCteAlias(withItem);

                // If recursive, CTE can reference itself
                if (withItem.isRecursive() && cteName != null) {
                    inScopeCteAliases.add(cteName.toLowerCase(Locale.ROOT));
                }

                ParenthesedSelect ps = withItem.getSelect();
                if (ps != null && ps.getSelect() != null) {
                    validateTablesInSelect(ps.getSelect(), schemaContext, inScopeCteAliases);
                }

                if (cteName != null) {
                    inScopeCteAliases.add(cteName.toLowerCase(Locale.ROOT));
                }
            }
        }

        // Temporarily detach withItemsList to find tables strictly in the body
        select.setWithItemsList(null);
        List<String> bodyTables;
        try {
            bodyTables = new TablesNamesFinder().getTableList((Statement) select);
        } finally {
            select.setWithItemsList(withItems);
        }

        if (bodyTables != null) {
            for (String rawTable : bodyTables) {
                validateSingleTableReference(rawTable, schemaContext, inScopeCteAliases);
            }
        }

        // Recursively inspect any nested subqueries in FROM or JOINs that may have inner WITH items
        if (select instanceof PlainSelect plainSelect) {
            if (plainSelect.getFromItem() instanceof ParenthesedSelect ps && ps.getSelect() != null) {
                validateTablesInSelect(ps.getSelect(), schemaContext, inScopeCteAliases);
            }
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    if (join.getFromItem() instanceof ParenthesedSelect ps && ps.getSelect() != null) {
                        validateTablesInSelect(ps.getSelect(), schemaContext, inScopeCteAliases);
                    }
                }
            }
        } else if (select instanceof SetOperationList setOperationList) {
            if (setOperationList.getSelects() != null) {
                for (Select child : setOperationList.getSelects()) {
                    validateTablesInSelect(child, schemaContext, inScopeCteAliases);
                }
            }
        } else if (select instanceof ParenthesedSelect parenthesedSelect && parenthesedSelect.getSelect() != null) {
            validateTablesInSelect(parenthesedSelect.getSelect(), schemaContext, inScopeCteAliases);
        }
    }

    private void validateSingleTableReference(String rawTable, DatasourceSchemaContext schemaContext, Set<String> inScopeCteAliases) {
        if (rawTable == null || rawTable.isBlank()) return;

        String unquoted = DatasourceSchemaContext.unquoteIdentifier(rawTable.trim());

        // 1. In-scope CTE alias check (CTE aliases are never schema-qualified)
        if (!rawTable.contains(".") && inScopeCteAliases.contains(unquoted.toLowerCase(Locale.ROOT))) {
            return;
        }

        // 2. Schema-qualified reference check (e.g. enterprise_ecommerce.orders or other_db.orders)
        if (rawTable.contains(".")) {
            int dotIdx = rawTable.indexOf('.');
            String qualifier = DatasourceSchemaContext.unquoteIdentifier(rawTable.substring(0, dotIdx));

            if (schemaContext.isForeignQualifier(rawTable)) {
                throw new DatasourceQueryException(
                        "Unauthorized schema or database qualifier: '" + qualifier + "' in reference '"
                                + rawTable + "' for datasource '" + schemaContext.datasourceId() + "'"
                );
            }

            if (!schemaContext.isValidTableReference(rawTable)) {
                throw new DatasourceQueryException(
                        "Unauthorized table reference: '" + rawTable + "' does not exist in datasource '"
                                + schemaContext.datasourceId() + "'. Available tables: " + schemaContext.getTableNames()
                );
            }
            return;
        }

        // 3. Unqualified physical table reference
        if (!schemaContext.hasTable(unquoted)) {
            throw new DatasourceQueryException(
                    "Unauthorized table reference: '" + rawTable + "' does not exist in datasource '"
                            + schemaContext.datasourceId() + "'. Available tables: " + schemaContext.getTableNames()
            );
        }
    }

    private String extractCteAlias(WithItem<?> withItem) {
        if (withItem == null) return null;
        if (withItem.getAlias() != null && withItem.getAlias().getName() != null) {
            return DatasourceSchemaContext.unquoteIdentifier(withItem.getAlias().getName());
        }
        if (withItem.getAliasName() != null) {
            return DatasourceSchemaContext.unquoteIdentifier(withItem.getAliasName());
        }
        return null;
    }

    private int enforceRowLimit(Select select) {
        int rowLimit = defaultMaxRows;

        if (select instanceof PlainSelect plainSelect) {
            Limit limit = plainSelect.getLimit();
            if (limit == null) {
                Limit newLimit = new Limit();
                newLimit.setRowCount(new LongValue(defaultMaxRows));
                plainSelect.setLimit(newLimit);
                rowLimit = defaultMaxRows;
            } else {
                rowLimit = clampLimit(limit);
            }
        } else if (select instanceof SetOperationList setOperationList) {
            Limit limit = setOperationList.getLimit();
            if (limit == null) {
                Limit newLimit = new Limit();
                newLimit.setRowCount(new LongValue(defaultMaxRows));
                setOperationList.setLimit(newLimit);
                rowLimit = defaultMaxRows;
            } else {
                rowLimit = clampLimit(limit);
            }
        } else if (select instanceof ParenthesedSelect parenthesedSelect) {
            Limit limit = parenthesedSelect.getLimit();
            if (limit == null) {
                Limit newLimit = new Limit();
                newLimit.setRowCount(new LongValue(defaultMaxRows));
                parenthesedSelect.setLimit(newLimit);
                rowLimit = defaultMaxRows;
            } else {
                rowLimit = clampLimit(limit);
            }
        } else {
            throw new DatasourceQueryException("Unsupported SELECT statement form: " + select.getClass().getSimpleName());
        }

        return rowLimit;
    }

    private int clampLimit(Limit limit) {
        if (limit.isLimitAll() || limit.isLimitNull()) {
            limit.setLimitAll(false);
            limit.setLimitNull(false);
            limit.setRowCount(new LongValue(defaultMaxRows));
            return defaultMaxRows;
        }

        Expression countExpr = limit.getRowCount();
        if (countExpr == null) {
            limit.setRowCount(new LongValue(defaultMaxRows));
            return defaultMaxRows;
        }

        long parsedVal;
        if (countExpr instanceof LongValue lv) {
            parsedVal = lv.getValue();
        } else {
            try {
                parsedVal = Long.parseLong(countExpr.toString().trim());
            } catch (NumberFormatException e) {
                throw new DatasourceQueryException("Malformed LIMIT clause: " + countExpr);
            }
        }

        if (parsedVal <= 0) {
            throw new DatasourceQueryException("Invalid LIMIT clause: row count must be greater than zero. Attempted: " + parsedVal);
        }

        if (parsedVal > defaultMaxRows) {
            limit.setRowCount(new LongValue(defaultMaxRows));
            return defaultMaxRows;
        }

        return (int) parsedVal;
    }

    public record ValidatedSql(String sql, int rowLimit) {}
}
