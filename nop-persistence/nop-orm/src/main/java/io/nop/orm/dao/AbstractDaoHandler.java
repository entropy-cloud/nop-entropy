/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.core.context.IEvalContext;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.sql_lib.ISqlLibManager;
import jakarta.inject.Inject;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 汇集各类DAO操作对象
 */
public class AbstractDaoHandler {
    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;
    private IOrmTemplate ormTemplate;
    private ITransactionTemplate transactionTemplate;
    private ISqlLibManager sqlLibManager;

    public IDaoProvider getDaoProvider() {
        return daoProvider;
    }

    @Inject
    public void setSqlLibManager(ISqlLibManager sqlLibManager) {
        this.sqlLibManager = sqlLibManager;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    protected IJdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    protected IOrmTemplate orm() {
        return ormTemplate;
    }

    protected ITransactionTemplate txn() {
        return transactionTemplate;
    }

    protected <T extends IDaoEntity> IEntityDao<T> dao(String entityName) {
        return daoProvider.dao(entityName);
    }

    protected <T extends IDaoEntity> IEntityDao<T> daoFor(Class<T> entityClass) {
        return daoProvider.daoFor(entityClass);
    }

    protected Object executeSql(String sqlName, LongRangeBean range, IEvalContext context) {
        return sqlLibManager.invoke(sqlName, range, context);
    }

    protected Object executeSql(String sqlName, IEvalContext context) {
        return sqlLibManager.invoke(sqlName, null, context);
    }

    protected <T> T runInTransaction(final Supplier<T> task) {
        return orm().runInSession(
                session -> txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> task.get()));
    }

    protected <T> T runInNewSession(Function<IOrmSession, T> task) {
        return orm().runInNewSession(task);
    }

    protected <T> T runLocal(final Function<IOrmSession, T> task) {
        return orm().runInNewSession(
                session -> txn().runInTransaction(null, TransactionPropagation.REQUIRES_NEW, txn -> task.apply(session)));
    }
}