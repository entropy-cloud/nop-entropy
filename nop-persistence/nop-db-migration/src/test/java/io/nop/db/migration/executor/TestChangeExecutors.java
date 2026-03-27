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
}
