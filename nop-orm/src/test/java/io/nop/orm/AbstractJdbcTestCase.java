/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SqlHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.sql_lib.SqlLibManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public abstract class AbstractJdbcTestCase extends BaseTestCase {
    protected HikariDataSource dataSource;
    protected IJdbcTemplate jdbcTemplate;
    protected SqlLibManager sqlLibManager;
    protected IOrmTemplate ormTemplate;
    protected ITransactionTemplate transactionTemplate;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        dataSource = createDataSource();
        jdbcTemplate = createJdbcTemplate();
        ormTemplate = createOrmTemplate();
        sqlLibManager = createSqlLibManager();
        sqlLibManager.init();
        executeSqlFile("/init.sql");
    }

    protected void executeSqlFile(String sqlFile) {
        String text = ResourceHelper.readText(testResource(sqlFile));
        List<SQL> sqls = SqlHelper.splitSqlText(text);
        for (SQL sql : sqls) {
            jdbcTemplate.executeUpdate(sql);
        }
    }

    @AfterEach
    public void tearDown() {
        if (sqlLibManager != null)
            sqlLibManager.destroy();

        if (dataSource != null) {
            dataSource.close();
        }
    }

    protected HikariDataSource createDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID());
        return ds;
    }

    protected IJdbcTemplate createJdbcTemplate() {
        JdbcFactory factory = new JdbcFactory();
        transactionTemplate = factory.newTransactionTemplate(dataSource);
        return factory.newJdbcTemplate(transactionTemplate);
    }

    protected IOrmTemplate createOrmTemplate() {
        return null;
    }

    protected SqlLibManager createSqlLibManager() {
        SqlLibManager sqlLibManager = new SqlLibManager();
        sqlLibManager.setJdbcTemplate(jdbcTemplate);
        sqlLibManager.setOrmTemplate(ormTemplate);
        return sqlLibManager;
    }

    protected IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    protected ITransactionTemplate txn() {
        return jdbc().txn();
    }
}
