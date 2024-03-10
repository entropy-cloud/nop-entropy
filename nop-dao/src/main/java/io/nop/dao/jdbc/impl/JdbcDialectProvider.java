/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.impl;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.dao.jdbc.txn.IJdbcTransaction;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.utils.DaoHelper;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ARG_TXN;
import static io.nop.dao.DaoErrors.ERR_DAO_QUERY_SPACE_NOT_JDBC_CONNECTION;

public class JdbcDialectProvider implements IDialectProvider {
    private final ITransactionTemplate transactionTemplate;
    private final Map<String, String> querySpaceToDialectMap = new ConcurrentHashMap<>();

    public JdbcDialectProvider(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = Guard.notNull(transactionTemplate, "transactionTemplate");
    }

    public void setQuerySpaceToDialectMap(Map<String, String> map) {
        if (map != null) {
            querySpaceToDialectMap.putAll(map);
        }
    }

    public void setQuerySpaceToDialectConfig(String config) {
        Map<String, String> map = StringHelper.parseStringMap(config, '=', ',');
        setQuerySpaceToDialectMap(map);
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);
        String dialectName = querySpaceToDialectMap.get(querySpace);
        if (dialectName != null)
            return DialectManager.instance().getDialect(dialectName);

        IDialect dialect = getDialectFromTxn(querySpace);
        querySpaceToDialectMap.put(querySpace, dialect.getName());
        return dialect;
    }

    private IDialect getDialectFromTxn(String querySpace) {
        return transactionTemplate.runInTransaction(querySpace, TransactionPropagation.SUPPORTS, txn -> {
            if (!(txn instanceof IJdbcTransaction))
                throw new NopException(ERR_DAO_QUERY_SPACE_NOT_JDBC_CONNECTION).param(ARG_QUERY_SPACE, querySpace)
                        .param(ARG_TXN, txn);

            // 如果没有调用过transaction.open，则这里返回的连接实际上是非事务状态的。
            Connection conn = ((IJdbcTransaction) txn).getConnection();
            return DialectManager.instance().getDialectForConnection(conn);
        });
    }
}
