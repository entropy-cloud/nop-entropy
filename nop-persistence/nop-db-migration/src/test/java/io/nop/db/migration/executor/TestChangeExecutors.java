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
import io.nop.db.migration.model.ColumnDefinition;
import io.nop.db.migration.model.CreateIndexChange;
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

        String sql = executor.buildCreateTableSql(change, dialect);
        assertTrue(sql.contains("COMMENT 'it''s a test'"),
            "single quotes in the remark must be escaped, but was: " + sql);
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
