/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.quarkus.autoconfig.dao;

import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import io.nop.dao.jdbc.txn.JdbcTransactionFactory;
import io.nop.dao.txn.ITransactionFactory;
import io.nop.dao.txn.ITransactionManager;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dao.txn.impl.DefaultTransactionManager;
import io.nop.dao.txn.impl.TransactionTemplateImpl;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.sql.DataSource;

@Dependent
public class NopDaoAutoConfiguration {

    @Produces
    public ITransactionManager nopTransactionManager(ITransactionFactory txnFactory) {
        DefaultTransactionManager txnManager = new DefaultTransactionManager();
        txnManager.setDefaultFactory(txnFactory);
        return txnManager;
    }

    @Produces
    public ITransactionFactory nopTransactionFactory(DataSource dataSource) {
        IDialect dialect = DialectManager.instance().getDialectForDataSource(dataSource);
        JdbcTransactionFactory txnFactory = new JdbcTransactionFactory(dataSource, null);
        return txnFactory;
    }

    @Produces
    public ITransactionTemplate nopTransactionTemplate() {
        return new TransactionTemplateImpl();
    }

    @Produces
    public IJdbcTemplate nopJdbcTemplate() {
        return new JdbcTemplateImpl();
    }
}