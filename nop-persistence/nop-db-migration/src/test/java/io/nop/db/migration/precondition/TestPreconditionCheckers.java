package io.nop.db.migration.precondition;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.db.migration.PreconditionExpect;
import io.nop.db.migration.core.MigrationContext;
import io.nop.db.migration.model.ColumnExistsPrecondition;
import io.nop.db.migration.model.ForeignKeyExistsPrecondition;
import io.nop.db.migration.model.IndexExistsPrecondition;
import io.nop.db.migration.model.TableExistsPrecondition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPreconditionCheckers {
    
    private static HikariDataSource dataSource;
    private static Connection connection;
    private static IJdbcTemplate jdbcTemplate;
    private static IDialect dialect;
    private static ITransactionTemplate txnTemplate;
    private MigrationContext context;
    
    @BeforeAll
    public static void init() throws SQLException {
        CoreInitialization.initialize();
        
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:test_precondition;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        
        dialect = DialectManager.instance().getDialect("h2");
        JdbcFactory factory = new JdbcFactory();
        txnTemplate = factory.newTransactionTemplate(dataSource, "h2");
        jdbcTemplate = factory.newJdbcTemplate(txnTemplate);
        
        connection = dataSource.getConnection();
    }
    
    @AfterAll
    public static void destroy() throws Exception {
        if (connection != null) {
            connection.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
        CoreInitialization.destroy();
    }
    
    @BeforeEach
    public void setUp() {
        context = new MigrationContext();
        context.setJdbcTemplate(jdbcTemplate);
        context.setDialect(dialect);
        context.setQuerySpace("default");
    }
    
    @AfterEach
    public void tearDown() {
        jdbcTemplate.executeUpdate(SQL.begin().append("DROP TABLE IF EXISTS test_table").end());
        jdbcTemplate.executeUpdate(SQL.begin().append("DROP TABLE IF EXISTS other_table").end());
    }
    
    @Test
    public void testTableExistsChecker_Exists() {
        jdbcTemplate.executeUpdate(SQL.begin().append("CREATE TABLE test_table (id VARCHAR(36) PRIMARY KEY)").end());
        
        TableExistsChecker checker = new TableExistsChecker();
        
        TableExistsPrecondition precondition = new TableExistsPrecondition();
        precondition.setTableName("test_table");
        precondition.setExpect(PreconditionExpect.EXISTS);
        
        assertTrue(checker.check(precondition, context));
    }
    
    @Test
    public void testTableExistsChecker_NotExists() {
        TableExistsChecker checker = new TableExistsChecker();
        
        TableExistsPrecondition precondition = new TableExistsPrecondition();
        precondition.setTableName("nonexistent_table");
        precondition.setExpect(PreconditionExpect.EXISTS);
        
        assertFalse(checker.check(precondition, context));
    }
    
    @Test
    public void testTableExistsChecker_ExpectNotExists() {
        jdbcTemplate.executeUpdate(SQL.begin().append("CREATE TABLE test_table (id VARCHAR(36) PRIMARY KEY)").end());
        
        TableExistsChecker checker = new TableExistsChecker();
        
        TableExistsPrecondition precondition = new TableExistsPrecondition();
        precondition.setTableName("test_table");
        precondition.setExpect(PreconditionExpect.NOT_EXISTS);
        
        assertFalse(checker.check(precondition, context));
    }
    
    @Test
    public void testColumnExistsChecker() {
        jdbcTemplate.executeUpdate(SQL.begin().append("CREATE TABLE test_table (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))").end());
        
        ColumnExistsChecker checker = new ColumnExistsChecker();
        
        ColumnExistsPrecondition preconditionExists = new ColumnExistsPrecondition();
        preconditionExists.setTableName("test_table");
        preconditionExists.setColumnName("name");
        preconditionExists.setExpect(PreconditionExpect.EXISTS);
        
        assertTrue(checker.check(preconditionExists, context));
        
        ColumnExistsPrecondition preconditionNotExists = new ColumnExistsPrecondition();
        preconditionNotExists.setTableName("test_table");
        preconditionNotExists.setColumnName("nonexistent_column");
        preconditionNotExists.setExpect(PreconditionExpect.EXISTS);
        
        assertFalse(checker.check(preconditionNotExists, context));
    }
    
    @Test
    public void testIndexExistsChecker() {
        jdbcTemplate.executeUpdate(SQL.begin().append("CREATE TABLE test_table (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100))").end());
        jdbcTemplate.executeUpdate(SQL.begin().append("CREATE INDEX idx_name ON test_table(name)").end());
        
        IndexExistsChecker checker = new IndexExistsChecker();
        
        IndexExistsPrecondition preconditionExists = new IndexExistsPrecondition();
        preconditionExists.setTableName("test_table");
        preconditionExists.setIndexName("idx_name");
        preconditionExists.setExpect(PreconditionExpect.EXISTS);
        
        assertTrue(checker.check(preconditionExists, context));
        
        IndexExistsPrecondition preconditionNotExists = new IndexExistsPrecondition();
        preconditionNotExists.setTableName("test_table");
        preconditionNotExists.setIndexName("nonexistent_idx");
        preconditionNotExists.setExpect(PreconditionExpect.EXISTS);
        
        assertFalse(checker.check(preconditionNotExists, context));
    }
    
    @Test
    public void testForeignKeyExistsChecker() {
        jdbcTemplate.executeUpdate(SQL.begin().append("CREATE TABLE other_table (id VARCHAR(36) PRIMARY KEY)").end());
        jdbcTemplate.executeUpdate(SQL.begin().append("CREATE TABLE test_table (id VARCHAR(36) PRIMARY KEY, other_id VARCHAR(36), CONSTRAINT fk_other FOREIGN KEY (other_id) REFERENCES other_table(id))").end());
        
        ForeignKeyExistsChecker checker = new ForeignKeyExistsChecker();
        
        ForeignKeyExistsPrecondition preconditionExists = new ForeignKeyExistsPrecondition();
        preconditionExists.setConstraintName("fk_other");
        preconditionExists.setTableName("test_table");
        preconditionExists.setExpect(PreconditionExpect.EXISTS);
        
        assertTrue(checker.check(preconditionExists, context));
        
        ForeignKeyExistsPrecondition preconditionNotExists = new ForeignKeyExistsPrecondition();
        preconditionNotExists.setConstraintName("nonexistent_fk");
        preconditionNotExists.setTableName("test_table");
        preconditionNotExists.setExpect(PreconditionExpect.EXISTS);
        
        assertFalse(checker.check(preconditionNotExists, context));
    }
}
