/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class AbstractMigrationTestCase {
    protected HikariDataSource dataSource;
    protected IJdbcTemplate jdbcTemplate;
    protected ITransactionTemplate transactionTemplate;
    protected IDialect dialect;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    protected void setUp() throws Exception {
        dataSource = createH2DataSource();
        jdbcTemplate = createJdbcTemplate();
        dialect = DialectManager.instance().getDialect("h2");

        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP ALL OBJECTS");
            }
        } catch (SQLException e) {
            // Ignore if no objects to drop
        }
    }

    @AfterEach
    protected void tearDown() throws Exception {
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP ALL OBJECTS");
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
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

    protected void executeSql(String sql) {
        jdbcTemplate.executeUpdate(SQL.begin().append(sql).end());
    }

    protected boolean tableExists(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                java.sql.ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '" + tableName.toUpperCase() + "'"
                );
                boolean exists = rs.next();
                rs.close();
                return exists;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    protected int countRows(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            return 0;
        }
        return 0;
    }
}
