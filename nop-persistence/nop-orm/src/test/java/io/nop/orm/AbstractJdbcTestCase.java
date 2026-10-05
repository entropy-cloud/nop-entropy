/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.sql_lib.SqlLibManager;
import io.nop.orm.sql_lib.dict.SqlLibDictLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

public abstract class AbstractJdbcTestCase extends BaseTestCase {
    // plan 2319：类型放宽为DataSource，允许子类覆写createDataSource()返回路由DataSource等装饰器
    protected DataSource dataSource;
    protected IJdbcTemplate jdbcTemplate;
    protected SqlLibManager sqlLibManager;
    protected IOrmTemplate ormTemplate;
    protected ITransactionTemplate transactionTemplate;

    private SqlLibDictLoader dictLoader;

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
        dictLoader = new SqlLibDictLoader();
        dictLoader.setSqlLibManager(sqlLibManager);
        dictLoader.init();
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
        if(dictLoader != null)
            dictLoader.destroy();

        // plan 2319：dataSource可能为非AutoCloseable的装饰器DataSource（如路由DataSource），
        // 物理连接池由测试子类自行管理；可关闭的实现（HikariDataSource等）在此统一关闭
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                throw new IllegalStateException("close test dataSource failed:" + dataSource, e);
            }
        }
    }

    protected DataSource createDataSource() {
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
        if (ormTemplate != null) {
            OrmDaoProvider __daoProvider = new OrmDaoProvider();
            __daoProvider.setOrmTemplate(ormTemplate);
            sqlLibManager.setDaoProvider(__daoProvider);
        }
        return sqlLibManager;
    }

    protected IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    protected ITransactionTemplate txn() {
        return jdbc().txn();
    }
}
