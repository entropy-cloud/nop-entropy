/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class JdbcTestCase extends BaseTestCase {
    // plan 2319：类型放宽为DataSource，允许子类覆写createDataSource()返回路由DataSource等装饰器
    private DataSource dataSource;
    private IJdbcTemplate jdbcTemplate;
    private ITransactionTemplate transactionTemplate;
    protected int maxPoolSize = 4;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    public ITransactionTemplate txn() {
        return transactionTemplate;
    }

    /**
     * 活跃连接数。dataSource为装饰器（如读写分离路由DataSource）时经unwrap取写库连接池指标
     */
    public int getActiveConnections() {
        return unwrapHikari().getHikariPoolMXBean().getActiveConnections();
    }

    public void checkNoActive() {
        assertTrue(getActiveConnections() == 0);
    }

    protected HikariDataSource unwrapHikari() {
        try {
            return dataSource.unwrap(HikariDataSource.class);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot unwrap HikariDataSource from test dataSource:" + dataSource, e);
        }
    }

    @BeforeEach
    public void setUp() {
        dataSource = createDataSource();
        jdbcTemplate = createJdbcTemplate();
        transactionTemplate = jdbcTemplate.txn();
        this.safeDropTable("my_entity");
        executeSqlFile(getInitSql());
    }

    public String getInitSql() {
        return "/init.sql";
    }

    public IDialect getDialect() {
        return DialectManager.instance().getDialectForDataSource(getDataSource());
    }

    protected void executeSqlFile(String sqlFile) {
        String text = ResourceHelper.readText(testResource(sqlFile));
        List<SQL> sqls = SqlHelper.splitSqlText(text);
        for (SQL sql : sqls) {
            jdbcTemplate.executeUpdate(sql);
        }
    }

    protected void safeDropTable(String tableName) {
        String sql = getDialect().getDropTableSql(tableName, true);
        try {
            jdbc().executeUpdate(new SQL(sql));
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    @AfterEach
    public void tearDown() {
        closeDataSource(dataSource);
    }

    /**
     * plan 2319：dataSource可能为非AutoCloseable的装饰器DataSource（如路由DataSource），
     * 物理连接池由测试子类自行管理；可关闭的实现（HikariDataSource等）在此统一关闭
     */
    protected void closeDataSource(DataSource ds) {
        if (ds == null)
            return;
        if (ds instanceof AutoCloseable) {
            try {
                ((AutoCloseable) ds).close();
            } catch (Exception e) {
                throw new IllegalStateException("close test dataSource failed:" + ds, e);
            }
        }
    }

    protected DataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID());
        ds.setMaximumPoolSize(maxPoolSize);
        return ds;
    }

    protected IJdbcTemplate createJdbcTemplate() {
        JdbcFactory factory = new JdbcFactory();
        transactionTemplate = factory.newTransactionTemplate(dataSource);
        return factory.newJdbcTemplate(transactionTemplate);
    }
}
