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

    public TccTransaction(boolean initiator, ITccRecord tccRecord, TccEngine tccEngine) {
        this.initiator = initiator;
        this.tccRecord = tccRecord;
        this.tccEngine = tccEngine;
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
        CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CANCELLING, null)
                .thenCompose(r -> TccRunner.cancelAllAsync(branchTxns, timeout, tccEngine.getServiceInvoker()));

        return FutureHelper.whenCompleteAsync(future, (response, err) ->
                aggregateAndWriteFinalStatus(true, branchTxns, err));
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
        CompletionStage<Void> future = getRepository().updateTccStatusAsync(tccRecord, TccStatus.CONFIRMING, null)
                .thenCompose(r -> TccRunner.confirmAllAsync(branchTxns, tccEngine.getServiceInvoker()));
        return FutureHelper.whenCompleteAsync(future, (r2, err) ->
                aggregateAndWriteFinalStatus(false, branchTxns, err));
    }

    /**
     * plan 2515 裁定③（终态直写前置校验）：挂点显式 err==null 门控——驱动链（含首跳 CAS）
     * 异常完成时不得执行终态折叠，全局保持 CANCELLING/CONFIRMING 交恢复扫描续跑，异常向上传播。
     * 裁定②（聚合权威裁决）：err==null 时聚合读 DB 权威分支状态（按 txnId 重载分支行，
     * 按 branchId 对齐；内存副本仅作调度判定），聚合函数本体（TccRunner）零改写；
     * 重载查询失败按裁定①异常完成传播，不折叠任何终态。
     */
    private CompletionStage<Void> aggregateAndWriteFinalStatus(boolean cancel,
                                                               List<ITccBranchTransaction> branchTxns, Throwable err) {
        if (err != null)
            return FutureHelper.reject(err);

        // 注意：不可用 thenApply+thenCompose(identity) 展平——ResolvedPromise 的 thenApply
        // 会把内层 stage 解包为结果值，identity 展平将把内层 result（null）当 stage 返回（NPE 源）
        return getRepository().getBranchRecordsAsync(tccRecord).thenCompose(rows -> {
            List<ITccBranchTransaction> authoritative = buildAuthoritativeViews(branchTxns, rows);
            TccStatus status = cancel
                    ? TccRunner.aggregateCancelBranchStatus(authoritative)
                    : TccRunner.aggregateConfirmBranchStatus(authoritative);
            return getRepository().updateTccStatusAsync(tccRecord, status, null);
        });
    }

    /**
     * 按 branchId 将重载的 DB 权威分支行对齐到聚合输入：DB 行在场的分支用权威行视图
     * （内存副本仅作调度判定），不在场的分支（清理竞态等）保留内存副本。
     */
    private static List<ITccBranchTransaction> buildAuthoritativeViews(List<ITccBranchTransaction> branchTxns,
                                                                       List<ITccBranchRecord> rows) {
        if (rows == null || rows.isEmpty())
            return branchTxns;

        Map<String, ITccBranchRecord> rowsByBranchId = new HashMap<>();
        for (ITccBranchRecord row : rows) {
            rowsByBranchId.put(row.getBranchId(), row);
        }

        List<ITccBranchTransaction> views = new ArrayList<>(branchTxns.size());
        for (ITccBranchTransaction branchTxn : branchTxns) {
            ITccBranchRecord row = rowsByBranchId.get(branchTxn.getBranchId());
            if (row == null) {
                views.add(branchTxn);
            } else if (row == branchTxn.getBranchRecord()) {
                views.add(branchTxn);
            } else {
                views.add(new AuthoritativeBranchTxnView(branchTxn, row));
            }
        }
        return views;
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