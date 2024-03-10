/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.impl;

import io.nop.commons.cache.ICacheProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.IJdbcTemplateFactory;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.metrics.IDaoMetrics;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.txn.impl.TransactionTemplateImpl;

import javax.sql.DataSource;

/**
 * 根据DataSource创建JdbcTemplate对象
 */
public class JdbcFactory implements IJdbcTemplateFactory {
    private ICacheProvider cacheProvider;
    private IDaoMetrics daoMetrics;

    public void setCacheProvider(ICacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    public void setDaoMetrics(IDaoMetrics daoMetrics) {
        this.daoMetrics = daoMetrics;
    }

    public static IJdbcTemplate newJdbcTemplateFor(DataSource dataSource) {
        JdbcFactory factory = new JdbcFactory();
        return factory.newJdbcTemplate(factory.newTransactionTemplate(dataSource));
    }

    public IJdbcTemplate newJdbcTemplate(ITransactionTemplate txn) {
        JdbcTemplateImpl jdbc = new JdbcTemplateImpl();
        jdbc.setCacheProvider(cacheProvider);
        jdbc.setDaoMetrics(daoMetrics);
        jdbc.setTransactionTemplate(txn);
        return jdbc;
    }

    public TransactionTemplateImpl newTransactionTemplate(DataSource dataSource, String dialectName) {
        TransactionTemplateImpl txn = new TransactionTemplateImpl();
        DefaultTransactionManager txnManager = new DefaultTransactionManager();
        txnManager.setTransactionMetrics(daoMetrics);
        txnManager.setDefaultFactory(newTransactionFactory(dataSource, dialectName));
        txn.setTransactionManager(txnManager);
        return txn;
    }

    public JdbcTransactionFactory newTransactionFactory(DataSource dataSource, String dialectName) {
        JdbcTransactionFactory factory = new JdbcTransactionFactory(dataSource, dialectName);
        factory.setDaoMetrics(daoMetrics);
        return factory;
    }
}