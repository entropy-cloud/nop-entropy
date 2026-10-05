/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.impl;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.pagination.LimitOffsetPaginationHandler;
import io.nop.dao.dialect.pagination.OffsetFetchPaginationHandler;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.JdbcTestCase;
import io.nop.dao.jdbc.datasource.ReadWriteHint;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.txn.impl.TransactionTemplateImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.PRIMARY;
import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.REPLICA;
import static io.nop.dao.jdbc.datasource.ReadWriteHint.Hint.UNSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2319 Phase 1：JdbcTemplateImpl只读意图桥接的focused断言。
 *
 * <p>观察点：HintRecordingDataSource在getConnection()被调用时记录当时的ReadWriteHint状态，
 * 直接观测"连接获取点"上的hint作用域，无需依赖Phase 2的路由DataSource。
 * FakeDataSource.lastResolved桩模式（TestDynamicDataSource先例）的变体。</p>
 */
public class TestJdbcReadOnlyBridge extends JdbcTestCase {
    static final String QS = "rw_bridge_space";

    /**
     * 包装真实H2连接池，记录每次getConnection()调用时刻的ReadWriteHint状态
     */
    static class HintRecordingDataSource implements DataSource {
        final HikariDataSource delegate;
        final List<ReadWriteHint.Hint> hintsAtAcquisition = new ArrayList<>();

        HintRecordingDataSource() {
            delegate = new HikariDataSource();
            delegate.setMetricRegistry(GlobalMeterRegistry.instance());
            delegate.setDriverClassName("org.h2.Driver");
            delegate.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID());
        }

        @Override
        public Connection getConnection() throws SQLException {
            hintsAtAcquisition.add(ReadWriteHint.currentHint());
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        void close() {
            delegate.close();
        }
    }

    private HintRecordingDataSource recordingDs;
    private IJdbcTemplate jdbc;

    @BeforeEach
    public void setUpBridge() {
        recordingDs = new HintRecordingDataSource();
        DefaultTransactionManager tm = new DefaultTransactionManager();
        tm.addQuerySpace(QS, recordingDs);
        tm.setDefaultFactory(new JdbcTransactionFactory(recordingDs));

        TransactionTemplateImpl template = new TransactionTemplateImpl();
        template.setTransactionManager(tm);

        JdbcTemplateImpl template1 = new JdbcTemplateImpl();
        template1.setTransactionTemplate(template);
        jdbc = template1;

        jdbc.executeUpdate(new SQL("create table rw_bridge_entity(id int primary key, v varchar(20))"));
        jdbc.executeUpdate(new SQL("insert into rw_bridge_entity(id, v) values(1, 'a')"));
    }

    @AfterEach
    public void tearDownBridge() {
        if (recordingDs != null)
            recordingDs.close();
        // hint作用域由runWith的try/finally保证恢复（testHintScopeCleanedAfterQuery显式断言）
    }

    private SQL readOnlySelect() {
        return SQL.begin().querySpace(QS).sql("select id from rw_bridge_entity where id=1").readOnly(true).end();
    }

    private SQL plainSelect() {
        return new SQL("select id from rw_bridge_entity where id=1").extend().querySpace(QS).end();
    }

    private void clearRecordedHints() {
        recordingDs.hintsAtAcquisition.clear();
    }

    @Test
    public void testReadOnlySqlAcquiresInReplicaScope() {
        clearRecordedHints();
        jdbc.findAll(readOnlySelect());
        assertEquals(1, recordingDs.hintsAtAcquisition.size());
        assertEquals(REPLICA, recordingDs.hintsAtAcquisition.get(0));
    }

    @Test
    public void testPlainSqlAcquiresUnset() {
        clearRecordedHints();
        jdbc.findAll(plainSelect());
        assertEquals(1, recordingDs.hintsAtAcquisition.size());
        assertEquals(UNSET, recordingDs.hintsAtAcquisition.get(0));
    }

    @Test
    public void testExplicitPrimaryHintNotOverridden() {
        clearRecordedHints();
        ReadWriteHint.runWithPrimary(() -> jdbc.findAll(readOnlySelect()));
        assertEquals(1, recordingDs.hintsAtAcquisition.size());
        assertEquals(PRIMARY, recordingDs.hintsAtAcquisition.get(0));
    }

    @Test
    public void testExplicitReplicaHintPassesThrough() {
        clearRecordedHints();
        // 用户显式REPLICA作用域内，即使无readOnly标志，连接获取也在REPLICA作用域上
        ReadWriteHint.runWithReplica(() -> jdbc.findAll(plainSelect()));
        assertEquals(1, recordingDs.hintsAtAcquisition.size());
        assertEquals(REPLICA, recordingDs.hintsAtAcquisition.get(0));
    }

    @Test
    public void testOpenTransactionStaysPrimary() {
        clearRecordedHints();
        // 已打开事务：连接在事务open时缓存（此时无REPLICA作用域），
        // 事务内只读查询不得建立REPLICA作用域（一致性契约：事务内一律主库）
        jdbc.txn().runInTransaction(QS, TransactionPropagation.REQUIRED, txn -> jdbc.findAll(readOnlySelect()));

        assertFalse(recordingDs.hintsAtAcquisition.isEmpty());
        for (ReadWriteHint.Hint hint : recordingDs.hintsAtAcquisition) {
            assertEquals(UNSET, hint, "no replica scope may leak into an open transaction");
        }
    }

    @Test
    public void testPagedSqlKeepsReadOnlyFlag() {
        // 分页路径：executeQuery以range重建pagedSql（SQL.begin(sql)），
        // readOnly标志必须经重建保留（第1轮审查Blocker的回归钉）
        SQL paged = LimitOffsetPaginationHandler.INSTANCE.getPagedSql(LongRangeBean.of(0, 5), readOnlySelect());
        assertTrue(paged.isReadOnly(), "LimitOffset rebuild must preserve readOnly");

        SQL paged2 = OffsetFetchPaginationHandler.INSTANCE.getPagedSql(LongRangeBean.of(2, 5), readOnlySelect());
        assertTrue(paged2.isReadOnly(), "OffsetFetch rebuild must preserve readOnly");

        // 端到端：带range的执行走分页重建后的SQL，连接获取点上仍可见REPLICA作用域
        clearRecordedHints();
        jdbc.executeQuery(readOnlySelect(), LongRangeBean.of(0, 5), ds -> {
            while (ds.hasNext()) {
                ds.next();
            }
            return null;
        });
        assertFalse(recordingDs.hintsAtAcquisition.isEmpty());
        assertEquals(REPLICA, recordingDs.hintsAtAcquisition.get(recordingDs.hintsAtAcquisition.size() - 1));
    }

    @Test
    public void testHintScopeCleanedAfterQuery() {
        jdbc.findAll(readOnlySelect());
        // 桥接作用域在查询结束后必须恢复UNSET，不得残留到同线程后续操作
        assertEquals(UNSET, ReadWriteHint.currentHint());
    }
}
