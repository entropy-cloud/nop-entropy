/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn.impl;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
import io.nop.dao.jdbc.JdbcTestCase;
import io.nop.dao.jdbc.txn.IJdbcTransaction;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionMetrics;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.dao.DaoErrors.ERR_DAO_UNKNOWN_QUERY_SPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 覆盖补强：DefaultTransactionManager 的 querySpace 注册/解析与真实 H2 事务语义。
 */
public class TestDefaultTransactionManager extends JdbcTestCase {
    static final String QS = "tm_test_space";

    @Test
    public void testMainTxnGroupDefaultsAndMapping() {
        DefaultTransactionManager tm = new DefaultTransactionManager();

        // 未配置映射时统一归于缺省事务组
        assertEquals(DaoConstants.DEFAULT_TXN_GROUP, tm.getMainTxnGroup(null));
        assertEquals(DaoConstants.DEFAULT_TXN_GROUP, tm.getMainTxnGroup("any_space"));

        tm.setTxnGroupMapConfig("tm_test_space=groupB,batch=groupC");
        assertEquals("groupB", tm.getMainTxnGroup("tm_test_space"));
        assertEquals("groupC", tm.getMainTxnGroup("batch"));
        // 未命中的querySpace仍归缺省组
        assertEquals(DaoConstants.DEFAULT_TXN_GROUP, tm.getMainTxnGroup("other"));
    }

    @Test
    public void testQuerySpaceRegistrationLifecycle() {
        DefaultTransactionManager tm = new DefaultTransactionManager();

        // 缺省querySpace永远视为已定义
        assertTrue(tm.isQuerySpaceDefined(null));
        assertFalse(tm.isQuerySpaceDefined("no_such_space"));

        HikariDataSource ds = new HikariDataSource();
        ds.setMetricRegistry(GlobalMeterRegistry.instance());
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID());
        try {
            tm.addQuerySpace(QS, ds);
            assertTrue(tm.isQuerySpaceDefined(QS));
            assertTrue(tm.getNamedQuerySpaces().contains(QS));
            assertNotNull(tm.getTransactionFactory(QS));

            tm.removeQuerySpace(QS);
            assertFalse(tm.isQuerySpaceDefined(QS));
        } finally {
            ds.close();
        }
    }

    @Test
    public void testUnknownQuerySpaceThrowsWithParam() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        NopException e = assertThrows(NopException.class, () -> tm.getTransactionFactory("no_such_space"));
        assertEquals(ERR_DAO_UNKNOWN_QUERY_SPACE.getErrorCode(), e.getErrorCode());
        assertEquals("no_such_space", e.getParam("querySpace"));
    }

    @Test
    public void testAddQuerySpaceRejectsEmptyArguments() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        assertThrows(IllegalArgumentException.class, () -> tm.addQuerySpace(null, getDataSource()));
        assertThrows(IllegalArgumentException.class, () -> tm.addQuerySpace("", getDataSource()));
        assertThrows(IllegalArgumentException.class, () -> tm.addQuerySpace(QS, null));
    }

    /**
     * 记录型事务指标，验证manager把metrics listener挂到新事务上
     */
    static class RecordingMetrics implements ITransactionMetrics {
        final List<String> opened = new ArrayList<>();
        final List<String> success = new ArrayList<>();
        final List<String> failure = new ArrayList<>();

        @Override
        public void onTransactionOpen(String txnGroup) {
            opened.add(txnGroup);
        }

        @Override
        public void onTransactionSuccess(String txnGroup) {
            success.add(txnGroup);
        }

        @Override
        public void onTransactionFailure(String txnGroup) {
            failure.add(txnGroup);
        }
    }

    @Test
    public void testNewTransactionCommitPersistsData() throws Exception {
        DefaultTransactionManager tm = newManager();

        ITransaction txn = tm.newTransaction(QS);
        assertFalse(txn.isTransactionOpened());
        assertEquals(QS, txn.getTxnGroup());

        txn.open();
        assertTrue(txn.isTransactionOpened());
        // 事务内连接必须经事务对象获取，参与提交/回滚
        Connection conn = ((IJdbcTransaction) txn).getConnection();
        try (PreparedStatement st = conn.prepareStatement(
                "create table tm_txn_entity(id int primary key, v varchar(20))")) {
            st.execute();
        }
        try (PreparedStatement st = conn.prepareStatement(
                "insert into tm_txn_entity(id, v) values(1, 'committed')")) {
            st.executeUpdate();
        }
        txn.commit();
        txn.close();
        assertFalse(txn.isTransactionOpened());

        // 提交后的数据经另一个连接可见
        List<Map<String, Object>> rows = jdbc().findAll(new SQL("select id, v from tm_txn_entity"));
        assertEquals(1, rows.size());
        assertEquals("committed", rows.get(0).get("V"));
    }

    @Test
    public void testNewTransactionRollbackDiscardsData() throws Exception {
        DefaultTransactionManager tm = newManager();
        jdbc().executeUpdate(new SQL("create table tm_txn_rb(id int primary key, v varchar(20))"));

        ITransaction txn = tm.newTransaction(QS);
        txn.open();
        Connection conn = ((IJdbcTransaction) txn).getConnection();
        try (PreparedStatement st = conn.prepareStatement(
                "insert into tm_txn_rb(id, v) values(1, 'rolled-back')")) {
            st.executeUpdate();
        }
        txn.rollback(new IllegalStateException("test"));
        txn.close();

        // 回滚后数据不可见
        List<Map<String, Object>> rows = jdbc().findAll(new SQL("select id, v from tm_txn_rb"));
        assertEquals(0, rows.size());
    }

    @Test
    public void testTransactionMetricsListenerAttached() {
        DefaultTransactionManager tm = newManager();
        RecordingMetrics metrics = new RecordingMetrics();
        tm.setTransactionMetrics(metrics);

        ITransaction txn = tm.newTransaction(QS);
        txn.open();
        txn.commit();
        txn.close();

        // metrics listener 记录了同组事务的开启与成功
        assertEquals(1, metrics.opened.size());
        assertEquals(QS, metrics.opened.get(0));
        assertEquals(1, metrics.success.size());
        assertEquals(0, metrics.failure.size());
    }

    @Test
    public void testTransactionMetricsRecordsFailure() {
        DefaultTransactionManager tm = newManager();
        RecordingMetrics metrics = new RecordingMetrics();
        tm.setTransactionMetrics(metrics);

        ITransaction txn = tm.newTransaction(QS);
        txn.open();
        txn.rollback(new IllegalStateException("boom"));
        txn.close();

        assertEquals(1, metrics.failure.size());
        assertEquals(0, metrics.success.size());
    }

    /**
     * 独立的DefaultTransactionManager，挂接当前测试的H2数据源
     */
    private DefaultTransactionManager newManager() {
        DefaultTransactionManager tm = new DefaultTransactionManager();
        DataSource ds = getDataSource();
        tm.addQuerySpace(QS, ds);
        // 缺省querySpace注册为defaultFactory
        tm.setDefaultFactory(tm.getTransactionFactory(QS));
        return tm;
    }
}
