/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConfigs;
import io.nop.dao.DaoErrors;
import io.nop.dao.jdbc.JdbcTestCase;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.txn.impl.TransactionTemplateImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.UNSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2319 Phase 3：ReadWriteDataSourceInitializer装配两态测试。
 * 装配生效（default经setDefaultFactory替换、非default经addQuerySpace注册）、
 * 未配置零行为变更、配置错误快速失败。观察点沿用StubDataSource.lastResolved桩模式
 * （桩返回真实H2内存连接：JdbcTransactionFactory构造期经连接元数据解析方言，
 * 因此继承JdbcTestCase以获得CoreInitialization环境）。
 */
public class TestReadWriteDataSourceInitializer extends JdbcTestCase {

    static class StubDataSource implements DataSource {
        final String name;
        final String jdbcUrl = "jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1";
        int calls;
        static StubDataSource lastResolved;

        StubDataSource(String name) {
            this.name = name;
        }

        @Override
        public Connection getConnection() throws SQLException {
            calls++;
            lastResolved = this;
            return DriverManager.getConnection(jdbcUrl);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }
    }

    @AfterEach
    public void tearDown() {
        ReadWriteHint.leave(UNSET);
        StubDataSource.lastResolved = null;
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_READWRITE, null);
    }

    private ReadWriteDataSourceInitializer newInitializer(DefaultTransactionManager tm) {
        Map<String, DataSource> beans = new HashMap<>();
        beans.put("nopReadWriteDs_default_write", new StubDataSource("w_default"));
        beans.put("nopReadWriteDs_default_read1", new StubDataSource("r1"));
        beans.put("nopReadWriteDs_default_read2", new StubDataSource("r2"));
        beans.put("nopReadWriteDs_qs2_write", new StubDataSource("w2"));
        beans.put("nopReadWriteDs_qs2_read1", new StubDataSource("r3"));

        ReadWriteDataSourceInitializer initializer = new ReadWriteDataSourceInitializer();
        initializer.setTransactionManager(tm);
        initializer.setDataSources(beans);
        return initializer;
    }

    private String configJson() {
        return "{\"default\":{"
                + "\"write-ds\":\"nopReadWriteDs_default_write\","
                + "\"read-ds\":[\"nopReadWriteDs_default_read1\",\"nopReadWriteDs_default_read2\"],"
                + "\"load-balancer\":\"round-robin\",\"replica-fail-threshold\":2,\"replica-cooldown-seconds\":30},"
                + "\"qs2\":{"
                + "\"write-ds\":\"nopReadWriteDs_qs2_write\","
                + "\"read-ds\":[\"nopReadWriteDs_qs2_read1\"]}}";
    }

    @Test
    public void testUnconfiguredMeansZeroBehaviorChange() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        ReadWriteDataSourceInitializer initializer = newInitializer(tm);
        // 不设置配置
        initializer.init();

        assertTrue(tm.getNamedQuerySpaces().isEmpty(), "no querySpace may be registered without config");
        assertNull(tm.getTransactionFactory("default"), "defaultFactory must stay untouched");
    }

    @Test
    public void testConfigRegistersDefaultAndNamedQuerySpaces() throws SQLException {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_READWRITE, configJson());
        ReadWriteDataSourceInitializer initializer = newInitializer(tm);
        initializer.init();

        // 非default querySpace经addQuerySpace注册
        assertTrue(tm.isQuerySpaceDefined("qs2"));

        // default querySpace经setDefaultFactory替换，路由真实生效（UNSET→write）
        tm.openConnection("default");
        assertEquals("w_default", StubDataSource.lastResolved.name);

        // REPLICA hint下default querySpace路由到从库池
        ReadWriteHint.runWithReplica(() -> tm.openConnection("default"));
        assertEquals("r1", StubDataSource.lastResolved.name);

        // 非default querySpace路由
        StubDataSource.lastResolved = null;
        tm.openConnection("qs2");
        assertEquals("w2", StubDataSource.lastResolved.name);

        ReadWriteHint.runWithReplica(() -> tm.openConnection("qs2"));
        assertEquals("r3", StubDataSource.lastResolved.name);
    }

    /**
     * 接线验证（Minimum Rules #23）：初始化器构造的路由DS被DefaultTransactionManager真实使用——
     * 经事务模板执行的只读SQL在连接获取点命中从库
     */
    @Test
    public void testWiredThroughTransactionTemplateObservable() throws SQLException {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_READWRITE, configJson());
        newInitializer(tm).init();

        TransactionTemplateImpl template = new TransactionTemplateImpl();
        template.setTransactionManager(tm);
        JdbcTemplateImpl jdbc = new JdbcTemplateImpl();
        jdbc.setTransactionTemplate(template);

        // 只读SQL（querySpace=qs2）：桥接建立REPLICA作用域 → 路由DS命中从库
        SQL readOnly = SQL.begin().querySpace("qs2").sql("select 1").readOnly(true).end();
        jdbc.findAll(readOnly);
        assertEquals("r3", StubDataSource.lastResolved.name);

        // 无只读标志：走主库
        StubDataSource.lastResolved = null;
        SQL plain = SQL.begin().querySpace("qs2").sql("select 1").end();
        jdbc.findAll(plain);
        assertEquals("w2", StubDataSource.lastResolved.name);
    }

    @Test
    public void testUnknownBeanNameFailsFast() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_READWRITE,
                "{\"default\":{\"write-ds\":\"nopReadWriteDs_missing\",\"read-ds\":[\"nopReadWriteDs_default_read1\"]}}");
        ReadWriteDataSourceInitializer initializer = newInitializer(tm);
        NopException e = assertThrows(NopException.class, initializer::init);
        assertEquals(DaoErrors.ERR_DAO_READWRITE_CONFIG_INVALID.getErrorCode(), e.getErrorCode());
        assertTrue(StringHelper.isNotEmpty(String.valueOf(e.getParam("reason"))));
        // defaultFactory未被替换（isQuerySpaceDefined对default恒为true，故以defaultFactory状态断言）
        assertNull(tm.getTransactionFactory("default"));
        assertFalse(tm.isQuerySpaceDefined("qs2"));
    }

    @Test
    public void testInvalidJsonFailsFast() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_READWRITE, "not-a-json{");
        ReadWriteDataSourceInitializer initializer = newInitializer(tm);
        assertThrows(NopException.class, initializer::init);
    }

    @Test
    public void testMissingReadDsFailsFast() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_READWRITE,
                "{\"default\":{\"write-ds\":\"nopReadWriteDs_default_write\"}}");
        ReadWriteDataSourceInitializer initializer = newInitializer(tm);
        NopException e = assertThrows(NopException.class, initializer::init);
        assertEquals(DaoErrors.ERR_DAO_READWRITE_CONFIG_INVALID.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testUnknownLoadBalancerTypeFailsFast() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_READWRITE,
                "{\"default\":{\"write-ds\":\"nopReadWriteDs_default_write\","
                        + "\"read-ds\":[\"nopReadWriteDs_default_read1\"],\"load-balancer\":\"bogus\"}}");
        ReadWriteDataSourceInitializer initializer = newInitializer(tm);
        NopException e = assertThrows(NopException.class, initializer::init);
        assertEquals(DaoErrors.ERR_DAO_READWRITE_CONFIG_INVALID.getErrorCode(), e.getErrorCode());
    }
}
