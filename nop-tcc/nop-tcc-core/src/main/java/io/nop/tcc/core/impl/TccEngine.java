/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.IApiResponseNormalizer;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.api.DefaultApiResponseNormalizer;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.ITccEngine;
import io.nop.tcc.api.ITccExceptionChecker;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccRecordStore;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static io.nop.api.core.context.ContextProvider.completeAsyncOnContext;
import static io.nop.api.core.context.ContextProvider.thenOnContext;
import static io.nop.tcc.core.TccCoreErrors.ARG_SERVICE_METHOD;
import static io.nop.tcc.core.TccCoreErrors.ARG_SERVICE_NAME;
import static io.nop.tcc.core.TccCoreErrors.ARG_TCC_STATUS;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_GROUP;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_ID;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_MISSING_TRANSACTION_RECORD;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_TRANSACTION_ALREADY_FINISHED;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH;

public class TccEngine implements ITccEngine {
    private static final Logger LOG = LoggerFactory.getLogger(TccEngine.class);

    private ITccRecordStore repository;
    private IRpcServiceInvoker serviceInvoker;
    private ITccExceptionChecker exceptionChecker;
    private IApiResponseNormalizer apiResponseNormalizer = DefaultApiResponseNormalizer.INSTANCE;

    public IApiResponseNormalizer getApiResponseNormalizer() {
        return apiResponseNormalizer;
    }

    public void setApiResponseNormalizer(IApiResponseNormalizer apiResponseNormalizer) {
        this.apiResponseNormalizer = apiResponseNormalizer;
    }

    public void setExceptionChecker(ITccExceptionChecker exceptionChecker) {
        this.exceptionChecker = exceptionChecker;
    }

    @Inject
    public void setTccRecordStore(ITccRecordStore repository) {
        this.repository = repository;
    }

    @Inject
    public void setServiceInvoker(IRpcServiceInvoker serviceInvoker) {
        this.serviceInvoker = serviceInvoker;
    }

    public IRpcServiceInvoker getServiceInvoker() {
        return serviceInvoker;
    }

    public ITccRecordStore getTccRecordRepository() {
        return repository;
    }

    private String normalizeTxnGroup(String txnGroup) {
        return TccHelper.normalizeTxnGroup(txnGroup);
    }

    public boolean isSafeFailException(Throwable ex) {
        if (exceptionChecker == null)
            return false;
        return exceptionChecker.isSafeFailException(ex);
    }

    @Override
    public ITccTransaction newTransaction(String txnGroup) {
        txnGroup = normalizeTxnGroup(txnGroup);
        return new TccTransaction(true, repository.newTccRecord(txnGroup), this);
    }

    @Override
    public ITccBranchTransaction newBranchTransaction(ITccTransaction txn, TccBranchRequest request) {
        ITccBranchRecord branchRecord = repository.newBranchRecord(txn.getTccRecord(), request);
        return new TccBranchTransaction(branchRecord, this);
    }

    @Override
    public CompletionStage<ITccTransaction> loadTransactionAsync(String txnGroup, String txnId) {
        txnGroup = normalizeTxnGroup(txnGroup);
        return repository.getTccRecordAsync(txnGroup, txnId).thenApply(this::newTccTransaction);
    }

    private TccTransaction newTccTransaction(ITccRecord record) {
        if (record == null)
            return null;
        return new TccTransaction(false, record, this);
    }

    /**
     * 根事务节点提交时，会主动加载其他节点加入的事务分支，然后把它们都加入到自己的subTxns集合中一起提交
     */
    @Override
    public CompletionStage<List<ITccBranchTransaction>> loadBranchTransactionsAsync(ITccTransaction tccTxn) {
        return repository.getBranchRecordsAsync(tccTxn.getTccRecord()).thenApply(this::newBranchTransactions);
    }

    private List<ITccBranchTransaction> newBranchTransactions(List<ITccBranchRecord> branches) {
        List<ITccBranchTransaction> branchTransactions = new ArrayList<>(branches.size());
        for (ITccBranchRecord branchRecord : branches) {
            branchTransactions.add(new TccBranchTransaction(branchRecord, this));
        }
        return branchTransactions;
    }

    @Override
    public ITccTransaction getCurrentTransaction(String txnGroup) {
        txnGroup = normalizeTxnGroup(txnGroup);
        return TccTransactionRegistry.instance().get(txnGroup);
    }

    @Override
    public <T> CompletionStage<T> runInTransactionAsync(String txnGroup, String txnId,
                                                        Function<ITccTransaction, CompletionStage<T>> task) {
        if (StringHelper.isEmpty(txnId))
            return runInTransactionAsync(txnGroup, task);

        String normalizeTxnGroup = normalizeTxnGroup(txnGroup);

        return loadTransactionAsync(normalizeTxnGroup, txnId).thenCompose(txn -> {
            checkTransactionActive(txn, normalizeTxnGroup, txnId);
            return runTaskWithExitingTxnAsync(txn, task);
        });
    }

    @Override
    public <T> T runInTransaction(String txnGroup, String txnId,
                                  Function<ITccTransaction, T> task) {
        if (StringHelper.isEmpty(txnId))
            return runInTransaction(txnGroup, task);

        String normalizeTxnGroup = normalizeTxnGroup(txnGroup);

        ITccTransaction txn = loadTransaction(normalizeTxnGroup, txnId);
        checkTransactionActive(txn, normalizeTxnGroup, txnId);
        return runTaskWithExitingTxn(txn, task);
    }

    private void checkTransactionActive(ITccTransaction txn, String txnGroup, String txnId) {
        if (txn == null) {
            throw new NopException(ERR_TCC_MISSING_TRANSACTION_RECORD).param(ARG_TXN_GROUP, txnGroup).param(ARG_TXN_ID,
                    txnId);
        }

        // status列为null的脏数据按未完结处理，不NPE
        TccStatus status = txn.getTccStatus();
        if (status != null && status.isFinished()) {
            throw new NopException(ERR_TCC_TRANSACTION_ALREADY_FINISHED).param(ARG_TXN_GROUP, txn.getTxnGroup())
                    .param(ARG_TXN_ID, txnId);
        }
    }

    private <T> CompletionStage<T> runTaskWithExitingTxnAsync(ITccTransaction txn,
                                                              Function<ITccTransaction, CompletionStage<T>> task) {
        TccTransactionRegistry registry = TccTransactionRegistry.instance();
        String txnGroup = txn.getTxnGroup();
        ITccTransaction old = registry.put(txnGroup, txn);

        CompletionStage<T> future;
        try {
            future = task.apply(txn);
        } catch (Throwable e) {
            // task同步抛异常/Error时whenComplete不会挂接，必须显式恢复registry。
            // 参与已有事务路径Error不在此执行endAsync补偿（事务不属于本调用方，与同步版runTaskWithExitingTxn一致），
            // Error原样重抛（不包装成NopException）
            registry.put(txnGroup, old);
            if (e instanceof Error)
                throw (Error) e;
            throw NopException.adapt(e);
        }
        CompletionStage<T> f = future;
        return thenOnContext(f).whenComplete((ret, err) -> {
            registry.put(txnGroup, old);
        });
    }

    private <T> T runTaskWithExitingTxn(ITccTransaction txn, Function<ITccTransaction, T> task) {
        TccTransactionRegistry registry = TccTransactionRegistry.instance();
        String txnGroup = txn.getTxnGroup();
        ITccTransaction old = registry.put(txnGroup, txn);

        try {
            return task.apply(txn);
        } finally {
            registry.put(txnGroup, old);
        }
    }

    @Override
    public <T> CompletionStage<T> runInTransactionAsync(String txnGroup,
                                                        Function<ITccTransaction, CompletionStage<T>> task) {
        txnGroup = normalizeTxnGroup(txnGroup);

        TccTransactionRegistry registry = TccTransactionRegistry.instance();
        ITccTransaction txn = registry.get(txnGroup);
        if (txn != null) {
            return task.apply(txn);
        }

        txn = newTransaction(txnGroup);

        return runTaskWithNewTxnAsync(registry, txn, task);
    }

    private <T> CompletionStage<T> runTaskWithNewTxnAsync(TccTransactionRegistry registry,
                                                          ITccTransaction txn, Function<ITccTransaction, CompletionStage<T>> task) {
        String txnGroup = txn.getTxnGroup();
        ITccTransaction old = registry.put(txnGroup, txn);

        return thenOnContext(txn.beginAsync()).thenCompose(v -> {
            CompletionStage<T> taskFuture;
            try {
                taskFuture = task.apply(txn);
            } catch (Throwable e) {
                // task同步抛异常/Error时必须显式执行endAsync补偿，否则record残留TRYING
                // 只能等超时扫描兜底（对比同步版本runTaskWithNewTxn的catch）。Error补偿后原样传播
                return txn.endAsync(false, null, e)
                        .thenCompose(v2 -> CompletableFuture.<T>failedFuture(e));
            }
            return completeAsyncOnContext(taskFuture,
                    (ret, err) -> txn.endAsync(false, apiResponseNormalizer.toApiResponse(ret), err).thenApply(v2 -> ret));
        }).whenComplete((ret, err) -> {
            registry.put(txnGroup, old);
        });
    }

    @Override
    public <T> T runInTransaction(String txnGroup, Function<ITccTransaction, T> task) {
        txnGroup = normalizeTxnGroup(txnGroup);

        TccTransactionRegistry registry = TccTransactionRegistry.instance();
        ITccTransaction txn = registry.get(txnGroup);
        if (txn != null) {
            return task.apply(txn);
        }

        txn = newTransaction(txnGroup);

        return runTaskWithNewTxn(registry, txn, task);
    }

    private <T> T runTaskWithNewTxn(TccTransactionRegistry registry, ITccTransaction txn,
                                    Function<ITccTransaction, T> task) {
        String txnGroup = txn.getTxnGroup();
        ITccTransaction old = registry.put(txnGroup, txn);

        try {
            txn.begin();
            T ret = task.apply(txn);
            txn.end(false, apiResponseNormalizer.toApiResponse(ret), null);
            return ret;
        } catch (Throwable e) {
            txn.end(false, null, e);
            throw NopException.adapt(e);
        } finally {
            registry.put(txnGroup, old);
        }
    }

    @Override
    public <T> CompletionStage<T> runBranchTransactionAsync(ITccTransaction txn,
                                                            TccBranchRequest branchRequest, Function<ITccBranchTransaction, CompletionStage<T>> task) {
        // 如果事务已经结束，则不能执行分支
        if (txn.getTccStatus() != TccStatus.TRYING)
            return FutureHelper.reject(new NopException(ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH)
                    .param(ARG_TXN_ID, txn.getTxnId()).param(ARG_TXN_GROUP, txn.getTxnGroup())
                    .param(ARG_TCC_STATUS, txn.getTccStatus()).param(ARG_SERVICE_NAME, branchRequest.getServiceName())
                    .param(ARG_SERVICE_METHOD, branchRequest.getServiceMethod()));

        ITccBranchRecord branchRecord = getTccRecordRepository().newBranchRecord(txn.getTccRecord(), branchRequest);

        TccBranchTransaction txnBranch = new TccBranchTransaction(branchRecord, this);

        return TccRunner.runBranchTryAsync(txnBranch, apiResponseNormalizer, task);
    }

    @Override
    public <T> T runBranchTransaction(ITccTransaction txn, TccBranchRequest branchRequest,
                                      Function<ITccBranchTransaction, T> task) {
        return FutureHelper.syncGet(runBranchTransactionAsync(txn, branchRequest, t -> FutureHelper.futureApply(task, t)));
    }

    @Override
    public void checkExpiredTransactions(long expireGap, int maxRetryCount, ICancelToken canceller) {
        int pageSize = 100;
        long checkInterval = expireGap;

        while (canceller == null || !canceller.isCancelled()) {
            List<? extends ITccRecord> records = repository.fetchExpiredRecords(pageSize, expireGap, checkInterval, maxRetryCount);
            if (records.isEmpty())
                break;

            for (ITccRecord record : records) {
                if (canceller != null && canceller.isCancelled())
                    return;

                try {
                    // 单条记录的补偿等待加上限，取expireGap作为超时时间：
                    // 单个补偿挂死时跳过该记录继续处理下一条，不再永久阻塞整个调度循环
                    checkExpiredAsync(record).toCompletableFuture().get(expireGap, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    LOG.error("TccEngine.checkExpiredTransactions compensation timeout for txnId={}",
                            record.getTxnId(), e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    LOG.error("TccEngine.checkExpiredTransactions failed for txnId={}", record.getTxnId(), e);
                }
            }
        }
    }

    private CompletionStage<Void> checkExpiredAsync(ITccRecord record) {
        TccTransaction txn = new TccTransaction(false, record, this);
        return txn.endAsync(true, null, null);
    }

    @Override
    public void cleanCompletedTransactions(long retentionTime) {
        // 契约：只删除已成功完结/取消的事务，未知状态保留等待人工处理。
        // 此前传 false 会按 beginTime 无状态过滤，物理删除未完结事务，补偿信息全部丢失
        repository.removeCompletedRecords(retentionTime, true);
    }
}