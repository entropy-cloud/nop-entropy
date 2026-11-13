/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IPeriodicTaskListener;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.DaoErrors;
import io.nop.retry.api.IRetryEngine;
import io.nop.retry.api.IRetryTask;
import io.nop.retry.dao.entity.NopRetryAttempt;
import io.nop.retry.dao.entity.NopRetryDeadLetter;
import io.nop.retry.dao.entity.NopRetryPolicy;
import io.nop.retry.dao.entity.NopRetryRecord;
import io.nop.retry.engine.NopRetryConstants;
import io.nop.retry.engine.RetryEngineConfigs;
import io.nop.retry.engine.scanner.IRetryScanner;
import io.nop.retry.engine.store.IRetryRecordStore;
import io.nop.api.core.beans.ErrorBean;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.retry.engine.NopRetryErrors.*;

public class RetryEngineImpl extends LifeCycleSupport implements IRetryEngine {

    static final Logger LOG = LoggerFactory.getLogger(RetryEngineImpl.class);

    private IRpcServiceInvoker rpcServiceInvoker;
    private IRetryRecordStore recordStore;
    private IRetryScanner retryScanner;
    private IScheduledExecutor executor;

    // 1.6-C7-001（plan 2502）：三账本表保留期清理参数。null = 未显式注入，回退
    // RetryEngineConfigs 声明的键值（设计 ledger-retention-policy §3.2 nop-retry 行；
    // 参数修订只走该设计文档，模块内不发明缺省值）。
    private Integer ledgerRetentionDays;
    private Duration ledgerCleanupInterval;
    private Integer ledgerDeleteBatchSize;

    // 周期清理链句柄与失败计数（fail-fast 包络：失败 ERROR 可见 + 计数可观测，链由绝缘层重臂）
    private Future<?> ledgerCleanupFuture;
    private final AtomicLong ledgerCleanupFailureCount = new AtomicLong();

    // 1.6-C3-003（plan 2515 裁定④）：attempt 级超时上界（< 租约，装配期 fail-fast 校验）
    private Long attemptTimeoutMs;

    // 1.6-C3-005（plan 2515 裁定⑤）：per-recordId 进程内代际号——OVERWRITE 重建递增，
    // 旧链回调/续跑/回写前校验代际（执行令牌「record 版本 + 代际」二元组的进程内半边）
    private final ConcurrentHashMap<String, AtomicLong> recordGenerations = new ConcurrentHashMap<>();

    // 1.6-C3-006/C8-012（plan 2515 裁定⑥）：两层簿记失败计数（ERROR 通道 + 计数可观测）
    private final AtomicLong attemptBookkeepFailureCount = new AtomicLong();
    private final AtomicLong recordBookkeepFailureCount = new AtomicLong();

    /** 1.6-C7-008（plan 2543 W-09）：回调 idempotentId 逐环追加的后缀（回调链深度载体）。 */
    static final String CALLBACK_ID_SUFFIX = "_callback";

    // 1.6-C7-008（plan 2543 W-09）：回调深度上限。null = 未显式注入，回退 RetryEngineConfigs 键值。
    private Integer maxCallbackDepth;
    private final AtomicLong callbackDepthExceededCount = new AtomicLong();

    // 1.6-C7-014（plan 2543 W-11）：扫描批内单条记录派发失败计数（同步抛被隔离，非静默）
    private final AtomicLong scannerDispatchFailureCount = new AtomicLong();

    @Inject
    public void setRpcServiceInvoker(IRpcServiceInvoker rpcServiceInvoker) {
        this.rpcServiceInvoker = rpcServiceInvoker;
    }

    @Inject
    public void setRecordStore(IRetryRecordStore recordStore) {
        this.recordStore = recordStore;
    }

    @Inject
    public void setRetryScanner(IRetryScanner retryScanner) {
        this.retryScanner = retryScanner;
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

    public long getLedgerCleanupFailureCount() {
        return ledgerCleanupFailureCount.get();
    }

    public void setAttemptTimeoutMs(Long attemptTimeoutMs) {
        this.attemptTimeoutMs = attemptTimeoutMs;
    }

    public long getAttemptBookkeepFailureCount() {
        return attemptBookkeepFailureCount.get();
    }

    public long getRecordBookkeepFailureCount() {
        return recordBookkeepFailureCount.get();
    }

    /** 1.6-C7-008（plan 2543 W-09）：回调深度超限被拒孵化的次数。 */
    public long getCallbackDepthExceededCount() {
        return callbackDepthExceededCount.get();
    }

    public void setMaxCallbackDepth(Integer maxCallbackDepth) {
        this.maxCallbackDepth = maxCallbackDepth;
    }

    /** 1.6-C7-014（plan 2543 W-11）：扫描批内单条记录派发失败（同步抛）的次数。 */
    public long getScannerDispatchFailureCount() {
        return scannerDispatchFailureCount.get();
    }

    /** 裁定⑤：读取 record 当前进程内代际号（无记录返回 0）。 */
    long currentGeneration(String recordId) {
        AtomicLong gen = recordGenerations.get(recordId);
        return gen != null ? gen.get() : 0L;
    }

    /** 裁定⑤：OVERWRITE 重建时递增 record 代际号，旧链持有的代际随即失配。 */
    private void bumpGeneration(String recordId) {
        recordGenerations.computeIfAbsent(recordId, k -> new AtomicLong()).incrementAndGet();
    }

    /** 裁定④：attempt 超时上界。null = 未显式注入，回退 RetryEngineConfigs 声明的键值。 */
    private long resolveAttemptTimeoutMs() {
        return attemptTimeoutMs != null ? attemptTimeoutMs
                : RetryEngineConfigs.CFG_RETRY_ATTEMPT_TIMEOUT_MS.get();
    }

    @Override
    public IRetryTask newRetryTask(String serviceName, String serviceMethod) {
        return new RetryTaskImpl(this, serviceName, serviceMethod);
    }

    @Override
    public CompletionStage<ApiResponse<?>> retryFromDeadLetter(String deadLetterId, ICancelToken cancelToken) {
        NopRetryDeadLetter deadLetter = recordStore.loadDeadLetter(deadLetterId);

        if (deadLetter == null) {
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            future.completeExceptionally(
                    new NopException(ERR_RETRY_DEAD_LETTER_NOT_FOUND)
                            .param(ARG_DEAD_LETTER_ID, deadLetterId));
            return future;
        }

        String serviceName = deadLetter.getServiceName();
        String serviceMethod = deadLetter.getServiceMethod();
        String requestPayload = deadLetter.getRequestPayload();

        if (StringHelper.isEmpty(serviceName) || StringHelper.isEmpty(serviceMethod)) {
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            future.completeExceptionally(
                    new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR)
                            .param(ARG_DEAD_LETTER_ID, deadLetterId));
            return future;
        }

        if (StringHelper.isEmpty(requestPayload)) {
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            future.completeExceptionally(
                    new NopException(ERR_RETRY_DEAD_LETTER_INVALID_REQUEST)
                            .param(ARG_DEAD_LETTER_ID, deadLetterId));
            return future;
        }

        ApiRequest<?> request = JsonTool.parseBeanFromText(requestPayload, ApiRequest.class);
        return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null);
    }

    @Override
    public void pause(String recordId) {
        NopRetryRecord record = recordStore.loadRecord(recordId);

        if (record == null) {
            throw new NopException(ERR_RETRY_RECORD_NOT_FOUND)
                    .param(ARG_RECORD_ID, recordId);
        }

        int status = record.getStatus() != null ? record.getStatus() : 0;

        if (status == NopRetryConstants.RETRY_RECORD_STATUS_SUSPENDED) {
            throw new NopException(ERR_RETRY_RECORD_ALREADY_SUSPENDED)
                    .param(ARG_RECORD_ID, recordId);
        }

        if (status == NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED) {
            throw new NopException(ERR_RETRY_RECORD_ALREADY_COMPLETED)
                    .param(ARG_RECORD_ID, recordId);
        }

        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_SUSPENDED);
        recordStore.updateRecord(record);
    }

    @Override
    public void resume(String recordId) {
        NopRetryRecord record = recordStore.loadRecord(recordId);

        if (record == null) {
            throw new NopException(ERR_RETRY_RECORD_NOT_FOUND)
                    .param(ARG_RECORD_ID, recordId);
        }

        int status = record.getStatus() != null ? record.getStatus() : 0;

        if (status != NopRetryConstants.RETRY_RECORD_STATUS_SUSPENDED) {
            throw new NopException(ERR_RETRY_RECORD_NOT_SUSPENDED)
                    .param(ARG_RECORD_ID, recordId);
        }

        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_PENDING);
        record.setNextTriggerTime(new Timestamp(recordStore.getCurrentTime()));
        recordStore.updateRecord(record);
    }

    /**
     * policyId 是可选配置（newRetryTask 不设置时为null），无策略时使用内置默认策略，
     * 避免落库后在 executeWithRetry 中NPE并留下被扫描器反复接管的孤儿记录
     */
    private NopRetryPolicy loadPolicyOrDefault(String policyId) {
        NopRetryPolicy policy = recordStore.loadPolicy(policyId);
        if (policy != null)
            return policy;
        return new NopRetryPolicy();
    }

    CompletionStage<ApiResponse<?>> executeTask(IRetryTask task, ApiRequest<?> request, ICancelToken cancelToken) {
        NopRetryPolicy policy = loadPolicyOrDefault(task.getPolicyId());

        NopRetryRecord existingRecord = recordStore.findRecordByIdempotentId(
                task.getNamespaceId(), task.getGroupId(), task.getIdempotentId());
        if (existingRecord != null) {
            return handleExistingRecord(existingRecord, policy, request, cancelToken, task);
        }

        NopRetryRecord record = recordStore.newRecord(task, request);
        try {
            recordStore.saveRecord(record);
        } catch (Exception e) {
            NopRetryRecord raceWinner = resolveInsertRaceWinner(task, e);
            if (raceWinner == null)
                throw e;
            // 并发首提的TOCTOU窗口（plan 2282 G7-14-01）：唯一键冲突兜底，
            // 与先查后插同样按记录状态分流，不再以裸数据库约束冲突暴露
            return handleExistingRecord(raceWinner, policy, request, cancelToken, task);
        }
        return executeWithRetry(record, policy, request, cancelToken, task);
    }

    /**
     * 幂等键生命周期语义（plan 2282 G7-14-01 修复裁定，二选一中选择"复用终态结果"）：
     * <ul>
     * <li>COMPLETED 记录：上次同键提交已成功，重提交按幂等重放处理——直接返回成功，
     *     不重新执行业务副作用，也不咨询OVERWRITE策略（覆盖已成功的记录重新执行副作用
     *     超出重复提交的授权范围；需要重新驱动时走死信/暂停恢复等显式运维入口）。</li>
     * <li>其余状态（PENDING/RETRYING/SUSPENDED）：按blockStrategy处理。SUSPENDED是运维
     *     主动暂停，重复提交不允许偷偷恢复执行——DISCARD保持暂停，OVERWRITE显式重建。</li>
     * </ul>
     * 不选"显式删除重建"的原因：删除COMPLETED记录会让下一次重放重新执行业务副作用，
     * 违背幂等键契约；且死信路径已提供显式的重新驱动入口。
     */
    private CompletionStage<ApiResponse<?>> handleExistingRecord(
            NopRetryRecord existingRecord,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        int status = existingRecord.getStatus() != null ? existingRecord.getStatus() : 0;
        if (status == NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED) {
            LOG.info("nop.retry.idempotent-replay-completed:recordId={},idempotentId={}",
                    existingRecord.getSid(), existingRecord.getIdempotentId());
            return FutureHelper.success(ApiResponse.success(null));
        }
        return handleBlockStrategy(existingRecord, policy, request, cancelToken, task);
    }

    /**
     * 插入唯一键冲突时判定是否为并发首提竞争：是则返回竞胜者的记录（已提交可见），否则返回null。
     */
    private NopRetryRecord resolveInsertRaceWinner(IRetryTask task, Exception e) {
        if (!isDuplicateKeyError(e))
            return null;

        NopRetryRecord winner = recordStore.findRecordByIdempotentId(
                task.getNamespaceId(), task.getGroupId(), task.getIdempotentId());
        if (winner == null) {
            // 冲突行在我们读取前被删除（如并发OVERWRITE/死信迁移）：保留原始错误暴露给调用方
            LOG.warn("nop.retry.insert-conflict-without-winner:idempotentId={}", task.getIdempotentId(), e);
            return null;
        }
        LOG.info("nop.retry.concurrent-submit-race-lost:recordId={},idempotentId={}",
                winner.getSid(), task.getIdempotentId());
        return winner;
    }

    private static boolean isDuplicateKeyError(Throwable e) {
        return e instanceof NopException
                && DaoErrors.ERR_SQL_DUPLICATE_KEY.getErrorCode().equals(((NopException) e).getErrorCode());
    }

    private CompletionStage<ApiResponse<?>> handleBlockStrategy(
            NopRetryRecord existingRecord,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        switch (policy.getBlockStrategyOrDefault()) {
            case NopRetryConstants.BLOCK_STRATEGY_DISCARD:
                LOG.info("nop.retry.block-strategy-discard:recordId={}", existingRecord.getSid());
                return FutureHelper.success(ApiResponse.success(null));

            case NopRetryConstants.BLOCK_STRATEGY_OVERWRITE:
                LOG.info("nop.retry.block-strategy-overwrite:recordId={}", existingRecord.getSid());
                // 1.6-C3-005（plan 2515 裁定⑤）：OVERWRITE 递增进程内代际号——旧链（在途回调、
                // 已调度续跑定时器）每次回调/续跑前校验代际，不一致即短路，幽灵任务双执行不可达
                bumpGeneration(existingRecord.getSid());
                NopRetryRecord newRecord = recordStore.newRecord(task, request);
                try {
                    // 1.6-C6-003（plan 2515 裁定⑤）：原子替换包络（delete+save 同事务），
                    // 替换失败整体回滚 → 旧记录存活
                    recordStore.replaceRecord(existingRecord, newRecord);
                } catch (Exception e) {
                    // 1.6-C1-002（plan 2543 W-01）：替换失败必须按「异常类型 × 重读行身份」分流，
                    // 不得一律对 existingRecord 造死信——2026 并发 OVERWRITE 输家的垂败面是
                    // 「旧行已被对端合法替换、我方 delete 0 行后撞 UK」，此时对 stale 行造死信
                    // （含删除动作）即误删赢家刚写入的键位语义并暴露裸约束冲突给调用方。
                    return handleOverwriteReplaceFailure(existingRecord, e);
                }
                return executeWithRetry(newRecord, policy, request, cancelToken, task);

            default:
                LOG.info("nop.retry.block-strategy-discard:recordId={}", existingRecord.getSid());
                return FutureHelper.success(ApiResponse.success(null));
        }
    }

    /**
     * 1.6-C1-002（plan 2543 W-01）：OVERWRITE 替换失败的四分支分流（设计 §3.1.1）。
     * <p>
     * 判据 = 异常类型（UK 冲突与否）× 重读行的 sid 身份。UK 冲突是「冲突行必已提交可见」的强信号，
     * 故只有它能区分「键已被对端合法替换」（不造死信）与「旧行真实存活」（造死信）。
     * 非 UK 异常（瞬时 DB 故障/锁超时/序列化失败）下无法判定是否存在并发赢家——死信含删除动作，
     * 禁止以猜测驱动破坏性操作，退为纯失败传播（调用方可重试）。
     */
    private CompletionStage<ApiResponse<?>> handleOverwriteReplaceFailure(NopRetryRecord existingRecord,
                                                                          Exception replaceError) {
        String recordId = existingRecord.getSid();
        LOG.error("nop.retry.overwrite-replace-failed:recordId={}", recordId, replaceError);

        if (!isDuplicateKeyError(replaceError)) {
            // 非 UK 异常：无法判定并发赢家 → 不造死信、不删除旧行，纯失败传播
            LOG.warn("nop.retry.overwrite-replace-failed-non-uk:recordId={}", recordId);
            return CompletableFuture.failedStage(new NopException(ERR_RETRY_RECORD_OVERWRITTEN, replaceError)
                    .param(ARG_RECORD_ID, recordId));
        }

        // UK 冲突：重读该幂等键当前占位行，按 sid 身份判定输赢
        NopRetryRecord current = recordStore.findRecordByIdempotentId(
                existingRecord.getNamespaceId(), existingRecord.getGroupId(), existingRecord.getIdempotentId());

        if (current == null) {
            // 行在读取前被并发删除（如死信迁移）：键位已空，不属「已被替换」也不属「旧行存活」
            LOG.warn("nop.retry.overwrite-race-lost-no-row:recordId={},idempotentId={}",
                    recordId, existingRecord.getIdempotentId());
            return CompletableFuture.failedStage(new NopException(ERR_RETRY_OVERWRITE_RACE_LOST)
                    .param(ARG_RECORD_ID, recordId));
        }

        if (!Objects.equals(current.getSid(), existingRecord.getSid())) {
            // 并发 OVERWRITE 输家：键已被对端合法替换——OVERWRITE 的语义结果（键位上是一条新记录）
            // 已由赢家满足；不造死信、不暴露裸约束冲突，按幂等重放语义返回成功
            LOG.info("nop.retry.overwrite-loser-conceded:recordId={},winnerSid={},idempotentId={}",
                    recordId, current.getSid(), existingRecord.getIdempotentId());
            return FutureHelper.success(ApiResponse.success(null));
        }

        // 同一行仍在场：事务确实整体回滚、旧记录真实存活 → 死信快照 + 显式失败
        // （对齐「死信保留全量快照」可恢复性设计，半个成功任务蒸发不可达 C6-003/R10）
        moveToDeadLetter(existingRecord, "OVERWRITE_REPLACE_FAILED",
                "OVERWRITE atomic replacement failed (see nop.retry.overwrite-replace-failed log for cause)", null);
        return CompletableFuture.failedStage(new NopException(ERR_RETRY_RECORD_OVERWRITTEN, replaceError)
                .param(ARG_RECORD_ID, recordId));
    }

    CompletionStage<ApiResponse<?>> executeRetryFromScanner(NopRetryRecord record, ICancelToken cancelToken) {
        String serviceName = record.getServiceName();
        String serviceMethod = record.getServiceMethod();
        String requestPayload = record.getRequestPayload();

        // 无法执行的坏记录直接进死信。此前仅返回失败future不改记录状态/retryCount，
        // 记录每个租约周期被重扫一次，永不进死信
        if (StringHelper.isEmpty(serviceName) || StringHelper.isEmpty(serviceMethod)) {
            NopException ex = new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR)
                    .param(ARG_RECORD_ID, record.getSid());
            moveToDeadLetter(record, ex);
            return CompletableFuture.failedStage(ex);
        }

        if (StringHelper.isEmpty(requestPayload)) {
            NopException ex = new NopException(ERR_RETRY_DEAD_LETTER_INVALID_REQUEST)
                    .param(ARG_RECORD_ID, record.getSid());
            moveToDeadLetter(record, ex);
            return CompletableFuture.failedStage(ex);
        }

        // 1.6-C7-003（plan 2515 裁定⑦）：反序列化与策略装载包 try/catch——损坏 JSON /
        // 策略装载异常的记录首次失败即走 moveToDeadLetter 既有兜底（:305-319 同族漏网路径
        // 补齐）+ failedStage。原缺陷：裸 parseMap/loadPolicy 同步抛止于 scanner catch，
        // 记录状态/retryCount 零推进，每租约周期（144 条 ERROR/天）无限循环重拾（僵尸记录）
        NopRetryPolicy policy;
        ApiRequest<?> request;
        try {
            policy = loadPolicyOrDefault(record.getPolicyId());
            request = ApiRequest.build(JsonTool.parseMap(requestPayload));
        } catch (Throwable t) {
            LOG.error("nop.retry.scanner.payload-parse-failed:recordId={}", record.getSid(), t);
            moveToDeadLetter(record, t);
            return CompletableFuture.failedStage(t);
        }

        return executeWithRetry(record, policy, request, cancelToken, null);
    }

    private CompletionStage<ApiResponse<?>> executeWithRetry(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        try {
            // 1.6-C6-002（plan 2515 裁定⑦）：执行入口对 record 名下遗留 RUNNING attempt 对账
            // （置 STOPPED + endTime 落账）——覆盖 scanner 重拾与 OVERWRITE 新链。崩溃/挂死后
            // retryCount 未递增，重拾产出同号 attempt 的 UK 碰撞砖化链从根消除；幂等
            // （无 RUNNING 行零写）。对账失败（DB 故障）由外层 catch 统一 failedStage。
            recordStore.reconcileStaleRunningAttempts(record.getSid());

            long deadlineTimeoutMs = policy.getDeadlineTimeoutMsOrDefault();
            if (deadlineTimeoutMs > 0) {
                Timestamp createTime = record.getCreateTime();
                if (createTime != null) {
                    long elapsed = recordStore.getCurrentTime() - createTime.getTime();
                    if (elapsed > deadlineTimeoutMs) {
                        return handleDeadlineExceeded(record);
                    }
                }
            }

            if (isImmediateRetryPhase(record, policy)) {
                return executeImmediateRetry(record, policy, request, cancelToken, task);
            } else {
                return executeSingleAttempt(record, policy, request, cancelToken, task);
            }
        } catch (Throwable t) {
            // 1.6-C8-012（plan 2515 裁定⑥）T1 逃逸点闭合：doExecute 前置持久化等同步异常
            // 不再悬置返回 future——任何未预期 Throwable 使链以异常完成（每 future 必有终态，
            // 扫描器 allOf().join() 有界返回，业务级永久冻结不可再现）
            return CompletableFuture.failedStage(t);
        }
    }

    private boolean isImmediateRetryPhase(NopRetryRecord record, NopRetryPolicy policy) {
        int immediateRetryCount = policy.getImmediateRetryCountOrDefault();
        if (immediateRetryCount <= 0) {
            return false;
        }
        int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;
        return retryCount < immediateRetryCount;
    }

    /**
     * 即时重试链（plan 2515 裁定④⑥重构）：
     * <ul>
     * <li>裁定④：每轮 attempt 发起前 CAS 续租 nextTriggerTime（载体 tryUpdateWithVersionCheck）；
     *     续租 0 行 = 租约被扫描器接管/他链抢持 → 败方显式中止（ERR_RETRY_LEASE_LOST + WARN），
     *     不静默续跑、不重抢、不再发起 attempt。</li>
     * <li>裁定⑥ future 拓扑单一化：返回 future ≡ 回调挂接同一 promise（handle/thenCompose 链式
     *     传导），续排经 scheduleImmediateRetry 的挂接 future 回流到同一链；每条链 Throwable 兜底，
     *     任何路径必有终态（E-a/E-b/E-c 逃逸点逐一闭合）。</li>
     * <li>裁定⑥ 记账/业务分层：业务结论（成功/失败/重试判定）先于簿记确定并锁定；attempt 层与
     *     record 层簿记写各自独立 try/catch 隔离，簿记异常 ERROR+计数显式可见，不改判业务结论、
     *     不以簿记异常完成 stage。</li>
     * <li>裁定⑤ 代际校验：回调入口校验进程内代际号，被 OVERWRITE 重建抢先的旧链短路（INFO 留痕），
     *     不再回写、不再续排。</li>
     * </ul>
     */
    private CompletionStage<ApiResponse<?>> executeImmediateRetry(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        // 裁定④：轮间续租。0 行 = 租约被抢 → 败方显式中止（回写义务随链终止消灭）
        if (!recordStore.renewLease(record, NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS)) {
            NopException leaseLost = new NopException(ERR_RETRY_LEASE_LOST)
                    .param(ARG_RECORD_ID, record.getSid());
            LOG.warn("nop.retry.lease-lost-abort:recordId={}", record.getSid());
            return CompletableFuture.failedStage(leaseLost);
        }

        long generation = currentGeneration(record.getSid());
        try {
            return doExecute(record, request, cancelToken)
                    .handle((response, ex) ->
                            finishImmediateRetry(record, policy, request, cancelToken, task, generation,
                                    response, ex))
                    .thenCompose(stage -> stage);
        } catch (Throwable t) {
            // 裁定⑥：任何同步异常不悬置链——异常完成（终态完备）
            return CompletableFuture.failedStage(t);
        }
    }

    /**
     * 裁定⑥：即时重试链业务结算。业务结论在此先确定，簿记写全部经独立 try/catch 隔离。
     */
    private CompletionStage<ApiResponse<?>> finishImmediateRetry(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task,
            long generation,
            ApiResponse<?> response,
            Throwable ex) {

        // 裁定⑤：代际校验——被 OVERWRITE 重建抢先的旧链短路（INFO 留痕，非吞），
        // 不再回写（回写义务随链终止消灭）、不再续排
        if (generation != currentGeneration(record.getSid())) {
            LOG.info("nop.retry.stale-chain-aborted:recordId={},generation={}", record.getSid(), generation);
            return CompletableFuture.failedStage(new NopException(ERR_RETRY_RECORD_OVERWRITTEN)
                    .param(ARG_RECORD_ID, record.getSid()));
        }

        int immediateRetryCount = policy.getImmediateRetryCountOrDefault();
        long immediateRetryIntervalMs = policy.getImmediateRetryIntervalMsOrDefault();

        if (ex != null) {
            int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
            if (retryCount < immediateRetryCount) {
                bookkeepRecordRetryCount(record, retryCount);
                return scheduleImmediateRetry(record, policy, request, cancelToken, task,
                        immediateRetryIntervalMs, generation);
            }
            runRecordBookkeeping(record, "handleExecutionFailure", () ->
                    handleExecutionFailure(record, policy, ex, task));
            return CompletableFuture.failedStage(ex);
        }

        if (response != null && response.isOk()) {
            runRecordBookkeeping(record, "handleExecutionSuccess", () ->
                    handleExecutionSuccess(record, policy, task));
            return CompletableFuture.completedFuture(response);
        }

        NopException failureEx = response != null
                ? NopRebuildException.rebuild(response)
                : new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR);
        if (failureEx.isBizFatal()) {
            // bizFatal 不重试，直接移入死信（簿记失败不改判业务结论：仍以原 response 完成）
            runRecordBookkeeping(record, "moveToDeadLetter", () -> moveToDeadLetter(record, failureEx));
            return CompletableFuture.completedFuture(response);
        }

        int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
        if (retryCount < immediateRetryCount) {
            bookkeepRecordRetryCount(record, retryCount);
            return scheduleImmediateRetry(record, policy, request, cancelToken, task,
                    immediateRetryIntervalMs, generation);
        }
        runRecordBookkeeping(record, "handleExecutionFailure", () ->
                handleExecutionFailure(record, policy, failureEx, task));
        return CompletableFuture.completedFuture(response);
    }

    /**
     * 裁定⑥ record 层簿记隔离：retryCount 推进写失败 ERROR+计数显式可见，不改判业务结论、
     * 不中断续排。
     */
    private void bookkeepRecordRetryCount(NopRetryRecord record, int retryCount) {
        try {
            record.setRetryCount(retryCount);
            recordStore.updateRecord(record);
        } catch (Throwable t) {
            recordBookkeepFailureCount.incrementAndGet();
            LOG.error("nop.retry.record.bookkeep-fail:recordId={},op=updateRetryCount", record.getSid(), t);
        }
    }

    /**
     * 裁定⑥ record 层簿记隔离：终态结算（成功落账/失败落账/死信迁移）失败 ERROR+计数显式可见，
     * 不改判业务结论。
     */
    private void runRecordBookkeeping(NopRetryRecord record, String op, Runnable bookkeeping) {
        try {
            bookkeeping.run();
        } catch (Throwable t) {
            recordBookkeepFailureCount.incrementAndGet();
            LOG.error("nop.retry.record.bookkeep-fail:recordId={},op={}", record.getSid(), op, t);
        }
    }

    private CompletionStage<ApiResponse<?>> scheduleImmediateRetry(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task,
            long immediateRetryIntervalMs,
            long generation) {

        // Capture the executor at scheduling time to prevent stale callbacks from
        // leaking retries into a different test's executor (gh-xxx stale-callback race).
        IScheduledExecutor executor = getExecutor();
        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();

        executor.schedule(() -> {
            try {
                // 裁定⑤：定时器触发时先校验代际——被 OVERWRITE 重建抢先的旧链定时器短路，
                // 不再发起 attempt（幽灵任务双执行不可达）
                if (generation != currentGeneration(record.getSid())) {
                    LOG.info("nop.retry.stale-timer-aborted:recordId={},generation={}",
                            record.getSid(), generation);
                    future.completeExceptionally(new NopException(ERR_RETRY_RECORD_OVERWRITTEN)
                            .param(ARG_RECORD_ID, record.getSid()));
                    return null;
                }
                // 裁定⑥ E-a：delay-lambda 体任何同步异常显式传导至持有 future（completeExceptionally），
                // 不再悬置；续排经同一 promise 链式回流（返回 future ≡ 挂接 future）
                executeImmediateRetry(record, policy, request, cancelToken, task)
                        .whenComplete((resp, ex) -> {
                            if (ex != null) {
                                future.completeExceptionally(ex);
                            } else {
                                future.complete(resp);
                            }
                        });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return null;
        }, immediateRetryIntervalMs, TimeUnit.MILLISECONDS);

        return future;
    }

    private CompletionStage<ApiResponse<?>> executeSingleAttempt(
            NopRetryRecord record,
            NopRetryPolicy policy,
            ApiRequest<?> request,
            ICancelToken cancelToken,
            IRetryTask task) {

        // 裁定⑥：单次 attempt 链——返回 future ≡ doExecute 挂接链（拓扑单一化），
        // record 层簿记独立 try/catch 隔离，簿记异常不改判业务结论、不以簿记异常完成 stage
        return doExecute(record, request, cancelToken)
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        runRecordBookkeeping(record, "handleExecutionFailure", () ->
                                handleExecutionFailure(record, policy, ex, task));
                    } else if (response != null && response.isOk()) {
                        runRecordBookkeeping(record, "handleExecutionSuccess", () ->
                                handleExecutionSuccess(record, policy, task));
                    } else {
                        NopException failureEx = response != null
                                ? NopRebuildException.rebuild(response)
                                : new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR);
                        if (failureEx.isBizFatal()) {
                            // bizFatal 不重试，直接移入死信
                            runRecordBookkeeping(record, "moveToDeadLetter", () ->
                                    moveToDeadLetter(record, failureEx));
                        } else {
                            runRecordBookkeeping(record, "handleExecutionFailure", () ->
                                    handleExecutionFailure(record, policy, failureEx, task));
                        }
                    }
                });
    }

    private CompletionStage<ApiResponse<?>> doExecute(
            NopRetryRecord record,
            ApiRequest<?> request,
            ICancelToken cancelToken) {

        NopRetryAttempt attempt = recordStore.newAttempt(record);
        // 1.6-C8-012（plan 2515 裁定⑥）attempt 层簿记位点隔离：前置 RUNNING 落账失败
        // ERROR+计数显式可见，不阻断业务执行（原缺陷：同步 DB 异常悬置返回 future →
        // 扫描器 allOf().join() 永久冻结，T1 形态）
        try {
            attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_RUNNING);
            recordStore.saveAttempt(attempt);
        } catch (Throwable t) {
            attemptBookkeepFailureCount.incrementAndGet();
            LOG.error("nop.retry.attempt.bookkeep-fail:recordId={},op=saveRunningAttempt",
                    record.getSid(), t);
        }

        CompletionStage<ApiResponse<?>> invoke;
        try {
            invoke = rpcServiceInvoker.invokeAsync(
                    record.getServiceName(),
                    record.getServiceMethod(),
                    request,
                    cancelToken
            );
        } catch (Throwable t) {
            // 1.6-C7-003（plan 2515 裁定⑦）：invokeAsync 同步抛时 whenComplete 未挂接——
            // attempt 终态化落账（FAILED + endTime + 错误三元组，簿记失败 ERROR+计数隔离）
            // + 业务链 failedStage，僵尸 RUNNING attempt 不可达
            finalizeAttemptOnSyncThrow(record, attempt, t);
            return CompletableFuture.failedStage(t);
        }

        // 1.6-C3-003（plan 2515 裁定④）：attempt 级超时上界 < 租约——挂死 attempt 在租约到期前
        // 被取消落账（TimeoutException 经 whenComplete 以 FAILED 落账），租约到期接管读到的必是
        // 已终态化的 attempt，attempt 内双执行窗不可达
        long attemptTimeoutMs = resolveAttemptTimeoutMs();
        if (attemptTimeoutMs > 0) {
            invoke = invoke.toCompletableFuture().orTimeout(attemptTimeoutMs, TimeUnit.MILLISECONDS);
        }

        return invoke.whenComplete((response, ex) -> {
            // attempt 层簿记位点独立 try/catch（裁定⑥）：簿记失败 ERROR+计数，stage 以业务
            // response/ex 完成——簿记异常不再成为 stage 完成异常（逃逸入口关闭，C3-006）
            try {
                long endTime = recordStore.getCurrentTime();
                Timestamp startTime = attempt.getStartTime();
                attempt.setEndTime(new Timestamp(endTime));
                attempt.setDurationMs(startTime != null ? endTime - startTime.getTime() : null);

                if (ex != null) {
                    attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_FAILED);
                    ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, ex);
                    attempt.setErrorCode(errorBean.getErrorCode());
                    attempt.setErrorMessage(errorBean.getDescription());
                    attempt.setErrorStack(errorBean.getErrorStack());
                } else if (response != null && response.isOk()) {
                    attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_SUCCESS);
                } else {
                    attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_FAILED);
                    ErrorBean errorBean = response != null
                            ? ErrorMessageManager.instance().buildErrorMessage(null, NopRebuildException.rebuild(response))
                            : ErrorMessageManager.instance().buildErrorMessage(null,
                                    new NopException(ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR));
                    attempt.setErrorCode(errorBean.getErrorCode());
                    attempt.setErrorMessage(errorBean.getDescription());
                    attempt.setErrorStack(errorBean.getErrorStack());
                }
                recordStore.saveAttempt(attempt);
            } catch (Throwable t) {
                attemptBookkeepFailureCount.incrementAndGet();
                LOG.error("nop.retry.attempt.bookkeep-fail:recordId={},op=saveFinalAttempt",
                        record.getSid(), t);
            }
        });
    }

    /**
     * 1.6-C7-003（plan 2515 裁定⑦）：invokeAsync 同步抛路径的 attempt 终态化落账。
     * 簿记失败按裁定⑥口径 ERROR+计数隔离（不覆盖业务异常传导）。
     */
    private void finalizeAttemptOnSyncThrow(NopRetryRecord record, NopRetryAttempt attempt, Throwable t) {
        try {
            long endTime = recordStore.getCurrentTime();
            Timestamp startTime = attempt.getStartTime();
            attempt.setEndTime(new Timestamp(endTime));
            attempt.setDurationMs(startTime != null ? endTime - startTime.getTime() : null);
            attempt.setStatus(NopRetryConstants.RETRY_ATTEMPT_STATUS_FAILED);
            ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, t);
            attempt.setErrorCode(errorBean.getErrorCode());
            attempt.setErrorMessage(errorBean.getDescription());
            attempt.setErrorStack(errorBean.getErrorStack());
            recordStore.saveAttempt(attempt);
        } catch (Throwable bookkeepFailure) {
            attemptBookkeepFailureCount.incrementAndGet();
            LOG.error("nop.retry.attempt.bookkeep-fail:recordId={},op=finalizeSyncThrowAttempt",
                    record.getSid(), bookkeepFailure);
        }
    }

    private CompletionStage<ApiResponse<?>> handleDeadlineExceeded(NopRetryRecord record) {
        LOG.warn("nop.retry.deadline-exceeded:recordId={}", record.getSid());
        moveToDeadLetter(record, "DEADLINE_EXCEEDED", "Deadline timeout exceeded", null);
        return CompletableFuture.failedStage(
                new NopException(ERR_RETRY_DEADLINE_EXCEEDED)
                        .param(ARG_RECORD_ID, record.getSid()));
    }

    private void handleExecutionSuccess(NopRetryRecord record, NopRetryPolicy policy, IRetryTask task) {
        record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED);
        recordStore.updateRecord(record);
        LOG.info("nop.retry.task-completed:recordId={}", record.getSid());

        if (policy.hasCallbackPolicy() && shouldTriggerCallback(policy, true)) {
            triggerCallback(record, policy, true, null);
        }
    }

    private void handleExecutionFailure(NopRetryRecord record, NopRetryPolicy policy, Throwable ex, IRetryTask task) {
        LOG.error("nop.retry.task-failed:recordId={}", record.getSid(), ex);

        int retryCount = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
        record.setRetryCount(retryCount);
        int maxRetryCount = record.getMaxRetryCount() != null ? record.getMaxRetryCount() : policy.getMaxRetryCountOrDefault();

        if (retryCount >= maxRetryCount) {
            moveToDeadLetter(record, ex);
            if (policy.hasCallbackPolicy() && shouldTriggerCallback(policy, false)) {
                triggerCallback(record, policy, false, ex);
            }
        } else {
            long nextTriggerTime = calculateNextTriggerTime(record, policy);
            record.setNextTriggerTime(new Timestamp(nextTriggerTime));
            record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_PENDING);
            recordStore.updateRecord(record);
        }
    }

    private boolean shouldTriggerCallback(NopRetryPolicy policy, boolean success) {
        switch (policy.getCallbackTriggerTypeOrDefault()) {
            case NopRetryConstants.CALLBACK_TRIGGER_TYPE_ON_SUCCESS:
                return success;
            case NopRetryConstants.CALLBACK_TRIGGER_TYPE_ON_FAILURE:
                return !success;
            case NopRetryConstants.CALLBACK_TRIGGER_TYPE_ALWAYS:
            default:
                return true;
        }
    }

    private void triggerCallback(NopRetryRecord record, NopRetryPolicy policy, boolean success, Throwable error) {
        if (!policy.hasCallbackPolicy()) {
            LOG.debug("nop.retry.callback-policy-not-configured:recordId={}", record.getSid());
            return;
        }

        String callbackService = record.getServiceName();
        String callbackMethod = record.getServiceMethod();
        if (StringHelper.isEmpty(callbackService) || StringHelper.isEmpty(callbackMethod)) {
            LOG.warn("nop.retry.callback-service-not-configured:recordId={}", record.getSid());
            return;
        }

        // 1.6-C7-008（plan 2543 W-09）：回调链环护栏。policy 的 callbackPolicyId 若成环
        // （A→A 或 A→B→A），每条回调记录终态又孵化下一条、idempotentId 逐环追加 "_callback"
        // 永不撞 UK —— 自维持的无限副作用调用 + 无界记录增长。深度护栏：孵化前统计 idempotentId
        // 尾部的 "_callback" 重复次数，达到上限拒绝孵化（主业务结论已定，回调只是旁路）。
        int depth = callbackDepth(record.getIdempotentId());
        int maxDepth = resolveMaxCallbackDepth();
        if (maxDepth > 0 && depth >= maxDepth) {
            callbackDepthExceededCount.incrementAndGet();
            LOG.error("nop.retry.callback-depth-exceeded:recordId={},depth={},maxDepth={},idempotentId={}",
                    record.getSid(), depth, maxDepth, record.getIdempotentId());
            return;
        }

        // 创建回调任务，使用 callbackPolicyId 作为策略
        IRetryTask callbackTask = newRetryTask(callbackService, callbackMethod)
                .withPolicyId(policy.getCallbackPolicyId())
                .withIdempotentId(record.getIdempotentId() + "_callback");

        ApiRequest<Object> callbackRequest = new ApiRequest<>();
        callbackRequest.setData(buildCallbackData(record, success, error));

        // 异步执行回调任务。1.6-C8-017（plan 2515 裁定⑥ E-b）：executeTask 同步抛（DB 类故障）
        // 补 try 隔离——回调提交失败 ERROR 留痕，不影响主结论（业务结果已定）
        try {
            executeTask(callbackTask, callbackRequest, null)
                    .whenComplete((resp, ex) -> {
                        if (ex != null) {
                            LOG.error("nop.retry.callback-failed:recordId={}", record.getSid(), ex);
                        } else if (resp != null && resp.isOk()) {
                            LOG.info("nop.retry.callback-success:recordId={}", record.getSid());
                        } else {
                            LOG.warn("nop.retry.callback-response-error:recordId={},status={}",
                                    record.getSid(), resp != null ? resp.getStatus() : null);
                        }
                    });
        } catch (Throwable t) {
            LOG.error("nop.retry.callback-submit-failed:recordId={}", record.getSid(), t);
        }
    }

    /**
     * 1.6-C7-008（plan 2543 W-09）：回调深度 = idempotentId 尾部连续 "_callback" 后缀的重复次数。
     * 只匹配紧贴末尾的重复段（不统计业务键中间恰巧出现 "_callback" 的情形，避免假阳性）；
     * 环检测充分性：每经过一环回调记录 idempotentId 增长一次该后缀，故后缀次数即环深上界。
     */
    static int callbackDepth(String idempotentId) {
        if (idempotentId == null)
            return 0;
        int depth = 0;
        int idx = idempotentId.length();
        String suffix = CALLBACK_ID_SUFFIX;
        while (idx >= suffix.length() && idempotentId.startsWith(suffix, idx - suffix.length())) {
            depth++;
            idx -= suffix.length();
        }
        return depth;
    }

    private int resolveMaxCallbackDepth() {
        return maxCallbackDepth != null ? maxCallbackDepth
                : RetryEngineConfigs.CFG_RETRY_CALLBACK_MAX_DEPTH.get();
    }

    private Object buildCallbackData(NopRetryRecord record, boolean success, Throwable error) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("recordId", record.getSid());
        data.put("idempotentId", record.getIdempotentId());
        data.put("success", success);
        data.put("retryCount", record.getRetryCount());
        if (error != null) {
            data.put("errorCode", error instanceof NopException ? ((NopException) error).getErrorCode() : "UNKNOWN");
            data.put("errorMessage", error.getMessage());
        }
        return data;
    }

    private void moveToDeadLetter(NopRetryRecord record, Throwable ex) {
        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, ex);
        recordStore.moveToDeadLetter(record, errorBean.getErrorCode(), errorBean.getDescription(), errorBean.getErrorStack());
        LOG.warn("nop.retry.task-moved-to-dead-letter:recordId={}", record.getSid());
    }

    private void moveToDeadLetter(NopRetryRecord record, String errorCode, String errorMessage, String errorStack) {
        recordStore.moveToDeadLetter(record, errorCode, errorMessage, errorStack);
        LOG.warn("nop.retry.task-moved-to-dead-letter:recordId={}", record.getSid());
    }

    private long calculateNextTriggerTime(NopRetryRecord record, NopRetryPolicy policy) {
        int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;

        long interval;
        if (policy.getBackoffStrategyOrDefault() == NopRetryConstants.BACKOFF_STRATEGY_EXPONENTIAL_BACKOFF) {
            interval = policy.getInitialIntervalMsOrDefault() * (1L << Math.min(retryCount - 1, 10));
        } else {
            interval = policy.getInitialIntervalMsOrDefault();
        }

        interval = Math.min(interval, policy.getMaxIntervalMsOrDefault());

        double jitterRatio = policy.getJitterRatioOrDefault();
        if (jitterRatio > 0) {
            double jitterFactor = 1.0 + (Math.random() * 2 - 1) * jitterRatio;
            interval = (long) (interval * jitterFactor);
        }

        return recordStore.getCurrentTime() + interval;
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
        // 1.6-C3-003（plan 2515 裁定④）：attempt 超时上界必须小于租约，装配期 fail-fast——
        // 否则挂死 attempt 存活跨租约边界，扫描器接管读到未终态化 attempt，attempt 内双执行窗复现
        long attemptTimeout = resolveAttemptTimeoutMs();
        if (attemptTimeout > 0 && attemptTimeout >= NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS) {
            throw new NopException(ERR_RETRY_ATTEMPT_TIMEOUT_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.attempt.timeout-ms")
                    .param(ARG_CONFIG_VALUE, attemptTimeout);
        }

        if (retryScanner != null) {
            retryScanner.startScanning(this::dispatchScannerBatch);
            LOG.info("nop.retry.scanner-started");
        }

        // 1.6-C7-001（plan 2502）：三账本表保留期清理周期链接线（生产可达生命周期钩子，
        // 防 C7-005「清理 API 零生产装配」空壳形态）。retention-days=0 为显式禁用。
        startLedgerCleanup();
    }

    /**
     * 1.6-C7-014（plan 2543 W-11）：扫描批内 per-record 隔离派发（设计 §3.3.2）。
     * <p>
     * 原实现为 {@code records.stream().map(executeRetryFromScanner...).toArray()}——惰性流的
     * map 在 toArray 时逐条求值，第 k 条同步抛出即中断，第 k+1..N 条的 future 根本不会被创建；
     * 且被锁定的记录已置 RETRYING（租约 +10min），下一周期同页排序不变时永久顺延（确定性饿死）。
     * 改为显式循环 + 单条 try/catch：单条同步抛不再中断本批其余记录，失败显式 ERROR + 计数
     * （非静默），其租约到期后由扫描器重新拾取。
     */
    protected void dispatchScannerBatch(java.util.List<NopRetryRecord> records) {
        java.util.List<CompletableFuture<?>> futures = new java.util.ArrayList<>(records.size());
        for (NopRetryRecord record : records) {
            try {
                futures.add(executeRetryFromScanner(record, null)
                        .whenComplete((resp, ex) -> {
                            if (ex != null) {
                                LOG.error("nop.retry.scanner.execute-failed:recordId={}", record.getSid(), ex);
                            }
                        })
                        .toCompletableFuture());
            } catch (Throwable t) {
                scannerDispatchFailureCount.incrementAndGet();
                LOG.error("nop.retry.scanner.record-dispatch-failed:recordId={}", record.getSid(), t);
            }
        }

        // 等待本批所有已派发的 record 执行完成（join 拓扑零改动——每条 future 必有终态）
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Override
    protected void doStop() {
        if (ledgerCleanupFuture != null) {
            ledgerCleanupFuture.cancel(false);
            ledgerCleanupFuture = null;
            LOG.info("nop.retry.ledger.cleanup-stopped");
        }
        if (retryScanner != null) {
            retryScanner.stopScanning();
            LOG.info("nop.retry.scanner-stopped");
        }
    }

    /**
     * 1.6-C7-001：账本保留期清理周期链。载体与扫描链同构（绝缘层 scheduleWithFixedDelay +
     * IPeriodicTaskListener）：清理体任何 Throwable 由设施捕获观测并重臂，失败 ERROR 显式可见
     * （fail-fast 包络：不静默吞掉、链不中断）；错配置（负保留期/非正批删上限/周期低于 60s 下限）
     * 启动期 fail-fast——参数语义见 ledger-retention-policy §3.1 第 1/2/3/5 项。
     */
    protected void startLedgerCleanup() {
        int retentionDays = resolveLedgerRetentionDays();
        long intervalMillis = resolveLedgerCleanupInterval().toMillis();
        int batchSize = resolveLedgerDeleteBatchSize();

        if (retentionDays < 0) {
            throw new NopException(ERR_RETRY_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.ledger.retention-days")
                    .param(ARG_CONFIG_VALUE, retentionDays);
        }
        if (batchSize <= 0) {
            throw new NopException(ERR_RETRY_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.ledger.delete-batch-size")
                    .param(ARG_CONFIG_VALUE, batchSize);
        }
        if (intervalMillis < RetryEngineConfigs.RETRY_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS) {
            throw new NopException(ERR_RETRY_LEDGER_CONFIG_INVALID)
                    .param(ARG_CONFIG_KEY, "nop.retry.ledger.cleanup-interval")
                    .param(ARG_CONFIG_VALUE, intervalMillis);
        }
        if (retentionDays == 0) {
            LOG.info("nop.retry.ledger.cleanup-disabled");
            return;
        }

        long retentionMillis = retentionDays * 24L * 3600_000L;
        ledgerCleanupFuture = getExecutor().scheduleWithFixedDelay(this::runLedgerCleanup,
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS,
                "nop-retry-ledger-cleanup", new IPeriodicTaskListener() {
                    @Override
                    public void onRunFailure(Throwable e, long consecutiveFailures) {
                        ledgerCleanupFailureCount.incrementAndGet();
                        LOG.error("nop.retry.ledger.cleanup-fail:consecutiveFailures={}", consecutiveFailures, e);
                    }

                    @Override
                    public void onChainRejected(Throwable e) {
                        ledgerCleanupFailureCount.incrementAndGet();
                        LOG.error("nop.retry.ledger.cleanup-chain-rejected: cleanup chain terminated"
                                + " by executor rejection, will not resurrect", e);
                    }
                });
        LOG.info("nop.retry.ledger.cleanup-started:retentionDays={},intervalMillis={},batchSize={}",
                retentionDays, intervalMillis, batchSize);
    }

    /**
     * 单轮清理：清理体失败直接抛出（由绝缘 listener 观测），不在本方法内吞异常。
     */
    protected void runLedgerCleanup() {
        long retentionMillis = resolveLedgerRetentionDays() * 24L * 3600_000L;
        long deleted = recordStore.purgeExpiredLedgerRows(retentionMillis, resolveLedgerDeleteBatchSize());
        if (deleted > 0) {
            LOG.info("nop.retry.ledger.cleanup-done:deleted={}", deleted);
        }
    }

    private int resolveLedgerRetentionDays() {
        return ledgerRetentionDays != null ? ledgerRetentionDays
                : RetryEngineConfigs.CFG_RETRY_LEDGER_RETENTION_DAYS.get();
    }

    private Duration resolveLedgerCleanupInterval() {
        return ledgerCleanupInterval != null ? ledgerCleanupInterval
                : RetryEngineConfigs.CFG_RETRY_LEDGER_CLEANUP_INTERVAL.get();
    }

    private int resolveLedgerDeleteBatchSize() {
        return ledgerDeleteBatchSize != null ? ledgerDeleteBatchSize
                : RetryEngineConfigs.CFG_RETRY_LEDGER_DELETE_BATCH_SIZE.get();
    }
}
