/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class JdbcTestCase extends BaseTestCase {
    private HikariDataSource dataSource;
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

    public int getActiveConnections() {
        return dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public void checkNoActive() {
        assertTrue(getActiveConnections() == 0);
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
        if (dataSource != null) {
            dataSource.close();
        }
    }

    protected HikariDataSource createDataSource() {
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
