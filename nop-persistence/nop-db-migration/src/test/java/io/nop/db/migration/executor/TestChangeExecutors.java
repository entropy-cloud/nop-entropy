/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.executor;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.AddColumnChange;
import io.nop.db.migration.model.AlterColumnChange;
import io.nop.db.migration.model.ColumnDefinition;
import io.nop.db.migration.model.CreateIndexChange;
import io.nop.db.migration.model.DbSpecificSql;
import io.nop.db.migration.model.SqlChange;
import io.nop.db.migration.model.CreateTableChange;
import io.nop.db.migration.model.DeleteDataChange;
import io.nop.db.migration.model.DropColumnChange;
import io.nop.db.migration.model.DropIndexChange;
import io.nop.db.migration.model.DropTableChange;
import io.nop.db.migration.model.InsertColumnModel;
import io.nop.db.migration.model.InsertDataChange;
import io.nop.db.migration.model.UpdateColumnModel;
import io.nop.db.migration.model.UpdateDataChange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestChangeExecutors {

    private HikariDataSource dataSource;
    private Connection connection;
    private IJdbcTemplate jdbcTemplate;
    private ITransactionTemplate transactionTemplate;
    private IDialect dialect;
    private MigrationContext context;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() throws Exception {
        dataSource = createH2DataSource();
        jdbcTemplate = createJdbcTemplate();
        dialect = DialectManager.instance().getDialect("h2");
        connection = dataSource.getConnection();

        context = new MigrationContext();
        context.setJdbcTemplate(jdbcTemplate);
        context.setDialect(dialect);
        context.setQuerySpace("default");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private HikariDataSource createH2DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(4);
        return ds;
    }

    private IJdbcTemplate createJdbcTemplate() {
        JdbcFactory factory = new JdbcFactory();
        transactionTemplate = factory.newTransactionTemplate(dataSource);
        return factory.newJdbcTemplate(transactionTemplate);
    }
    
    @Test
    public void testCreateTable() {
        CreateTableExecutor executor = new CreateTableExecutor();
        assertTrue(executor.supports("createTable"));
        
        CreateTableChange change = new CreateTableChange();
        change.setName("test_table");
        
        ColumnDefinition idCol = new ColumnDefinition();
        idCol.setName("id");
        idCol.setType(StdSqlType.VARCHAR);
        idCol.setSize(36);
        idCol.setPrimaryKey(true);
        idCol.setNullable(false);
        
        ColumnDefinition nameCol = new ColumnDefinition();
        nameCol.setName("name");
        nameCol.setType(StdSqlType.VARCHAR);
        nameCol.setSize(100);
        nameCol.setNullable(false);
        
        change.setColumns(java.util.Arrays.asList(idCol, nameCol));
        
        executor.execute(change, context, dialect);
        
        assertTrue(tableExists("test_table"));
    }
    
    @Test
    public void testDropTable() {
        executeUpdate("CREATE TABLE test_drop (id VARCHAR(36) PRIMARY KEY)");
        
        DropTableExecutor executor = new DropTableExecutor();
        assertTrue(executor.supports("dropTable"));
        
        DropTableChange change = new DropTableChange();
        change.setName("test_drop");
        
        executor.execute(change, context, dialect);
        
        assertFalse(tableExists("test_drop"));
    }
    
    @Test
    public void testAddColumn() {
        executeUpdate("CREATE TABLE test_add_col (id VARCHAR(36) PRIMARY KEY)");

        AddColumnExecutor executor = new AddColumnExecutor();
        assertTrue(executor.supports("addColumn"));

        AddColumnChange change = new AddColumnChange();
        change.setTableName("test_add_col");

        ColumnDefinition col = new ColumnDefinition();
        col.setName("email");
        col.setType(StdSqlType.VARCHAR);
        col.setSize(100);
        col.setNullable(true);

        change.setColumns(java.util.Collections.singletonList(col));

        executor.execute(change, context, dialect);

        assertTrue(columnExists("test_add_col", "email"));
    }

    @Test
    public void testAddColumnMultipleColumns() {
        // executeUpdate does not split statements, so multiple ADD COLUMN
        // clauses must be executed as one statement per column (H2 happens to
        // tolerate joined statements, MySQL/PostgreSQL/Oracle do not)
        executeUpdate("CREATE TABLE test_add_col_multi (id VARCHAR(36) PRIMARY KEY)");

        List<String> executedSqls = new java.util.ArrayList<>();
        IJdbcTemplate recording = newRecordingJdbcTemplate(jdbcTemplate, executedSqls);
        MigrationContext recordingContext = new MigrationContext();
        recordingContext.setJdbcTemplate(recording);
        recordingContext.setDialect(dialect);
        recordingContext.setQuerySpace("default");

        AddColumnExecutor executor = new AddColumnExecutor();

        AddColumnChange change = new AddColumnChange();
        change.setTableName("test_add_col_multi");

        ColumnDefinition col1 = new ColumnDefinition();
        col1.setName("email");
        col1.setType(StdSqlType.VARCHAR);
        col1.setSize(100);
        col1.setNullable(true);

        ColumnDefinition col2 = new ColumnDefinition();
        col2.setName("age");
        col2.setType(StdSqlType.INTEGER);
        col2.setNullable(true);

        change.setColumns(java.util.Arrays.asList(col1, col2));

        executor.execute(change, recordingContext, dialect);

        assertEquals(2, executedSqls.size(), "each column must be added with its own statement: " + executedSqls);
        for (String sql : executedSqls) {
            assertFalse(sql.contains(";"), "a single ADD COLUMN statement must not contain ';': " + sql);
        }

        assertTrue(columnExists("test_add_col_multi", "email"), "first added column should exist");
        assertTrue(columnExists("test_add_col_multi", "age"), "second added column should exist");
    }

    /**
     * Delegating proxy that records the SQL text of every executeUpdate call.
     */
    private static IJdbcTemplate newRecordingJdbcTemplate(IJdbcTemplate delegate, List<String> recordedSqls) {
        return (IJdbcTemplate) java.lang.reflect.Proxy.newProxyInstance(
            IJdbcTemplate.class.getClassLoader(),
            new Class<?>[]{IJdbcTemplate.class},
            (proxy, method, args) -> {
                if (method.getName().equals("executeUpdate") && args != null && args.length > 0
                    && args[0] instanceof io.nop.core.lang.sql.SQL) {
                    recordedSqls.add(((io.nop.core.lang.sql.SQL) args[0]).getText());
                }
                try {
                    return method.invoke(delegate, args);
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    throw ex.getCause();
                }
            });
    }

    @Test
    public void testBuildColumnTypeUsesDialectTypeMapping() {
        // StdSqlType names are not portable SQL type names (e.g. DATETIME is
        // not accepted by every database), so the dialect mapping must be used
        CreateTableExecutor executor = new CreateTableExecutor();

        ColumnDefinition col = new ColumnDefinition();
        col.setName("created");
        col.setType(StdSqlType.DATETIME);

        String sqlType = executor.buildColumnType(col, dialect);
        assertEquals("TIMESTAMP", sqlType, "h2 dialect maps DATETIME to TIMESTAMP");
    }

    @Test
    public void testCreateTableSqlEscapesRemark() {
        CreateTableExecutor executor = new CreateTableExecutor();

        CreateTableChange change = new CreateTableChange();
        change.setName("test_remark");
        change.setRemark("it's a test");

        ColumnDefinition col = new ColumnDefinition();
        col.setName("id");
        col.setType(StdSqlType.VARCHAR);
        col.setSize(36);
        col.setPrimaryKey(true);
        change.setColumns(java.util.Collections.singletonList(col));

        // H2 has no inline table COMMENT clause; the remark becomes a
        // separate COMMENT ON statement and single quotes must be escaped
        List<String> statements = executor.buildCreateTableStatements(change, dialect);
        String createSql = statements.get(0);
        assertTrue(createSql.indexOf("COMMENT") < 0,
            "H2 must not get the MySQL-only inline COMMENT clause, but was: " + createSql);
        assertEquals(2, statements.size(), "remark must be emitted as a COMMENT ON statement on H2");
        assertTrue(statements.get(1).contains("IS 'it''s a test'"),
            "single quotes in the remark must be escaped, but was: " + statements.get(1));

        // execution of both statements must succeed on H2
        executor.execute(change, context, dialect);
        assertTrue(tableExists("test_remark"));
    }

    @Test
    public void testInsertDataTypedValues() {
        executeUpdate("CREATE TABLE test_insert_typed (id VARCHAR(36) PRIMARY KEY, age INT, active BOOLEAN)");

        InsertDataExecutor executor = new InsertDataExecutor();

        InsertDataChange change = new InsertDataChange();
        change.setTableName("test_insert_typed");

        InsertColumnModel idCol = new InsertColumnModel();
        idCol.setName("id");
        idCol.setValue("typed-1");

        InsertColumnModel ageCol = new InsertColumnModel();
        ageCol.setName("age");
        ageCol.setValueNumeric(42);

        InsertColumnModel activeCol = new InsertColumnModel();
        activeCol.setName("active");
        activeCol.setValueBoolean(true);

        change.setColumns(java.util.Arrays.asList(idCol, ageCol, activeCol));

        executor.execute(change, context, dialect);

        assertEquals(Integer.valueOf(42), queryIntValue("SELECT age FROM test_insert_typed WHERE id = 'typed-1'"),
            "numeric column value must be rendered as an unquoted numeric literal");
        assertEquals(Boolean.TRUE, queryBoolValue("SELECT active FROM test_insert_typed WHERE id = 'typed-1'"),
            "boolean column value must be rendered as TRUE/FALSE");
    }

    @Test
    public void testUpdateDataTypedValues() {
        executeUpdate("CREATE TABLE test_update_typed (id VARCHAR(36) PRIMARY KEY, age INT, active BOOLEAN)");
        executeUpdate("INSERT INTO test_update_typed (id, age, active) VALUES ('1', 0, FALSE)");

        UpdateDataExecutor executor = new UpdateDataExecutor();

        UpdateDataChange change = new UpdateDataChange();
        change.setTableName("test_update_typed");

        UpdateColumnModel ageCol = new UpdateColumnModel();
        ageCol.setName("age");
        ageCol.setValueNumeric(42);

        UpdateColumnModel activeCol = new UpdateColumnModel();
        activeCol.setName("active");
        activeCol.setValueBoolean(true);

        change.setColumns(java.util.Arrays.asList(ageCol, activeCol));
        change.setWhere("id = '1'");

        executor.execute(change, context, dialect);

        assertEquals(Integer.valueOf(42), queryIntValue("SELECT age FROM test_update_typed WHERE id = '1'"));
        assertEquals(Boolean.TRUE, queryBoolValue("SELECT active FROM test_update_typed WHERE id = '1'"));
    }

    @Test
    public void testInsertDataGenerateRollbackSqlReturnsNull() {
        // Without knowing the primary key the inverse of an INSERT cannot be
        // constrained; a DELETE without WHERE would wipe the whole table
        InsertDataExecutor executor = new InsertDataExecutor();

        InsertDataChange change = new InsertDataChange();
        change.setTableName("test_insert");

        assertNull(executor.generateRollbackSql(change, dialect),
            "unimplemented rollback must return null instead of an unconditional DELETE");
    }
    
    @Test
    public void testDropColumn() {
        executeUpdate("CREATE TABLE test_drop_col (id VARCHAR(36) PRIMARY KEY, extra VARCHAR(100))");
        
        DropColumnExecutor executor = new DropColumnExecutor();
        assertTrue(executor.supports("dropColumn"));
        
        DropColumnChange change = new DropColumnChange();
        change.setTableName("test_drop_col");
        change.setColumnName("extra");
        
        executor.execute(change, context, dialect);
        
        assertFalse(columnExists("test_drop_col", "extra"));
    }
    
    @Test
    public void testCreateIndex() {
        executeUpdate("CREATE TABLE test_idx (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))");
        
        CreateIndexExecutor executor = new CreateIndexExecutor();
        assertTrue(executor.supports("createIndex"));
        
        CreateIndexChange change = new CreateIndexChange();
        change.setName("idx_test_name");
        change.setTableName("test_idx");
        change.setColumnNames(new java.util.HashSet<>(java.util.Collections.singletonList("name")));
        
        executor.execute(change, context, dialect);
        
        assertTrue(indexExists("idx_test_name"));
    }
    
    @Test
    public void testDropIndex() {
        executeUpdate("CREATE TABLE test_drop_idx (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))");
        executeUpdate("CREATE INDEX idx_drop_name ON test_drop_idx(name)");
        
        DropIndexExecutor executor = new DropIndexExecutor();
        assertTrue(executor.supports("dropIndex"));
        
        DropIndexChange change = new DropIndexChange();
        change.setName("idx_drop_name");
        change.setTableName("test_drop_idx");
        
        executor.execute(change, context, dialect);
    }
    
    @Test
    public void testInsertData() {
        executeUpdate("CREATE TABLE test_insert (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))");
        
        InsertDataExecutor executor = new InsertDataExecutor();
        assertTrue(executor.supports("insertData"));
        
        InsertDataChange change = new InsertDataChange();
        change.setTableName("test_insert");
        
        InsertColumnModel col1 = new InsertColumnModel();
        col1.setName("id");
        col1.setValue("test-id-1");
        
        InsertColumnModel col2 = new InsertColumnModel();
        col2.setName("name");
        col2.setValue("Test Name");
        
        change.setColumns(java.util.Arrays.asList(col1, col2));
        
        executor.execute(change, context, dialect);
        
        assertEquals(1, countRows("test_insert"));
    }
    
    @Test
    public void testUpdateData() {
        executeUpdate("CREATE TABLE test_update (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))");
        executeUpdate("INSERT INTO test_update (id, name) VALUES ('1', 'old')");
        
        UpdateDataExecutor executor = new UpdateDataExecutor();
        assertTrue(executor.supports("updateData"));
        
        UpdateDataChange change = new UpdateDataChange();
        change.setTableName("test_update");
        
        UpdateColumnModel col = new UpdateColumnModel();
        col.setName("name");
        col.setValue("new");
        
        change.setColumns(java.util.Collections.singletonList(col));
        change.setWhere("id = '1'");
        
        executor.execute(change, context, dialect);
        
        assertEquals("new", getSingleValue("SELECT name FROM test_update WHERE id = '1'"));
    }
    
    @Test
    public void testDeleteData() {
        executeUpdate("CREATE TABLE test_delete (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))");
        executeUpdate("INSERT INTO test_delete (id, name) VALUES ('1', 'test')");

        DeleteDataExecutor executor = new DeleteDataExecutor();
        assertTrue(executor.supports("deleteData"));

        DeleteDataChange change = new DeleteDataChange();
        change.setTableName("test_delete");
        change.setWhere("id = '1'");

        executor.execute(change, context, dialect);

        assertEquals(0, countRows("test_delete"));
    }

    // ---- dialect-specific DDL shapes (cross-dialect executor regression) ----

    private CreateTableChange newAutoIncrementTable() {
        CreateTableChange change = new CreateTableChange();
        change.setName("t_autoinc");
        ColumnDefinition col = new ColumnDefinition();
        col.setName("id");
        col.setType(StdSqlType.INTEGER);
        col.setAutoIncrement(true);
        col.setPrimaryKey(true);
        change.setColumns(java.util.Collections.singletonList(col));
        return change;
    }

    @Test
    public void testAutoIncrementClausePerDialect() {
        CreateTableExecutor executor = new CreateTableExecutor();

        String mysql = executor.buildCreateTableSql(newAutoIncrementTable(), DialectManager.instance().getDialect("mysql"));
        assertTrue(mysql.contains("AUTO_INCREMENT"), "mysql uses AUTO_INCREMENT: " + mysql);

        String postgres = executor.buildCreateTableSql(newAutoIncrementTable(), DialectManager.instance().getDialect("postgresql"));
        assertTrue(postgres.contains("GENERATED BY DEFAULT AS IDENTITY"), "postgresql must not get AUTO_INCREMENT: " + postgres);

        String oracle = executor.buildCreateTableSql(newAutoIncrementTable(), DialectManager.instance().getDialect("oracle"));
        assertTrue(oracle.contains("GENERATED BY DEFAULT AS IDENTITY"), "oracle must not get AUTO_INCREMENT: " + oracle);

        String mssql = executor.buildCreateTableSql(newAutoIncrementTable(), DialectManager.instance().getDialect("mssql"));
        assertTrue(mssql.contains("IDENTITY(1,1)"), "mssql uses IDENTITY(1,1): " + mssql);
    }

    @Test
    public void testInlineTableCommentOnlyOnMySqlFamily() {
        CreateTableExecutor executor = new CreateTableExecutor();
        CreateTableChange change = newAutoIncrementTable();
        change.setRemark("table comment");

        List<String> mysqlStatements = executor.buildCreateTableStatements(change, DialectManager.instance().getDialect("mysql"));
        assertTrue(mysqlStatements.size() == 1 && mysqlStatements.get(0).contains("COMMENT 'table comment'"),
            "mysql supports the inline table COMMENT clause");

        for (String dialectName : new String[]{"postgresql", "oracle", "h2"}) {
            List<String> statements = executor.buildCreateTableStatements(change, DialectManager.instance().getDialect(dialectName));
            assertTrue(statements.get(0).indexOf("COMMENT") < 0,
                dialectName + " must not get the MySQL-only inline COMMENT clause: " + statements.get(0));
            assertEquals(2, statements.size(),
                dialectName + " must emit the remark as a separate COMMENT ON statement");
            assertTrue(statements.get(1).startsWith("COMMENT ON TABLE "),
                dialectName + " remark statement shape: " + statements.get(1));
        }
    }

    @Test
    public void testAlterColumnSqlShapePerDialect() {
        AlterColumnExecutor executor = new AlterColumnExecutor();

        AlterColumnChange change = new AlterColumnChange();
        change.setTableName("t_alter");
        change.setColumnName("name");
        change.setNewType(StdSqlType.VARCHAR);
        change.setNewSize(100);
        change.setNewNullable(false);

        List<String> postgres = executor.buildAlterColumnSqls(change, DialectManager.instance().getDialect("postgresql"));
        assertEquals(2, postgres.size(), "postgres splits type and nullability into two statements: " + postgres);
        assertTrue(postgres.get(0).contains("ALTER COLUMN name TYPE VARCHAR(100)"), postgres.get(0));
        assertTrue(postgres.get(1).contains("SET NOT NULL"), postgres.get(1));

        List<String> h2 = executor.buildAlterColumnSqls(change, dialect);
        assertEquals(2, h2.size(), "h2 splits type and nullability into two statements: " + h2);
        assertTrue(h2.get(0).contains("SET DATA TYPE VARCHAR(100)"),
            "h2 uses SET DATA TYPE, not the PostgreSQL-only TYPE keyword: " + h2.get(0));

        String mysql = String.join(";", executor.buildAlterColumnSqls(change, DialectManager.instance().getDialect("mysql")));
        assertTrue(mysql.contains("MODIFY COLUMN name VARCHAR(100) NOT NULL"),
            "mysql uses MODIFY COLUMN with the full definition: " + mysql);

        String oracle = String.join(";", executor.buildAlterColumnSqls(change, DialectManager.instance().getDialect("oracle")));
        assertTrue(oracle.contains("MODIFY (name VARCHAR2(100) NOT NULL)"),
            "oracle uses MODIFY (...): " + oracle);

        String mssql = String.join(";", executor.buildAlterColumnSqls(change, DialectManager.instance().getDialect("mssql")));
        assertTrue(mssql.contains("ALTER COLUMN name VARCHAR(100) NOT NULL"),
            "mssql uses ALTER COLUMN with the full definition: " + mssql);
    }

    @Test
    public void testAlterColumnExecutesOnH2() {
        executeUpdate("CREATE TABLE t_alter_h2 (id VARCHAR(36) PRIMARY KEY, name VARCHAR(50) NULL)");

        AlterColumnExecutor executor = new AlterColumnExecutor();
        AlterColumnChange change = new AlterColumnChange();
        change.setTableName("t_alter_h2");
        change.setColumnName("name");
        change.setNewType(StdSqlType.VARCHAR);
        change.setNewSize(100);
        change.setNewNullable(false);

        executor.execute(change, context, dialect);

        // SET NOT NULL must have taken effect
        assertThrows(Exception.class, () ->
            executeUpdate("INSERT INTO t_alter_h2 (id, name) VALUES ('1', NULL)"));
    }

    @Test
    public void testDropIndexSqlShapePerDialect() {
        DropIndexExecutor executor = new DropIndexExecutor();

        DropIndexChange change = new DropIndexChange();
        change.setName("idx_name");
        change.setTableName("t_drop_idx");

        String mysql = executor.buildDropIndexSql(change, DialectManager.instance().getDialect("mysql"));
        assertTrue(mysql.contains("ON t_drop_idx"), "mysql requires ON table: " + mysql);

        for (String dialectName : new String[]{"postgresql", "oracle", "h2", "mssql"}) {
            String sql = executor.buildDropIndexSql(change, DialectManager.instance().getDialect(dialectName));
            assertTrue(sql.indexOf(" ON ") < 0,
                dialectName + " drops the index by name only: " + sql);
        }
    }

    @Test
    public void testAddColumnSqlShapePerDialect() {
        AddColumnExecutor executor = new AddColumnExecutor();

        AddColumnChange change = new AddColumnChange();
        change.setTableName("t_add");
        ColumnDefinition col = new ColumnDefinition();
        col.setName("email");
        col.setType(StdSqlType.VARCHAR);
        col.setSize(100);
        change.setColumns(java.util.Collections.singletonList(col));

        String oracle = executor.buildAddColumnSql(change, col, DialectManager.instance().getDialect("oracle"));
        assertTrue(oracle.contains(" ADD (") && !oracle.contains("ADD COLUMN"),
            "oracle does not accept the ADD COLUMN keyword: " + oracle);

        String h2 = executor.buildAddColumnSql(change, col, dialect);
        assertTrue(h2.contains(" ADD COLUMN "), "h2 uses ADD COLUMN: " + h2);
    }

    // ---- table-level constraints regression ----

    @Test
    public void testCreateTableEmitsTableLevelConstraints() {
        CreateTableExecutor executor = new CreateTableExecutor();

        CreateTableChange change = new CreateTableChange();
        change.setName("t_constraints");

        ColumnDefinition id = new ColumnDefinition();
        id.setName("id");
        id.setType(StdSqlType.VARCHAR);
        id.setSize(36);
        id.setNullable(false);
        ColumnDefinition tenant = new ColumnDefinition();
        tenant.setName("tenant");
        tenant.setType(StdSqlType.VARCHAR);
        tenant.setSize(20);
        tenant.setNullable(false);
        ColumnDefinition email = new ColumnDefinition();
        email.setName("email");
        email.setType(StdSqlType.VARCHAR);
        email.setSize(100);
        change.setColumns(java.util.Arrays.asList(id, tenant, email));

        io.nop.db.migration.model.PrimaryKeyConstraint pk = new io.nop.db.migration.model.PrimaryKeyConstraint();
        pk.setColumnNames(new java.util.LinkedHashSet<>(java.util.Arrays.asList("id", "tenant")));
        change.setPrimaryKey(pk);

        io.nop.db.migration.model.UniqueConstraint unique = new io.nop.db.migration.model.UniqueConstraint();
        unique.setName("uk_t_constraints_email");
        unique.setColumnNames(new java.util.LinkedHashSet<>(java.util.Collections.singletonList("email")));
        change.setUniqueConstraint(unique);

        io.nop.db.migration.model.ForeignKeyConstraint fk = new io.nop.db.migration.model.ForeignKeyConstraint();
        fk.setName("fk_t_constraints_tenant");
        fk.setColumnNames(new java.util.LinkedHashSet<>(java.util.Collections.singletonList("tenant")));
        fk.setRefTableName("t_ref");
        fk.setRefColumnNames(new java.util.LinkedHashSet<>(java.util.Collections.singletonList("code")));
        fk.setOnDelete(io.nop.db.migration.ForeignKeyAction.CASCADE);
        change.setForeignKey(fk);

        String sql = executor.buildCreateTableSql(change, dialect);
        assertTrue(sql.contains("PRIMARY KEY (id, tenant)"),
            "composite primary key must be emitted, but was: " + sql);
        assertTrue(sql.contains("CONSTRAINT uk_t_constraints_email UNIQUE (email)"), sql);
        assertTrue(sql.contains("FOREIGN KEY (tenant) REFERENCES t_ref (code)"), sql);
        assertTrue(sql.contains("ON DELETE CASCADE"), sql);
    }

    @Test
    public void testTableLevelConstraintsAreEnforcedOnH2() {
        CreateTableExecutor executor = new CreateTableExecutor();

        executeUpdate("CREATE TABLE t_ref (code VARCHAR(20) PRIMARY KEY)");

        CreateTableChange change = new CreateTableChange();
        change.setName("t_enforced");

        ColumnDefinition id = new ColumnDefinition();
        id.setName("id");
        id.setType(StdSqlType.VARCHAR);
        id.setSize(36);
        id.setNullable(false);
        ColumnDefinition tenant = new ColumnDefinition();
        tenant.setName("tenant");
        tenant.setType(StdSqlType.VARCHAR);
        tenant.setSize(20);
        tenant.setNullable(false);
        change.setColumns(java.util.Arrays.asList(id, tenant));

        io.nop.db.migration.model.PrimaryKeyConstraint pk = new io.nop.db.migration.model.PrimaryKeyConstraint();
        pk.setColumnNames(new java.util.LinkedHashSet<>(java.util.Arrays.asList("id", "tenant")));
        change.setPrimaryKey(pk);

        // previously the executor silently ignored these declarations
        executor.execute(change, context, dialect);

        executeUpdate("INSERT INTO t_enforced (id, tenant) VALUES ('1', 'a')");
        assertThrows(Exception.class,
            () -> executeUpdate("INSERT INTO t_enforced (id, tenant) VALUES ('1', 'a')"),
            "the composite primary key must be enforced in the created table");
    }

    // ---- DEFAULT value injection hardening ----

    @Test
    public void testSqlChangePicksSqlServerSpecificSqlForMssqlDialect() {
        SqlChange change = new SqlChange();
        change.setId("sql-1");
        DbSpecificSql specific = new DbSpecificSql();
        specific.setDbType("sqlserver");
        specific.setBody("SELECT 1 FROM mssql_only");
        change.setDbSpecific(java.util.Collections.singletonList(specific));

        SqlExecutor executor = new SqlExecutor();
        String sql = executor.getSqlForDialect(change, DialectManager.instance().getDialect("mssql"));

        assertEquals("SELECT 1 FROM mssql_only", sql, "dbType=\"sqlserver\" must match the mssql dialect");
    }

    @Test
    public void testCreateTableRejectsInjectedDefaultValue() {
        CreateTableExecutor executor = new CreateTableExecutor();

        CreateTableChange change = new CreateTableChange();
        change.setName("t_inject");
        ColumnDefinition col = new ColumnDefinition();
        col.setName("name");
        col.setType(StdSqlType.VARCHAR);
        col.setSize(50);
        col.setDefaultValue("x'); DROP TABLE t_inject; --");
        change.setColumns(java.util.Collections.singletonList(col));

        io.nop.api.core.exceptions.NopException e = assertThrows(io.nop.api.core.exceptions.NopException.class,
            () -> executor.execute(change, context, dialect));
        assertEquals("nop.err.db-migration.invalid-default-value", e.getErrorCode());
    }

    @Test
    public void testAddColumnRejectsInjectedDefaultValue() {
        AddColumnExecutor executor = new AddColumnExecutor();
        executeUpdate("CREATE TABLE t_inject2 (id VARCHAR(36) PRIMARY KEY)");

        AddColumnChange change = new AddColumnChange();
        change.setTableName("t_inject2");
        ColumnDefinition col = new ColumnDefinition();
        col.setName("name");
        col.setType(StdSqlType.VARCHAR);
        col.setSize(50);
        col.setDefaultValue("0 /* comment */");
        change.setColumns(java.util.Collections.singletonList(col));

        io.nop.api.core.exceptions.NopException e = assertThrows(io.nop.api.core.exceptions.NopException.class,
            () -> executor.execute(change, context, dialect));
        assertEquals("nop.err.db-migration.invalid-default-value", e.getErrorCode());
    }

    private void executeUpdate(String sql) {
        jdbcTemplate.executeUpdate(SQL.begin().append(sql).end());
    }
    
    private boolean tableExists(String tableName) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = UPPER('" + tableName + "')")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean columnExists(String tableName, String columnName) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = UPPER('" + tableName + "') AND COLUMN_NAME = UPPER('" + columnName + "')")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean indexExists(String indexName) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME = UPPER('" + indexName + "')")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
    
    private int countRows(String tableName) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }
    
    private String getSingleValue(String sql) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private Integer queryIntValue(String sql) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int value = rs.getInt(1);
                return rs.wasNull() ? null : value;
            }
        } catch (Exception e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
        return null;
    }

    private Boolean queryBoolValue(String sql) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                boolean value = rs.getBoolean(1);
                return rs.wasNull() ? null : value;
            }
        } catch (Exception e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
        return null;
    }
}
