package com.analytics.agent;

import com.analytics.agent.agent.SqlValidationAgent;
import com.analytics.agent.model.DatasourceSchemaContext;
import com.analytics.exception.DatasourceQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlValidationAgentTest {

    private SqlValidationAgent validator;
    private DatasourceSchemaContext schemaContext;

    @BeforeEach
    void setUp() {
        validator = new SqlValidationAgent(500);

        List<DatasourceSchemaContext.TableMeta> tables = List.of(
                new DatasourceSchemaContext.TableMeta(
                        "orders",
                        "enterprise_ecommerce",
                        List.of(
                                new DatasourceSchemaContext.ColumnMeta("id", "BIGINT", true, false),
                                new DatasourceSchemaContext.ColumnMeta("order_status", "VARCHAR", false, false),
                                new DatasourceSchemaContext.ColumnMeta("total_amount", "DECIMAL", false, false),
                                new DatasourceSchemaContext.ColumnMeta("order_date", "DATETIME", false, false)
                        )
                ),
                new DatasourceSchemaContext.TableMeta(
                        "order_items",
                        "enterprise_ecommerce",
                        List.of(
                                new DatasourceSchemaContext.ColumnMeta("id", "BIGINT", true, false),
                                new DatasourceSchemaContext.ColumnMeta("order_id", "BIGINT", false, false),
                                new DatasourceSchemaContext.ColumnMeta("product_id", "BIGINT", false, false),
                                new DatasourceSchemaContext.ColumnMeta("quantity", "INT", false, false),
                                new DatasourceSchemaContext.ColumnMeta("unit_price", "DECIMAL", false, false)
                        )
                )
        );

        schemaContext = new DatasourceSchemaContext(
                "ecommerce",
                "E-Commerce",
                "MySQL",
                "mysql",
                tables,
                List.of()
        );
    }

    // ==========================================
    // 1. Basic Query & AST Limit Enforcement
    // ==========================================

    @Test
    void validSelectQueryPassesAndReceivesDefaultLimit() {
        String sql = "SELECT order_status, COUNT(*) AS count FROM orders GROUP BY order_status;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertNotNull(result);
        assertTrue(result.sql().toUpperCase().startsWith("SELECT"));
        assertTrue(result.sql().toUpperCase().contains("LIMIT 500"), "AST must inject configured default limit");
        assertEquals(500, result.rowLimit());
    }

    @Test
    void queryWithoutLimitReceivesConfiguredMaximum() {
        String sql = "SELECT * FROM orders;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertEquals(500, result.rowLimit());
        assertTrue(result.sql().toUpperCase().contains("LIMIT 500"));
    }

    @Test
    void queryWithExistingLimitPreservesLimitBelowMaximum() {
        String sql = "SELECT * FROM orders LIMIT 25;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertEquals(25, result.rowLimit());
        assertTrue(result.sql().toUpperCase().contains("LIMIT 25"));
    }

    @Test
    void queryWithLimitExceedingMaximumIsClamped() {
        String sql = "SELECT * FROM orders LIMIT 2000;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertEquals(500, result.rowLimit());
        assertTrue(result.sql().toUpperCase().contains("LIMIT 500"), "AST must clamp limit to maximum");
        assertFalse(result.sql().contains("2000"), "Old unclamped limit must not remain");
    }

    @Test
    void unionQueryReceivesLimit() {
        String sql = "SELECT * FROM orders UNION SELECT * FROM orders;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertEquals(500, result.rowLimit());
        assertTrue(result.sql().toUpperCase().contains("LIMIT 500"));
    }

    @Test
    void rejectsMalformedNegativeLimit() {
        String sql = "SELECT * FROM orders LIMIT -10;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("must be greater than zero"));
    }

    @Test
    void rejectsMalformedZeroLimit() {
        String sql = "SELECT * FROM orders LIMIT 0;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("must be greater than zero"));
    }

    // ==========================================
    // 2. CTE Validation
    // ==========================================

    @Test
    void acceptsValidCteReferencingAuthorizedTable() {
        String sql = "WITH recent_orders AS (SELECT id, total_amount FROM orders WHERE total_amount > 100) " +
                "SELECT * FROM recent_orders;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertNotNull(result);
        assertTrue(result.sql().contains("recent_orders"));
        assertTrue(result.sql().contains("LIMIT 500"));
        assertEquals(500, result.rowLimit());
    }

    @Test
    void acceptsMultipleCteChain() {
        String sql = "WITH cte1 AS (SELECT id, total_amount FROM orders), " +
                "cte2 AS (SELECT id FROM cte1 WHERE total_amount > 50) " +
                "SELECT * FROM cte2;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertNotNull(result);
        assertEquals(500, result.rowLimit());
    }

    @Test
    void rejectsCteReferencingUnauthorizedPhysicalTable() {
        String sql = "WITH private_data AS (SELECT * FROM secret_payroll_data) " +
                "SELECT * FROM private_data;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Unauthorized table reference"));
        assertTrue(ex.getMessage().contains("secret_payroll_data"));
    }

    @Test
    void rejectsNestedCteWithUnauthorizedTable() {
        String sql = "SELECT * FROM (WITH inner_cte AS (SELECT * FROM unauthorized_accounts) SELECT * FROM inner_cte) sub;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Unauthorized table reference"));
        assertTrue(ex.getMessage().contains("unauthorized_accounts"));
    }

    @Test
    void preventCteAliasFromBypassingWhitelist() {
        // Attempting to query an unauthorized table by naming the CTE alias after it
        String sql = "WITH secret_payroll_data AS (SELECT * FROM secret_payroll_data) " +
                "SELECT * FROM secret_payroll_data;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Unauthorized table reference"));
        assertTrue(ex.getMessage().contains("secret_payroll_data"));
    }

    // ==========================================
    // 3. Schema-Qualified Table Validation
    // ==========================================

    @Test
    void acceptsValidDatasourceQualifiedTable() {
        String sql = "SELECT * FROM enterprise_ecommerce.orders;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertNotNull(result);
        assertEquals(500, result.rowLimit());
    }

    @Test
    void acceptsBacktickQuotedSchemaAndTable() {
        String sql = "SELECT * FROM `enterprise_ecommerce`.`orders`;";
        SqlValidationAgent.ValidatedSql result = validator.validate(sql, schemaContext);
        assertNotNull(result);
        assertEquals(500, result.rowLimit());
    }

    @Test
    void rejectsForeignDatabaseSchemaQualifier() {
        String sql = "SELECT * FROM other_db.orders;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Unauthorized schema or database qualifier"));
        assertTrue(ex.getMessage().contains("other_db"));
    }

    @Test
    void rejectsCrossDatasourceQualifier() {
        // 'public' is for CRM, not E-Commerce
        String sql = "SELECT * FROM public.orders;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Unauthorized schema or database qualifier"));
        assertTrue(ex.getMessage().contains("public"));
    }

    @Test
    void rejectsUnauthorizedTableReference() {
        String sql = "SELECT * FROM users_private_credentials;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Unauthorized table reference"));
        assertTrue(ex.getMessage().contains("users_private_credentials"));
    }

    // ==========================================
    // 4. Reject Locking SELECT Statements
    // ==========================================

    @Test
    void rejectsSelectForUpdate() {
        String sql = "SELECT * FROM orders FOR UPDATE;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Locking SELECT clauses"));
    }

    @Test
    void rejectsSelectForShare() {
        String sql = "SELECT * FROM orders FOR SHARE;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Locking SELECT clauses"));
    }

    @Test
    void rejectsLockInShareMode() {
        String sql = "SELECT * FROM orders LOCK IN SHARE MODE;";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Locking SELECT clauses"));
    }

    @Test
    void rejectsLockingClauseInNestedSubquery() {
        String sql = "SELECT * FROM orders WHERE id IN (SELECT order_id FROM order_items FOR UPDATE);";
        DatasourceQueryException ex = assertThrows(DatasourceQueryException.class,
                () -> validator.validate(sql, schemaContext));
        assertTrue(ex.getMessage().contains("Locking SELECT clauses"));
    }

    // ==========================================
    // 5. Reject DML, DDL, Multiple Statements & Dangerous Constructs
    // ==========================================

    @Test
    void rejectsInsertStatement() {
        String sql = "INSERT INTO orders (order_status, total_amount) VALUES ('pending', 100);";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql, schemaContext));
    }

    @Test
    void rejectsUpdateStatement() {
        String sql = "UPDATE orders SET order_status = 'cancelled' WHERE id = 1;";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql, schemaContext));
    }

    @Test
    void rejectsDeleteStatement() {
        String sql = "DELETE FROM orders WHERE id = 1;";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql, schemaContext));
    }

    @Test
    void rejectsDropTableStatement() {
        String sql = "DROP TABLE orders;";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql, schemaContext));
    }

    @Test
    void rejectsAlterTableStatement() {
        String sql = "ALTER TABLE orders ADD COLUMN hack VARCHAR(100);";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql, schemaContext));
    }

    @Test
    void rejectsMultipleStatements() {
        String sql = "SELECT * FROM orders; DROP TABLE orders;";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql, schemaContext));
    }

    @Test
    void rejectsDangerousConstructs() {
        String sql1 = "SELECT * FROM orders INTO OUTFILE '/tmp/dump.txt';";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql1, schemaContext));

        String sql2 = "SELECT * FROM orders WHERE pg_sleep(10) IS NOT NULL;";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql2, schemaContext));

        String sql3 = "SELECT * FROM orders WHERE benchmark(1000000, MD5('test'));";
        assertThrows(DatasourceQueryException.class, () -> validator.validate(sql3, schemaContext));
    }

    @Test
    void rejectsBlankQuery() {
        assertThrows(DatasourceQueryException.class, () -> validator.validate("   ", schemaContext));
    }
}
