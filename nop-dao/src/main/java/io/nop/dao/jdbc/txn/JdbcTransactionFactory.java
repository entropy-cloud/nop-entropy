/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.jdbc.txn;

import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class JdbcTransactionFactory implements ITransactionFactory {
    private final DataSource dataSource;
    private final IDialect dialect;
    private boolean eagerReleaseConnection = true;

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

    Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw dialect.getSQLExceptionTranslator().translate("ds.getConnection", e);
        }
    }

    @Override
    public ITransaction newTransaction(String querySpace) {
        return new JdbcTransaction(querySpace, this::getConnection, dialect, eagerReleaseConnection);
    }
}