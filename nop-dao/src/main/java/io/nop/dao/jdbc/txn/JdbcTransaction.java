/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.txn;

import io.nop.commons.util.IoHelper;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.impl.JdbcHelper;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.txn.impl.AbstractTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.function.Supplier;

public class JdbcTransaction extends AbstractTransaction implements IJdbcTransaction {
    static final Logger LOG = LoggerFactory.getLogger(JdbcTransaction.class);
    private final Supplier<Connection> dataSource;
    private final IDialect dialect;

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

    Connection getConnection0() {
        if (connection == null)
            connection = dataSource.get();
        return connection;
    }

    // 延迟获取连接
    @Override
    public Connection getConnection() {
        if (connection == null) {
            LOG.trace("nop.dao.txn.fetch-connection:conn={}", this);
            connection = dataSource.get();
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

    @Override
    protected void doRollback(Throwable error) {
        if (connection == null)
            return;

        JdbcHelper.rollback(connection, dialect);
        if (eagerReleaseConnection)
            releaseConnection();
    }

    @Override
    protected void doCommit() {
        if (connection == null)
            return;

        JdbcHelper.commit(connection, dialect);
        if (eagerReleaseConnection)
            releaseConnection();
    }

    public void releaseConnection() {
        if (connection != null) {
            LOG.trace("nop.dao.txn.release-connection:conn={}", this);

            IoHelper.safeClose(connection);
            this.connection = null;
        }
    }

    @Override
    protected void doClose() {
        releaseConnection();
    }

    @Override
    protected void doOpen() {
        Connection conn = getConnection0();
        if (JdbcHelper.getAutoCommit(conn, dialect)) {
            JdbcHelper.setAutoCommit(conn, false, dialect);
        }

    }
}