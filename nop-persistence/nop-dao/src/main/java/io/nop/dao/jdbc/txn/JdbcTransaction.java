/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.txn;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.IoHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.impl.JdbcHelper;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.txn.impl.AbstractTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class JdbcTransaction extends AbstractTransaction implements IJdbcTransaction {
    static final Logger LOG = LoggerFactory.getLogger(JdbcTransaction.class);
    static final AtomicLong S_SEQ = new AtomicLong();

    private final Supplier<Connection> dataSource;
    private final IDialect dialect;
    private final String txnId = String.valueOf(S_SEQ.incrementAndGet());

    /**
     * 提交或者回滚后是否自动释放连接。每次访问连接时再重新获取
     */
    private final boolean eagerReleaseConnection;

    private Connection connection;

    private final IDaoMetrics daoMetrics;

    public JdbcTransaction(String querySpace, Supplier<Connection> dataSource, IDialect dialect,
                           boolean eagerReleaseConnection, IDaoMetrics daoMetrics) {
        super(querySpace);
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.eagerReleaseConnection = eagerReleaseConnection;
        this.daoMetrics = daoMetrics;
    }

    @Override
    public String getTxnId() {
        return txnId;
    }

    // plan 2517 R2-14 裁定⑩（C2-017/C1-026）：事务实例自身为 monitor 串行化五触点
    // （getConnection/doCommit/doRollback/releaseConnection/doClose，getConnection0 同监视器）。
    // 临界区内含池获取 dataSource.get()；禁止锁内再取 registry/CHM 全局锁（锁序总表 §4.6）。
    // 与裁定⑨ synchronized(this) 同对象监视器（可重入安全，§4.6 同对象注）。
    Connection getConnection0() {
        if (connection == null)
            connection = dataSource.get();
        return connection;
    }

    /**
     * 延迟获取连接。
     *
     * <p>2.3-C4-003（plan 2553 裁定 K 声明面）：获取点线程语义声明——本方法可能在事务开启线程
     * 或异步完成/context 排空线程（flush/commit 回调链）上首次触发，连接池等待会放大该线程的
     * 停顿窗（R1 取证：懒取点 :65-83，2517-⑩ 已加 synchronized）。慢获取（>1s）以 WARN 留痕。
     */
    @Override
    public Connection getConnection() {
        synchronized (this) {
            if (connection == null) {
                LOG.trace("nop.dao.txn.fetch-connection:conn={}", this);
                long beginNano = CoreMetrics.nanoTime(); // plan 2572 P4：时钟口径统一
                connection = dataSource.get();
                long elapsedMs = (CoreMetrics.nanoTime() - beginNano) / 1_000_000L;
                if (elapsedMs > 1000L) {
                    LOG.warn("nop.warn.dao.txn-connection-fetch-slow:txn={},elapsedMs={}", this, elapsedMs);
                }
                if (isTransactionOpened()) {
                    if (JdbcHelper.getAutoCommit(connection, dialect)) {
                        JdbcHelper.setAutoCommit(connection, false, dialect);
                    }
                }
                if (daoMetrics != null) {
                    daoMetrics.onObtainConnection();
                }
            }
            return connection;
        }
    }

    @Override
    protected void doRollback(Throwable error) {
        synchronized (this) {
            if (connection == null)
                return;

            LOG.info("nop.dao.jdbc.rollback:{}", this);
            JdbcHelper.rollback(connection, dialect);
            // eagerRelease=false时连接被保留，必须恢复autoCommit，
            // 否则后续复用连接执行的写操作运行在永不提交的隐式事务中
            restoreAutoCommit();
            if (eagerReleaseConnection)
                releaseConnection();
        }
    }

    @Override
    protected void doCommit() {
        synchronized (this) {
            if (connection == null) {
                // 裁定⑩：null 早退补 WARN 留痕（非静默 no-op；eagerRelease+正常 close 双路径下 null
                // 为合法终态，不改抛错语义）。可观测指标：nop.dao.jdbc.commit-on-released-connection
                LOG.warn("nop.dao.jdbc.commit-on-released-connection:txn={}", this);
                return;
            }

            JdbcHelper.commit(connection, dialect);
            restoreAutoCommit();
            if (eagerReleaseConnection)
                releaseConnection();
        }
    }

    private void restoreAutoCommit() {
        if (connection == null)
            return;
        try {
            if (!connection.getAutoCommit())
                JdbcHelper.setAutoCommit(connection, true, dialect);
        } catch (Exception e) {
            LOG.info("nop.jdbc.restore-auto-commit-fail:{}", this, e);
        }
    }

    public void releaseConnection() {
        synchronized (this) {
            if (connection != null) {
                LOG.trace("nop.dao.txn.release-connection:conn={}", this);

                IoHelper.safeClose(connection);
                this.connection = null;
            }
        }
    }

    @Override
    protected void doClose() {
        releaseConnection();
    }

    @Override
    protected void doOpen() {
        synchronized (this) {
            Connection conn = getConnection0();
            if (JdbcHelper.getAutoCommit(conn, dialect)) {
                JdbcHelper.setAutoCommit(conn, false, dialect);
            }
        }
    }
}