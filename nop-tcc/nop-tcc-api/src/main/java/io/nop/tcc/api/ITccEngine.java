/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.api;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * TCC事务处理的核心功能
 */
public interface ITccEngine {

    ITccTransaction newTransaction(String txnGroup);

    ITccTransaction getCurrentTransaction(String txnGroup);

    ITccBranchTransaction newBranchTransaction(ITccTransaction txn, TccBranchRequest request);

    CompletionStage<ITccTransaction> loadTransactionAsync(String txnGroup, String txnId);

    CompletionStage<List<ITccBranchTransaction>> loadBranchTransactionsAsync(ITccTransaction txn);

    <T> CompletionStage<T> runInTransactionAsync(String txnGroup,
                                                 Function<ITccTransaction, CompletionStage<T>> task);

    <T> CompletionStage<T> runInTransactionAsync(String txnGroup, String txnId,
                                                 Function<ITccTransaction, CompletionStage<T>> task);

    <T> CompletionStage<T> runBranchTransactionAsync(ITccTransaction txn,
                                                     TccBranchRequest branchRequest, Function<ITccBranchTransaction, CompletionStage<T>> task);

    /**
     * 检查
     *
     * @param expireGap     超时超过一定的时间间隔才会被处理
     * @param maxRetryCount 重试次数超过一定次数后会被忽略，不再处理
     * @param cancelToken   处理过程中可以通过canceller.isCancelled()来判断是否需要中断处理
     */
    void checkExpiredTransactions(long expireGap, int maxRetryCount, ICancelToken cancelToken);

    /**
     * 删除一段时间之前的，已经成功完结或者成功取消的事务。如果事务状态处于未知状态，则仍然会被保留，等待手工处理。
     *
     * @param retentionTime 单位为毫秒，从当前时间减去对应时间，在此时间之前的记录才会被清理。
     */
    void cleanCompletedTransactions(long retentionTime);

    // --------------同步版本----------------------
    default ITccTransaction loadTransaction(String txnGroup, String txnId) {
        return FutureHelper.syncGet(loadTransactionAsync(txnGroup, txnId));
    }

    default List<ITccBranchTransaction> loadBranchTransactions(ITccTransaction txn) {
        return FutureHelper.syncGet(loadBranchTransactionsAsync(txn));
    }

    <T> T runInTransaction(String txnGroup, Function<ITccTransaction, T> task);

    <T> T runInTransaction(String txnGroup, String txnId, Function<ITccTransaction, T> task);

    <T> T runBranchTransaction(ITccTransaction txn, TccBranchRequest branchRequest,
                               Function<ITccBranchTransaction, T> task);
}