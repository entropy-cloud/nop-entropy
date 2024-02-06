/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.txn.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionFactory;
import io.nop.dao.txn.ITransactionListener;
import io.nop.dao.txn.ITransactionManager;
import io.nop.dao.txn.ITransactionMetrics;
import io.nop.dao.utils.DaoHelper;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ERR_DAO_UNKNOWN_QUERY_SPACE;

public class DefaultTransactionManager implements ITransactionManager {
    private Map<String, ITransactionFactory> transactionFactoryMap = new ConcurrentHashMap<>();
    private Map<String, String> txnGroupMap = new ConcurrentHashMap<>();

    private final Map<String, String> querySpaceToDialectMap = new ConcurrentHashMap<>();
    private ITransactionFactory defaultFactory;

    private ITransactionListener defaultListener;
    private ITransactionMetrics transactionMetrics;
    private ITransactionListener metricsListener = new ITransactionListener() {
        @Override
        public void onOpen(ITransaction txn) {
            transactionMetrics.onTransactionOpen(txn.getTxnGroup());
        }

        @Override
        public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
            if (status == CompleteStatus.COMMIT) {
                transactionMetrics.onTransactionSuccess(txn.getTxnGroup());
            } else {
                transactionMetrics.onTransactionFailure(txn.getTxnGroup());
            }
        }
    };

    public void setQuerySpaceToDialectMap(Map<String, String> map) {
        if (map != null) {
            querySpaceToDialectMap.putAll(map);
        }
    }

    public void setQuerySpaceToDialectConfig(String config) {
        Map<String, String> map = StringHelper.parseStringMap(config, '=', ',');
        setQuerySpaceToDialectMap(map);
    }

    public void setTransactionMetrics(ITransactionMetrics transactionMetrics) {
        this.transactionMetrics = transactionMetrics;
    }

    public void setTransactionFactoryMap(Map<String, ITransactionFactory> map) {
        this.transactionFactoryMap.putAll(map);
    }

    public void setDefaultFactory(ITransactionFactory factory) {
        this.defaultFactory = factory;
    }

    public void setDataSourceMap(Map<String, DataSource> dataSourceMap) {
        if (dataSourceMap != null) {
            for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
                String name = entry.getKey();
                DataSource ds = entry.getValue();

                transactionFactoryMap.put(name, new JdbcTransactionFactory(ds));
            }
        }
    }

    public Set<String> getNamedQuerySpaces() {
        return transactionFactoryMap.keySet();
    }

    public void addQuerySpace(String querySpace, DataSource ds) {
        Guard.notEmpty(querySpace, "querySpace");
        Guard.notNull(ds, "ds");
        transactionFactoryMap.put(querySpace, new JdbcTransactionFactory(ds));
    }

    public void removeQuerySpace(String querySpace) {
        this.transactionFactoryMap.remove(querySpace);
    }

    public void setTxnGroupMapConfig(String config) {
        Map<String, String> map = StringHelper.parseStringMap(config, '=', ',');
        if (map != null) {
            txnGroupMap.putAll(map);
        }
    }

    public ITransactionListener getDefaultListener() {
        return defaultListener;
    }

    @Inject
    @Named("nopDefaultTransactionListener")
    public void setDefaultListener(@Nullable ITransactionListener defaultListener) {
        this.defaultListener = defaultListener;
    }

    @Override
    public String getMainTxnGroup(String querySpace) {
        if (querySpace == null)
            return DaoConstants.DEFAULT_TXN_GROUP;

        // 如果没有明确指定事务分组，则实际上都归于缺省事务组
        String txnGroup = txnGroupMap.get(querySpace);
        if (txnGroup == null)
            txnGroup = DaoConstants.DEFAULT_TXN_GROUP;
        return txnGroup;
    }

    public void setTxnGroupMap(Map<String, String> txnGroupMap) {
        this.txnGroupMap.putAll(txnGroupMap);
    }

    @Override
    public ITransaction getRegisteredTransaction(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);

        ITransaction txn = TransactionRegistry.instance().get(querySpace);
        if (txn == null) {
            txn = getTransactionFactory(querySpace).getSynchronization(querySpace);
            if (txn != null && defaultListener != null) {
                txn.addListener(defaultListener);
            }
        }
        return txn;
    }

    @Override
    public ITransaction registerTransaction(ITransaction txn) {
        return TransactionRegistry.instance().put(txn.getTxnGroup(), txn);
    }

    @Override
    public boolean unregisterTransaction(ITransaction txn) {
        return TransactionRegistry.instance().remove(txn.getTxnGroup(), txn);
    }

    protected ITransactionFactory getTransactionFactory(String querySpace) {
        if (DaoHelper.isDefaultQuerySpace(querySpace))
            return defaultFactory;
        ITransactionFactory factory = transactionFactoryMap.get(querySpace);
        if (factory == null) {
            throw new NopException(ERR_DAO_UNKNOWN_QUERY_SPACE).param(ARG_QUERY_SPACE, querySpace);
        }
        return factory;
    }

    @Override
    public ITransaction newTransaction(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);

        ITransaction txn = getTransactionFactory(querySpace).newTransaction(querySpace);
        if (defaultListener != null)
            txn.addListener(defaultListener);

        if (transactionMetrics != null)
            txn.addListener(metricsListener);
        return txn;
    }

    @Override
    public boolean isQuerySpaceDefined(String querySpace) {
        if (DaoHelper.isDefaultQuerySpace(querySpace))
            return true;
        return transactionFactoryMap.containsKey(querySpace);
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);

        String dialectName = querySpaceToDialectMap.get(querySpace);
        if (dialectName != null)
            return DialectManager.instance().getDialect(dialectName);

        return getTransactionFactory(querySpace).getDialectForQuerySpace(querySpace);
    }
}