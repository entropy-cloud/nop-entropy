/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn;

import io.nop.api.core.util.FutureHelper;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

public interface ITransaction extends AutoCloseable {
    /**
     * 一个事务分组可能包含多个数据库的子事务
     */
    String getTxnGroup();

    boolean isTransactionOpened();

    /**
     * 创建Transaction对象时并没有真正获取连接。调用open操作后才真正开始事务连接
     */
    void open();

    /**
     * 只要调用过open，必须调用close来释放内部资源。如果调用close之前没有调用过commit，则实际会被回滚
     */
    void close();

    void commit();

    void rollback(Throwable exception);

    default CompletionStage<Void> commitAsync() {
        return FutureHelper.futureRun(this::commit);
    }

    default CompletionStage<Void> rollbackAsync(Throwable ex) {
        return FutureHelper.futureRun(() -> this.rollback(ex));
    }

    /**
     * 标记只能回滚，不能提交
     */
    void markRollbackOnly(Throwable exception);

    /**
     * 是否已经标记为只能回滚，不允许提交的状态
     */
    boolean isRollbackOnly();

    void addListener(ITransactionListener listener);

    void removeListener(ITransactionListener listener);

    /**
     * 多个数据库可能构成一个事务组。开启事务组后，组内的事务要一起提交 主事务提交之前会提交所有子事务，子事务失败会导致整体事务失败
     */
    void addSubTransaction(ITransaction subTxn);

    boolean removeSubTransaction(ITransaction subTxn);

    ITransaction getSubTransaction(String txnGroup);

    Collection<ITransaction> getSubTransactions();
}