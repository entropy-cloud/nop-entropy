/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.tcc.api.*;
import io.nop.tcc.core.TccCoreErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class TccTransaction implements ITccTransaction {
    static final Logger LOG = LoggerFactory.getLogger(TccTransaction.class);

    private final TccEngine tccEngine;
    private final ITccRecord tccRecord;
    private final boolean initiator;

    /**
     * 1.6-C3-008（plan 2543 W-04）：恢复链驱动令牌（0 = 非纪元管理链，如存活调用方链）。
     * 聚合写终态前校验令牌仍活跃——超时被放弃的旧链苏醒后不得与新一轮恢复链争写终态。
     */
    private final long drivingEpoch;

    public TccTransaction(boolean initiator, ITccRecord tccRecord, TccEngine tccEngine) {
        this(initiator, tccRecord, tccEngine, 0L);
    }

    public TccTransaction(boolean initiator, ITccRecord tccRecord, TccEngine tccEngine, long drivingEpoch) {
        this.initiator = initiator;
        this.tccRecord = tccRecord;
        this.tccEngine = tccEngine;
        this.drivingEpoch = drivingEpoch;
    }

    public String toString() {
        return "TccTransaction[txnId=" + getTxnId() + ",txnGroup=" + getTxnGroup() + ",status=" + tccRecord.getTccStatus() + "]";
    }

    public ITccRecord getTccRecord() {
        return tccRecord;
    }

    @Override
    public boolean isInitiator() {
        return initiator;
    }

    protected ITccRecordStore getRepository() {
        return tccEngine.getTccRecordRepository();
    }

    @Override
    public CompletionStage<Void> beginAsync() {
        LOG.info("nop.tcc.begin:txn={}", this);
        return getRepository().saveTccRecordAsync(tccRecord, TccStatus.TRYING);
    }

    @Override
    public CompletionStage<Void> endAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
        return tccEngine.loadBranchTransactionsAsync(this).thenCompose(branchTxns -> {
            // confirm阶段已经开始的事务必须继续confirm直到成功（TCC语义：confirm开始后不允许回滚）。
            // 此前CONFIRMING/CONFIRM_FAILED全局事务被分支的rollbackOnly导向cancel路径，又被doCancelAsync
            // 的confirm状态守卫拦截为no-op，崩溃恢复时事务永久卡死在中间态
            TccStatus curStatus = tccRecord.getTccStatus();
            if (curStatus == TccStatus.CONFIRMING || curStatus == TccStatus.CONFIRM_FAILED) {
                return doConfirmAsync(branchTxns);
            }
            // cancel半区的对称路由（P1-3.8-004，plan 2388）：cancel开始后不允许confirm（TCC语义），
            // 存活调用方与崩溃恢复路径同路由。此前全局CANCELLING/CANCEL_FAILED落入分支证据判定：
            // 恢复调用(endAsync(true,null,null))无回滚证据时被导向doConfirmAsync，又被其CANCELLING
            // 守卫拦截为no-op——每轮被扫描器重拾bump retryTimes，耗尽maxRetryCount后被永久遗弃
            if (curStatus == TccStatus.CANCELLING || curStatus == TccStatus.CANCEL_FAILED) {
                LOG.info("nop.tcc.resume-cancel:txn={},status={}", this, curStatus);
                return doCancelAsync(timeout, branchTxns);
            }

            // 1.6-C6-015（plan 2543 W-07-3）：超时恢复的未定向态一律回滚。
            // 原缺省是「isRollbackOnly/isFailed 都为假 → confirm」——超时恢复的
            // endAsync(true,null,null) 无回滚证据时会 confirm 一条没跑完的业务链，
            // 使失败事务以确认方向释放参与方预留资源（最严重的错误方向）。
            // 记录仍为未定向态时不存在「confirm 已经开始」的证据，故不违反 TCC confirm 不可回滚。
            if (timeout && isUndirected(curStatus)) {
                LOG.info("nop.tcc.timeout-recovery-cancel:txn={},status={}", this, curStatus);
                return doCancelAsync(timeout, branchTxns);
            }

            if (isRollbackOnly(branchTxns) || isFailed(response, ex)) {
                if (ex != null) {
                    LOG.info("nop.tcc.exec-fail", ex);
                } else if (response != null && !response.isOk()) {
                    LOG.info("nop.tcc.response:{}", JsonTool.stringify(response));
                }
                return doCancelAsync(timeout, branchTxns);
            } else {
                return doConfirmAsync(branchTxns);
            }
        }).whenComplete((ret, err2) -> {
            // 补偿(confirm/cancel)阶段产生的异常记录到日志，避免业务异常覆盖时丢失诊断信息
            if (err2 != null && ex != null) {
                LOG.error("nop.tcc.end-compensate-fail:txn={}", this, err2);
            }
            // 出现异常时避免业务异常被补偿阶段的返回值吃掉
            if (ex != null)
                throw NopException.adapt(ex);
        });
    }

    private CompletionStage<Void> doCancelAsync(boolean timeout, List<ITccBranchTransaction> branchTxns) {
        LOG.info("nop.tcc.cancel:txn={},branches={}", this, branchTxns);

        TccStatus curStatus = tccRecord.getTccStatus();
        // 如果事务正在或已经 confirm，则不允许 cancel，避免与 confirm 路径互相覆盖中间态。
        // curStatus为null（status列为null的脏数据）时不命中任何拦截条件，按普通cancel路径处理
        if (curStatus == TccStatus.CONFIRMING || curStatus == TccStatus.CONFIRM_FAILED
                || (curStatus != null && curStatus.isConfirmed()))
            return FutureHelper.success(null);

        if (curStatus != null && (curStatus.isCancelled() || curStatus.isFinished()))
            return FutureHelper.success(null);

        // plan 2515 裁定①：首跳 CAS 失败（0行/异常）异常完成 → thenCompose 结构性短路，
        // 分支零驱动即零补偿 RPC；终态折叠经聚合挂点门控（见 aggregateAndWriteFinalStatus）
        // plan 2543 W-07-1：首跳标记落库有限重试——该写是「业务失败/方向」的唯一持久化证据，
        // 一次瞬时 DB 抖动即短路会使记录滞留 TRYING 且无证据，恢复时按缺省方向收敛（方向翻转）
        CompletionStage<Void> future = retryStatusWrite(() ->
                        getRepository().updateTccStatusAsync(tccRecord, TccStatus.CANCELLING, null))
                .thenCompose(r -> TccRunner.cancelAllAsync(branchTxns, timeout, tccEngine.getServiceInvoker()));

        return FutureHelper.whenCompleteAsync(future, (response, err) ->
                aggregateAndWriteFinalStatus(true, branchTxns, err, timeout));
    }

    private CompletionStage<Void> doConfirmAsync(List<ITccBranchTransaction> branchTxns) {
        LOG.info("nop.tcc.confirm:txn={},branches={}", this, branchTxns);

        TccStatus curStatus = tccRecord.getTccStatus();
        // 如果事务正在或已经 cancel，则不允许 confirm，避免与 cancel 路径互相覆盖中间态。
        // curStatus为null（脏数据）时不命中任何拦截条件，按普通confirm路径处理
        if (curStatus == TccStatus.CANCELLING || curStatus == TccStatus.TIMEOUT_FAILED
                || (curStatus != null && curStatus.isCancelled()))
            return FutureHelper.success(null);

        if (curStatus != null && curStatus.isFinished())
            return FutureHelper.success(null);

        // plan 2515 裁定①：同 doCancelAsync——首跳 CAS 失败异常完成即分支零驱动
        // plan 2543 W-07-1：同 doCancelAsync——首跳标记落库有限重试（证据持久化与分支补偿解耦）
        CompletionStage<Void> future = retryStatusWrite(() ->
                        getRepository().updateTccStatusAsync(tccRecord, TccStatus.CONFIRMING, null))
                .thenCompose(r -> TccRunner.confirmAllAsync(branchTxns, tccEngine.getServiceInvoker()));
        return FutureHelper.whenCompleteAsync(future, (r2, err) ->
                aggregateAndWriteFinalStatus(false, branchTxns, err, false));
    }

    /**
     * 1.6-C6-015（plan 2543 W-07-1）：首跳状态标记的有限重试。
     * <p>
     * 该写是「业务失败/补偿方向」的唯一持久化证据：写失败的后果不是「这次补偿没做成」而是
     * 「记录滞留 TRYING 且无方向证据，后续恢复按缺省方向收敛」——失败事务可能被 confirm。
     * 重试仅针对非 CAS 冲突错误（CAS 0 行是语义冲突，重试无意义）；最多 3 次，耗尽后
     * ERROR 留痕并显式失败（不吞）。
     */
    private CompletionStage<Void> retryStatusWrite(java.util.function.Supplier<CompletionStage<Void>> write) {
        return retryStatusWrite(write, 1, STATUS_WRITE_MAX_ATTEMPTS);
    }

    private CompletionStage<Void> retryStatusWrite(java.util.function.Supplier<CompletionStage<Void>> write,
                                                   int attempt, int maxAttempts) {
        CompletionStage<Void> stage;
        try {
            stage = write.get();
        } catch (Throwable t) {
            return retryOrFail(t, write, attempt, maxAttempts);
        }
        return stage.handle((v, err) -> {
            if (err == null)
                return FutureHelper.success(null);
            return retryOrFail(err, write, attempt, maxAttempts);
        }).thenCompose(s -> (CompletionStage<Void>) s);
    }

    private CompletionStage<Void> retryOrFail(Throwable err, java.util.function.Supplier<CompletionStage<Void>> write,
                                              int attempt, int maxAttempts) {
        if (err instanceof NopException
                && TccErrors.ERR_TCC_STATUS_CAS_CONFLICT.getErrorCode().equals(((NopException) err).getErrorCode())) {
            // CAS 冲突是语义冲突（并发 writer 已改状态），重试必然再次失败
            return FutureHelper.reject(err);
        }
        if (attempt >= maxAttempts) {
            LOG.error("nop.tcc.status-write-retry-exhausted:txn={},attempts={}", this, attempt, err);
            return FutureHelper.reject(err);
        }
        LOG.warn("nop.tcc.status-write-retry:txn={},attempt={}", this, attempt, err);
        return retryStatusWrite(write, attempt + 1, maxAttempts);
    }

    private static final int STATUS_WRITE_MAX_ATTEMPTS = 3;

    /**
     * plan 2515 裁定③（终态直写前置校验）：挂点显式 err==null 门控——驱动链（含首跳 CAS）
     * 异常完成时不得执行终态折叠，全局保持 CANCELLING/CONFIRMING 交恢复扫描续跑，异常向上传播。
     * 裁定②（聚合权威裁决）：err==null 时聚合读 DB 权威分支状态（按 txnId 重载分支行，
     * 按 branchId 对齐；内存副本仅作调度判定），聚合函数本体（TccRunner）零改写；
     * 重载查询失败按裁定①异常完成传播，不折叠任何终态。
     */
    private CompletionStage<Void> aggregateAndWriteFinalStatus(boolean cancel,
                                                               List<ITccBranchTransaction> branchTxns, Throwable err,
                                                               boolean timeout) {
        if (err != null)
            return FutureHelper.reject(err);

        // 1.6-C3-008（plan 2543 W-04）：弃链写点闸——本链若为恢复链且纪元已被新一轮推进
        // （超时弃链苏醒），不得折叠终态/回写，否则与新链争写。存活调用方链令牌为 0，不受影响。
        if (drivingEpoch > 0 && !tccEngine.isActiveDriving(getTxnId(), drivingEpoch)) {
            LOG.warn("nop.tcc.driving-abandoned:txn={},epoch={}", this, drivingEpoch);
            return FutureHelper.reject(new NopException(TccCoreErrors.ERR_TCC_DRIVING_ABANDONED)
                    .param(TccCoreErrors.ARG_TXN_GROUP, getTxnGroup())
                    .param(TccCoreErrors.ARG_TXN_ID, getTxnId()));
        }

        return convergeAndAggregate(cancel, branchTxns, timeout, 1);
    }

    /**
     * 1.6-C1-007（plan 2543 W-03 腿2）：补偿期孤儿分支收敛 + 聚合。
     * <p>
     * 入口快照之外的分支（父事务补偿期间新插入）此前无任何驱动入口。本方法每轮：
     * ①重载 DB 分支行（2515 裁定②权威输入）；②按 branchId 求差集得到孤儿；
     * ③以既有的隔离批量助手（W-06 落地形态，自带方向筛与 per-branch try/catch）驱动一轮；
     * ④重读并重新聚合。最多 3 轮；仍有「本方向可驱动且未结算」分支则不折叠终态
     * （failedStage），全局保持中间态交恢复扫描——恢复入口由此闭合。
     */
    private CompletionStage<Void> convergeAndAggregate(boolean cancel, List<ITccBranchTransaction> branchTxns,
                                                       boolean timeout, int round) {
        // 注意：不可用 thenApply+thenCompose(identity) 展平——ResolvedPromise 的 thenApply
        // 会把内层 stage 解包为结果值，identity 展平将把内层 result（null）当 stage 返回（NPE 源）
        return getRepository().getBranchRecordsAsync(tccRecord).thenCompose(rows -> {
            // 聚合输入必须覆盖 DB 中的**全部**分支行（含入口快照之外的孤儿）——
            // 否则孤儿的结算状态不参与终态判定，终态折叠会忽略它（closure audit B-2）
            List<ITccBranchTransaction> views = buildAllViews(branchTxns, rows, tccEngine);
            // 待收敛 = 入口快照之外、且在本方向上仍可驱动（未结算）的分支。
            // 已结算的孤儿不再计入——这是每轮收敛能单调收敛（而非永远非空）的关键。
            List<ITccBranchTransaction> pending = findUnsettledOrphans(views, cancel);

            if (pending.isEmpty() || round > MAX_CONVERGENCE_ROUNDS) {
                if (!pending.isEmpty()) {
                    // 收敛轮次耗尽后仍有未结算孤儿 → 不折叠终态，保持 CANCELLING/CONFIRMING
                    // 交恢复扫描续跑（变体 B 的永久悬挂面由此闭合）
                    LOG.error("nop.tcc.branch-convergence-exhausted:txn={},cancel={},unsettled={}",
                            this, cancel, pending.size());
                    return FutureHelper.reject(new NopException(TccCoreErrors.ERR_TCC_BRANCH_CONVERGENCE_EXHAUSTED)
                            .param(TccCoreErrors.ARG_TXN_GROUP, getTxnGroup())
                            .param(TccCoreErrors.ARG_TXN_ID, getTxnId()));
                }
                TccStatus status = cancel
                        ? TccRunner.aggregateCancelBranchStatus(views)
                        : TccRunner.aggregateConfirmBranchStatus(views);
                return writeFinalStatusAndReleaseEpoch(status);
            }

            LOG.info("nop.tcc.branch-convergence:txn={},round={},unsettled={}", this, round, pending.size());
            CompletionStage<Void> drive = cancel
                    ? TccRunner.cancelAllAsync(pending, timeout, tccEngine.getServiceInvoker())
                    : TccRunner.confirmAllAsync(pending, tccEngine.getServiceInvoker());

            return drive.handle((v, driveErr) -> {
                if (driveErr != null) {
                    // 收敛驱动失败不折叠终态：保持中间态交恢复扫描（孤儿已在下一轮重试）
                    LOG.warn("nop.tcc.branch-convergence-drive-failed:txn={},round={}", this, round, driveErr);
                }
                return null;
            }).thenCompose(v -> convergeAndAggregate(cancel, branchTxns, timeout, round + 1));
        });
    }

    /**
     * 1.6-C1-007（plan 2543 W-03 腿2）：聚合与收敛的统一视图集——DB 行优先（裁定②权威输入），
     * DB 中缺席的分支（清理竞态）保留内存副本。入口快照之外的 DB 行（孤儿）以
     * {@link RowOnlyBranchTxnView} 参与聚合与驱动。
     */
    private static List<ITccBranchTransaction> buildAllViews(List<ITccBranchTransaction> branchTxns,
                                                             List<ITccBranchRecord> rows, TccEngine engine) {
        Map<String, ITccBranchTransaction> byId = new HashMap<>();
        for (ITccBranchTransaction branchTxn : branchTxns) {
            byId.put(branchTxn.getBranchId(), branchTxn);
        }

        List<ITccBranchTransaction> views = new ArrayList<>();
        java.util.Set<String> covered = new java.util.HashSet<>();
        if (rows != null) {
            for (ITccBranchRecord row : rows) {
                covered.add(row.getBranchId());
                ITccBranchTransaction known = byId.get(row.getBranchId());
                if (known == null) {
                    // 入口快照之外的孤儿分支
                    views.add(new RowOnlyBranchTxnView(row, engine));
                } else if (known.getBranchRecord() == row) {
                    views.add(known);
                } else {
                    views.add(new AuthoritativeBranchTxnView(known, row));
                }
            }
        }
        // DB 中已不存在、但入口快照仍持有的分支（清理竞态）保留内存副本
        for (ITccBranchTransaction branchTxn : branchTxns) {
            if (!covered.contains(branchTxn.getBranchId())) {
                views.add(branchTxn);
            }
        }
        return views;
    }

    /**
     * 1.6-C1-007（plan 2543 W-03 腿2）：**入口快照之外**且在本方向上**仍可驱动**的分支。
     * 已结算的孤儿不再计入——否则每轮都会把它当孤儿重驱动，循环永不收敛（closure audit B-2）。
     * 可驱动判据与 {@link TccRunner} 的批量筛条件严格对齐。
     */
    private static List<ITccBranchTransaction> findUnsettledOrphans(List<ITccBranchTransaction> views,
                                                                   boolean cancel) {
        List<ITccBranchTransaction> pending = new ArrayList<>();
        for (ITccBranchTransaction view : views) {
            if (!(view instanceof RowOnlyBranchTxnView))
                continue;

            TccStatus status = view.getBranchStatus();
            if (cancel) {
                // isBranchCancellable 已排除已 cancel/已 confirm/KILLED/TRY_FAILED（含
                // CANCEL_SUCCESS/TIMEOUT_SUCCESS 的 isCancelled 面）——无需二次判定
                if (TccRunner.isBranchCancellable(status))
                    pending.add(view);
            } else {
                if (status == null || status == TccStatus.CONFIRM_SUCCESS || status == TccStatus.KILLED)
                    continue;
                if (status.isCancelled() || status == TccStatus.CONFIRM_FAILED)
                    continue;
                pending.add(view);
            }
        }
        return pending;
    }

    private CompletionStage<Void> writeFinalStatusAndReleaseEpoch(TccStatus status) {
        return getRepository().updateTccStatusAsync(tccRecord, status, null)
                .whenComplete((v, e) -> {
                    if (e == null && drivingEpoch > 0) {
                        tccEngine.endDriving(getTxnId());
                    }
                });
    }

    /** 收敛轮次数上限：3 轮后仍有未结算分支则不折叠终态。 */
    private static final int MAX_CONVERGENCE_ROUNDS = 3;

    /**
     * 1.6-C1-007（plan 2543 W-03 腿2）：孤儿分支视图——仅持有重载的 DB 行，供批量助手驱动。
     * 驱动方法经 TccBranchTransaction 走 store，但孤儿行来自重载快照，
     * 故此处以独立包装提供最小驱动面：状态读取 + 委托式驱动。
     */
    private static final class RowOnlyBranchTxnView implements ITccBranchTransaction {
        private final ITccBranchRecord row;
        private final TccEngine engine;

        RowOnlyBranchTxnView(ITccBranchRecord row, TccEngine engine) {
            this.row = row;
            this.engine = engine;
        }

        @Override
        public ITccBranchRecord getBranchRecord() {
            return row;
        }

        private TccBranchTransaction delegate() {
            return new TccBranchTransaction(row, engine);
        }

        @Override
        public CompletionStage<Void> beginTryAsync() {
            return delegate().beginTryAsync();
        }

        @Override
        public CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex) {
            return delegate().finishTryAsync(response, ex);
        }

        @Override
        public CompletionStage<Void> beginConfirmAsync() {
            return delegate().beginConfirmAsync();
        }

        @Override
        public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
            return delegate().finishConfirmAsync(response, ex);
        }

        @Override
        public CompletionStage<Void> beginCancelAsync(boolean timeout) {
            return delegate().beginCancelAsync(timeout);
        }

        @Override
        public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
            return delegate().finishCancelAsync(timeout, response, ex);
        }
    }

    /** 聚合输入的 DB 权威视图：状态与标识取自重载行，其余能力委托原分支事务 */
    private static final class AuthoritativeBranchTxnView implements ITccBranchTransaction {
        private final ITccBranchTransaction delegate;
        private final ITccBranchRecord row;

        AuthoritativeBranchTxnView(ITccBranchTransaction delegate, ITccBranchRecord row) {
            this.delegate = delegate;
            this.row = row;
        }

        @Override
        public ITccBranchRecord getBranchRecord() {
            return row;
        }

        @Override
        public CompletionStage<Void> beginTryAsync() {
            return delegate.beginTryAsync();
        }

        @Override
        public CompletionStage<Void> finishTryAsync(ApiResponse<?> response, Throwable ex) {
            return delegate.finishTryAsync(response, ex);
        }

        @Override
        public CompletionStage<Void> beginConfirmAsync() {
            return delegate.beginConfirmAsync();
        }

        @Override
        public CompletionStage<Void> finishConfirmAsync(ApiResponse<?> response, Throwable ex) {
            return delegate.finishConfirmAsync(response, ex);
        }

        @Override
        public CompletionStage<Void> beginCancelAsync(boolean timeout) {
            return delegate.beginCancelAsync(timeout);
        }

        @Override
        public CompletionStage<Void> finishCancelAsync(boolean timeout, ApiResponse<?> response, Throwable ex) {
            return delegate.finishCancelAsync(timeout, response, ex);
        }
    }

    private boolean isFailed(ApiResponse<?> response, Throwable ex) {
        if (ex != null)
            return true;
        return response != null && !response.isOk();
    }

    /**
     * 1.6-C6-015（plan 2543 W-07-3）：未定向态——既未进入 confirm 半区也未进入 cancel 半区。
     * 注意 CURStatus 读的是本链内存副本，恢复链在 endAsync 入口即持有 DB 加载的最新行。
     */
    private static boolean isUndirected(TccStatus status) {
        return status == null || status == TccStatus.CREATED
                || status == TccStatus.TRYING || status == TccStatus.TRY_SUCCESS;
    }

    private boolean isRollbackOnly(List<ITccBranchTransaction> branchTxns) {
        for (ITccBranchTransaction branchTxn : branchTxns) {
            TccStatus status = branchTxn.getBranchStatus();
            // 分支状态为null（脏数据）时排除该分支，不参与决策也不NPE
            if (status == null) {
                LOG.warn("nop.tcc.ignore-branch-with-null-status:txnId={},branchId={}",
                        branchTxn.getTxnId(), branchTxn.getBranchId());
                continue;
            }
            if (status.isRollbackOnly())
                return true;
        }
        return false;
    }
}