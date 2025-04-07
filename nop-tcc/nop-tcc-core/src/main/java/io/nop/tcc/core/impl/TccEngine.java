/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.IApiResponseNormalizer;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.api.IRpcServiceLocator;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.ITccEngine;
import io.nop.tcc.api.ITccExceptionChecker;
import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.ITccRecordRepository;
import io.nop.tcc.api.ITccTransaction;
import io.nop.tcc.api.TccBranchRequest;
import io.nop.tcc.api.TccStatus;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
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
    //private static final Logger LOG = LoggerFactory.getLogger(TccEngine.class);

    private ITccRecordRepository repository;
    private IRpcServiceLocator serviceLocator;
    private ITccExceptionChecker exceptionChecker;
    private IApiResponseNormalizer apiResponseNormalizer;

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
    public void setTccRecordRepository(ITccRecordRepository repository) {
        this.repository = repository;
    }

    @Inject
    public void setServiceLocator(IRpcServiceLocator apiServiceLocator) {
        this.serviceLocator = apiServiceLocator;
    }

    public IRpcServiceLocator getServiceLocator() {
        return serviceLocator;
    }

    public ITccRecordRepository getTccRecordRepository() {
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

        if (txn.getTccStatus().isFinished()) {
            throw new NopException(ERR_TCC_TRANSACTION_ALREADY_FINISHED).param(ARG_TXN_GROUP, txn.getTxnGroup())
                    .param(ARG_TXN_ID, txnId);
        }
    }

    private <T> CompletionStage<T> runTaskWithExitingTxnAsync(ITccTransaction txn,
                                                              Function<ITccTransaction, CompletionStage<T>> task) {
        TccTransactionRegistry registry = TccTransactionRegistry.instance();
        String txnGroup = txn.getTxnGroup();
        ITccTransaction old = registry.put(txnGroup, txn);

        return thenOnContext(task.apply(txn)).whenComplete((ret, err) -> {
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
            return completeAsyncOnContext(task.apply(txn),
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
        //repository.forEachExpiredRecord(this::checkExpiredAsync, expireGap, maxRetryCount, canceller);
    }

    private CompletionStage<Void> checkExpiredAsync(ITccRecord record) {
        TccTransaction txn = new TccTransaction(false, record, this);
        return txn.endAsync(true, null, null);
    }

    @Override
    public void cleanCompletedTransactions(long retentionTime) {
        repository.removeCompletedRecords(retentionTime, false);
    }
}