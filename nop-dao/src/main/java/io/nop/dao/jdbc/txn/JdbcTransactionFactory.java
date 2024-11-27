/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.txn;

import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class JdbcTransactionFactory implements ITransactionFactory {
    private final DataSource dataSource;
    private final IDialect dialect;
    private boolean eagerReleaseConnection = true;
    private IDaoMetrics daoMetrics;

    public JdbcTransactionFactory(DataSource dataSource, String dialectName) {
        this.dataSource = dataSource;
        this.dialect = dialectName != null ? DialectManager.instance().getDialect(dialectName)
                : DialectManager.instance().getDialectForDataSource(dataSource);
    }

    public JdbcTransactionFactory(DataSource dataSource) {
        this(dataSource, null);
    }

    public void setEagerReleaseConnection(boolean eagerReleaseConnection) {
        this.eagerReleaseConnection = eagerReleaseConnection;
    }

    public IDaoMetrics getDaoMetrics() {
        return daoMetrics;
    }

    public void setDaoMetrics(IDaoMetrics daoMetrics) {
        this.daoMetrics = daoMetrics;
    }

    @Override
    public IDialect getDialectForQuerySpace(String txnGroup) {
        return dialect;
    }

    Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("ds.getConnection", e);
        }
    }

    @Override
    public ITransaction newTransaction(String querySpace) {
        return new JdbcTransaction(querySpace, this::getConnection, dialect, eagerReleaseConnection, daoMetrics);
    }

    @Override
    public Connection openConnection(String txnGroup) {
        return getConnection();
    }
}