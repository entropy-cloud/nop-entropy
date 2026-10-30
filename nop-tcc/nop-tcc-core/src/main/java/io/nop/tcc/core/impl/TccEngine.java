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
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.service.LifeCycleSupport;
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
import io.nop.tcc.core.TccEngineConfigs;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.nop.api.core.context.ContextProvider.completeAsyncOnContext;
import static io.nop.api.core.context.ContextProvider.thenOnContext;
import static io.nop.tcc.core.TccCoreErrors.ARG_CONFIG_KEY;
import static io.nop.tcc.core.TccCoreErrors.ARG_CONFIG_VALUE;
import static io.nop.tcc.core.TccCoreErrors.ARG_SERVICE_METHOD;
import static io.nop.tcc.core.TccCoreErrors.ARG_SERVICE_NAME;
import static io.nop.tcc.core.TccCoreErrors.ARG_TCC_STATUS;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_GROUP;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_ID;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_LEDGER_CONFIG_INVALID;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_MISSING_TRANSACTION_RECORD;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_TRANSACTION_ALREADY_FINISHED;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH;

public class TccEngine extends LifeCycleSupport implements ITccEngine {
    private static final Logger LOG = LoggerFactory.getLogger(TccEngine.class);

    private ITccRecordStore repository;
    private IRpcServiceInvoker serviceInvoker;
    private ITccExceptionChecker exceptionChecker;
    private IApiResponseNormalizer apiResponseNormalizer = DefaultApiResponseNormalizer.INSTANCE;

    // 清理周期链（plan 2515 裁定⑧）状态
    private IScheduledExecutor executor;
    private Future<?> ledgerCleanupFuture;
    private final AtomicLong ledgerCleanupFailureCount = new AtomicLong();
    // 测试/装配侧参数覆盖：null 时回落 TccEngineConfigs 配置键
    private Integer ledgerRetentionDays;
    private Duration ledgerCleanupInterval;
    private Integer ledgerDeleteBatchSize;
    private Integer maxRetryTimes;

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

    public void setLedgerRetentionDays(Integer ledgerRetentionDays) {
        this.ledgerRetentionDays = ledgerRetentionDays;
    }

    public void setLedgerCleanupInterval(Duration ledgerCleanupInterval) {
        this.ledgerCleanupInterval = ledgerCleanupInterval;
    }

    public void setLedgerDeleteBatchSize(Integer ledgerDeleteBatchSize) {
        this.ledgerDeleteBatchSize = ledgerDeleteBatchSize;
    }

    public void setMaxRetryTimes(Integer maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    protected IScheduledExecutor getExecutor() {
        if (executor == null) {
            executor = GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker());
        }
        return executor;
    }

    public void setExecutor(IScheduledExecutor executor) {
        this.executor = executor;
    }

    @Override
    protected void doStart() {
        // plan 2515 裁定⑧（C7-005）：清理周期链生产接线（生产可达生命周期钩子，防
        // 「清理 API 零生产装配」空壳形态）。retention-days=0 为显式禁用。
        startLedgerCleanup();
    }

    @Override
    protected void doStop() {
        if (ledgerCleanupFuture != null) {
            ledgerCleanupFuture.cancel(false);
            ledgerCleanupFuture = null;
            LOG.info("nop.tcc.ledger.cleanup-stopped");
        }
    }

    /**
     * plan 2515 裁定⑧：账本保留期清理周期链。载体与 R2-11 扫描链同构（绝缘载体
     * scheduleWithFixedDelay + IPeriodicTaskListener）：清理体任何 Throwable 由设施捕获观测
     * 并重臂，失败 ERROR 显式可见（fail-fast 包络：不静默吞掉、链不中断）；错配置（负保留期/
     * 非正批删上限/周期低于 60s 下限）启动期 fail-fast。键族/缺省值以 R01 族级 SSOT
     * ledger-retention-policy §3.1/§3.2 为唯一事实源（nop.tcc.ledger.* 投影，零参数发明）。
     */
    protected void startLedgerCleanup() {
        int retentionDays = resolveLedgerRetentionDays();
        long intervalMillis = resolveLedgerCleanupInterval().toMillis();
        int batchSize = resolveLedgerDeleteBatchSize();

        if (retentionDays < 0) {
            throw new NopException(ERR_TCC_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.tcc.ledger.retention-days")
                    .param(ARG_CONFIG_VALUE, retentionDays);
        }
        if (batchSize <= 0) {
            throw new NopException(ERR_TCC_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.tcc.ledger.delete-batch-size")
                    .param(ARG_CONFIG_VALUE, batchSize);
        }
        if (intervalMillis < TccEngineConfigs.TCC_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS) {
            throw new NopException(ERR_TCC_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.tcc.ledger.cleanup-interval")
                    .param(ARG_CONFIG_VALUE, intervalMillis);
        }
        if (retentionDays == 0) {
            LOG.info("nop.tcc.ledger.cleanup-disabled");
            return;
        }

        long retentionMillis = retentionDays * 24L * 3600_000L;
        ledgerCleanupFuture = getExecutor().scheduleWithFixedDelay(this::runLedgerCleanup,
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS,
                "nop-tcc-ledger-cleanup", new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        ledgerCleanupFailureCount.incrementAndGet();
                        LOG.error("nop.tcc.ledger.cleanup-fail:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        ledgerCleanupFailureCount.incrementAndGet();
                        LOG.error("nop.tcc.ledger.cleanup-chain-rejected: cleanup chain terminated"
                                + " by executor rejection, will not resurrect", e);
                    }
                });
        LOG.info("nop.tcc.ledger.cleanup-started:retentionDays={},intervalMillis={},batchSize={}",
                retentionDays, intervalMillis, batchSize);
    }

    /**
     * 单轮清理：①耗尽行前置终态化（KILLED，入既有 finished 谓词，C7-006 接缝：终态字段=
     * 复用 status 列，观测/干预工具本体归 R4-10）；②removeCompletedRecords 有界批删。
     * 清理体失败直接抛出（由绝缘 listener 观测），不在本方法内吞异常。
     */
    protected void runLedgerCleanup() {
        int killed = repository.killExhaustedRecords(resolveMaxRetryTimes(), resolveLedgerDeleteBatchSize());
        long retentionMillis = resolveLedgerRetentionDays() * 24L * 3600_000L;
        int deleted = repository.removeCompletedRecords(retentionMillis, true, resolveLedgerDeleteBatchSize());
        if (killed > 0 || deleted > 0) {
            LOG.info("nop.tcc.ledger.cleanup-done:killed={},deleted={}", killed, deleted);
        }
    }

    private int resolveLedgerRetentionDays() {
        return ledgerRetentionDays != null ? ledgerRetentionDays
                : TccEngineConfigs.CFG_TCC_LEDGER_RETENTION_DAYS.get();
    }

    private Duration resolveLedgerCleanupInterval() {
        return ledgerCleanupInterval != null ? ledgerCleanupInterval
                : TccEngineConfigs.CFG_TCC_LEDGER_CLEANUP_INTERVAL.get();
    }

    private int resolveLedgerDeleteBatchSize() {
        return ledgerDeleteBatchSize != null ? ledgerDeleteBatchSize
                : TccEngineConfigs.CFG_TCC_LEDGER_DELETE_BATCH_SIZE.get();
    }

    private int resolveMaxRetryTimes() {
        return maxRetryTimes != null ? maxRetryTimes
                : TccEngineConfigs.CFG_TCC_DEFAULT_MAX_RETRY_TIMES.get();
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

                CompletionStage<Void> future = checkExpiredAsync(record);
                try {
                    // 单条记录的补偿等待加上限，取expireGap作为超时时间：
                    // 单个补偿挂死时跳过该记录继续处理下一条，不再永久阻塞整个调度循环
                    future.toCompletableFuture().get(expireGap, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // plan 2515 裁定①（幽灵双驱动源封堵）：超时后必须取消在飞补偿链，
                    // 否则挂死的补偿链苏醒后与下一轮恢复扫描双驱动（C3-008 弃链追踪本体归 R4-10）
                    future.toCompletableFuture().cancel(true);
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