/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.IDialectProvider;

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nop.dao.DaoErrors.ARG_QUERY_SPACE;
import static io.nop.dao.DaoErrors.ERR_TXN_NOT_IN_TRANSACTION;

public interface ITransactionTemplate extends IDialectProvider {

    boolean isQuerySpaceDefined(String querySpace);

    IDialect getDialectForQuerySpace(String querySpace);

    /**
     * 返回指定分支事务组对应的主事务组
     *
     * @param txnGroup 一般一个txnGroup对应一个数据库，而mainTxnGroup则对应多个数据库事务所构成的联合事务
     */
    String getMainTxnGroup(String txnGroup);

    default boolean isTransactionOpened(String txnGroup) {
        ITransaction txn = getRegisteredTransaction(txnGroup);
        return txn != null && txn.isTransactionOpened();
    }

    /**
     * 如果txnGroup对应的txn已经存在，则直接返回。否则在txnGroup所对应的事务组中查找是否已经有打开的txn。
     *
     * @return 返回ITransaction对象不意味着已经开启事务。调用open方法之后才会开启事务，此时isInTransaction返回true
     */
    ITransaction getRegisteredTransaction(String txnGroup);

    <T> T runWithoutTransaction(String txnGroup, Supplier<T> task);

    <T> T runInTransaction(String txnGroup, TransactionPropagation propagation, Function<ITransaction, T> task);

    default <T> T runInTransaction(Function<ITransaction, T> task) {
        return runInTransaction(null, TransactionPropagation.REQUIRED, task);
    }

    <T> CompletionStage<T> runInTransactionAsync(String txnGroup, TransactionPropagation propagation,
                                                 Function<ITransaction, CompletionStage<T>> task);

    default void addTransactionListener(String txnGroup, ITransactionListener listener) {
        ITransaction txn = this.getRegisteredTransaction(txnGroup);
        if (txn != null) {
            txn.addListener(listener);
        } else {
            throw new NopException(ERR_TXN_NOT_IN_TRANSACTION).param(ARG_QUERY_SPACE, txnGroup);
        }
    }

    default void removeTransactionListener(String txnGroup, ITransactionListener listener) {
        ITransaction txn = this.getRegisteredTransaction(txnGroup);
        if (txn != null) {
            txn.removeListener(listener);
        }
    }

    default void beforeCommit(String txnGroup, Runnable action) {
        addTransactionListener(txnGroup, new ITransactionListener() {
            @Override
            public void onBeforeCommit(ITransaction txn) {
                action.run();
            }
        });
    }

    default void afterCommit(String txnGroup, Runnable action) {
        addTransactionListener(txnGroup, new ITransactionListener() {
            @Override
            public void onAfterCommit(ITransaction txn) {
                action.run();
            }
        });
    }

    default void afterCompletion(String txnGroup, BiConsumer<ITransactionListener.CompleteStatus, Throwable> action) {
        addTransactionListener(txnGroup, new ITransactionListener() {
            @Override
            public void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
                action.accept(status, exception);
            }
        });
    }
}