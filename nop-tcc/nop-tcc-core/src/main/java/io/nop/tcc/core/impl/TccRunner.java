/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.IApiResponseNormalizer;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.TccStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.tcc.core.TccCoreErrors.ARG_TCC_STATUS;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_GROUP;
import static io.nop.tcc.core.TccCoreErrors.ARG_TXN_ID;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * TccEngine内部实现所用到的帮助类。
 */
public class TccRunner {
    static final Logger LOG = LoggerFactory.getLogger(TccRunner.class);

    public static <T> CompletionStage<T> runBranchTryAsync(ITccBranchTransaction branchTxn, IApiResponseNormalizer normalizer,
                                                           Function<ITccBranchTransaction, CompletionStage<T>> task) {
        ITccBranchRecord branchRecord = branchTxn.getBranchRecord();

        // 1.6-C3-009（plan 2543 W-05）：手工 future 必须有终态——begin*Async() 在 thenCompose
        // lambda 之外调用（同步抛不会被 lambda 内 try 捕到）、finish/normalizer 在 lambda 内同步抛、
        // thenCompose stage 的异步异常，三条通道分别兜底（缺任一条即「future 永不完成」）。
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletionStage<Void> beginStage;
        try {
            // 如果标记状态失败，则不会去执行服务函数
            beginStage = branchTxn.beginTryAsync();
        } catch (Throwable t) {
            future.completeExceptionally(t);
            return future;
        }

        beginStage.thenCompose(arg -> {
            try {
                ApiRequest<?> request = branchRecord.getRequest();
                // 将tcc事务相关信息追加到rpc请求的元数据中
                if (!TccHelper.isDefaultTxnGroup(branchTxn.getTxnGroup())) {
                    ApiHeaders.setTxnGroup(request, branchTxn.getTxnGroup());
                }
                ApiHeaders.setTxnId(request, branchTxn.getTxnId());
                ApiHeaders.setTxnBranchId(request, branchTxn.getBranchId());
                ApiHeaders.setTxnBranchNo(request, branchTxn.getBranchNo());

                // 如果执行失败，则需要更新txn状态
                task.apply(branchTxn).whenComplete((ret, ex) -> {
                    try {
                        branchTxn.finishTryAsync(normalizer.toApiResponse(ret), ex).whenComplete((ret2, ex2) -> {
                            if (ex != null) {
                                future.completeExceptionally(ex);
                            } else if (ex2 != null) {
                                future.completeExceptionally(ex2);
                            } else {
                                future.complete(ret);
                            }
                        });
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }).exceptionally(t -> {
            future.completeExceptionally(t);
            return null;
        });
        return future;
    }

    public static CompletionStage<ApiResponse<?>> runBranchConfirmAsync(ITccBranchTransaction branchTxn,
                                                                        IRpcServiceInvoker serviceInvoker) {
        ITccBranchRecord branchRecord = branchTxn.getBranchRecord();
        if (branchRecord.getBranchStatus().isConfirmed())
            return FutureHelper.success(null);

        // 1.6-C3-009（plan 2543 W-05）：三通道兜底（begin 同步抛 / lambda 内同步抛 / stage 异步异常）
        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
        CompletionStage<Void> beginStage;
        try {
            // 如果标记状态失败，则不会去执行服务函数
            beginStage = branchTxn.beginConfirmAsync();
        } catch (Throwable t) {
            future.completeExceptionally(t);
            return future;
        }

        beginStage.thenCompose(arg -> {
            try {
                serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getConfirmMethod(),
                                branchRecord.getRequest(), null)
                        .whenComplete((ret, ex) -> {
                            try {
                                branchTxn.finishConfirmAsync(ret, ex).whenComplete((ret2, ex2) -> {
                                    if (ex != null) {
                                        future.completeExceptionally(ex);
                                    } else if (ex2 != null) {
                                        future.completeExceptionally(ex2);
                                    } else {
                                        future.complete(ret);
                                    }
                                });
                            } catch (Throwable t) {
                                future.completeExceptionally(t);
                            }
                        });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }).exceptionally(t -> {
            future.completeExceptionally(t);
            return null;
        });
        return future;
    }

    public static CompletionStage<ApiResponse<?>> runBranchCancelAsync(ITccBranchTransaction branchTxn, boolean timeout,
                                                                       IRpcServiceInvoker serviceInvoker) {
        ITccBranchRecord branchRecord = branchTxn.getBranchRecord();
        if (branchRecord.getBranchStatus().isCancelled())
            return FutureHelper.success(null);

        // 1.6-C3-009（plan 2543 W-05）：三通道兜底（begin 同步抛 / lambda 内同步抛 / stage 异步异常）
        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
        CompletionStage<Void> beginStage;
        try {
            // 如果标记状态失败，则不会去执行服务函数
            beginStage = branchTxn.beginCancelAsync(timeout);
        } catch (Throwable t) {
            future.completeExceptionally(t);
            return future;
        }

        beginStage.thenCompose(arg -> {
            try {
                serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getCancelMethod(),
                                branchRecord.getRequest(), null)
                        .whenComplete((ret, ex) -> {
                            try {
                                branchTxn.finishCancelAsync(timeout, ret, ex).whenComplete((ret2, ex2) -> {
                                    if (ex != null) {
                                        future.completeExceptionally(ex);
                                    } else if (ex2 != null) {
                                        future.completeExceptionally(ex2);
                                    } else {
                                        future.complete(ret);
                                    }
                                });
                            } catch (Throwable t) {
                                future.completeExceptionally(t);
                            }
                        });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }).exceptionally(t -> {
            future.completeExceptionally(t);
            return null;
        });
        return future;
    }

    public static boolean isAllBranchAllowConfirm(List<ITccBranchTransaction> branchTxns) {
        for (ITccBranchTransaction branchTxn : branchTxns) {
            TccStatus status = branchTxn.getBranchStatus();
            if (status == null || !status.isAllowConfirm())
                return false;
        }
        return true;
    }

    public static CompletionStage<Void> confirmAllAsync(List<ITccBranchTransaction> branchTxns,
                                                        IRpcServiceInvoker serviceInvoker) {
        List<CompletionStage<?>> futures = new ArrayList<>(branchTxns.size());
        for (ITccBranchTransaction branchTxn : branchTxns) {
            TccStatus status = branchTxn.getBranchStatus();
            // null状态分支为脏数据：排除并告警，不参与confirm调度（否则beginConfirmAsync的
            // 状态校验会同步抛异常中断整个confirm批次）
            if (status == null) {
                LOG.warn("nop.tcc.ignore-branch-with-null-status:txnId={},branchId={}",
                        branchTxn.getTxnId(), branchTxn.getBranchId());
                continue;
            }
            if (status == TccStatus.CONFIRM_SUCCESS)
                continue;
            // 1.6-C3-010（plan 2543 W-06）：逐分支隔离——单分支 begin/finish 同步抛（越界状态、
            // 脏数据）不再中断整批；失败分支进 waitAll（不静默），后续分支照常驱动。
            // 原缺陷：一次同步抛使排在其后的分支从未进入调度，且终态仍可被折叠为成功。
            try {
                futures.add(runBranchConfirmAsync(branchTxn, serviceInvoker));
            } catch (Throwable t) {
                LOG.error("nop.tcc.branch-confirm-dispatch-failed:txnId={},branchId={},status={}",
                        branchTxn.getTxnId(), branchTxn.getBranchId(), status, t);
                futures.add(CompletableFuture.failedFuture(t));
            }
        }
        return FutureHelper.waitAll(futures);
    }

    /**
     * 分支是否可以发起cancel：仍在事务中、且尚未处于任何不需要/不允许补偿的终态。
     * 包含TRY_SUCCESS——业务失败回滚时成功try的分支正是cancel的主要目标。
     * 此前用isRollbackOnly()判定，而TRY_SUCCESS属于allowConfirm状态（rollbackOnly=false），
     * 导致beginCancelAsync对成功try的分支同步抛异常，补偿被跳过且全局误报CANCEL_SUCCESS
     */
    public static boolean isBranchCancellable(TccStatus status) {
        if (status == null)
            return false;
        return status.isInTransaction() && !status.isCancelled() && !status.isConfirmed() && status != TccStatus.KILLED;
    }

    public static CompletionStage<Void> cancelAllAsync(List<ITccBranchTransaction> branchTxns, boolean timeout,
                                                       IRpcServiceInvoker serviceInvoker) {
        List<CompletionStage<?>> futures = new ArrayList<>(branchTxns.size());
        for (ITccBranchTransaction branchTxn : branchTxns) {
            // 跳过不需要/不允许cancel的分支：已取消(TRY_FAILED等)、已confirm（含无confirmMethod
            // 的自动确认分支）、KILLED等。此前只跳过isCancelled()，CONFIRM_SUCCESS等分支导致
            // beginCancelAsync同步抛异常，中断后续分支的补偿且聚合状态误报CANCEL_SUCCESS
            if (!isBranchCancellable(branchTxn.getBranchStatus()))
                continue;
            // 1.6-C3-010（plan 2543 W-06）：逐分支隔离（同 confirmAllAsync 形态）
            try {
                futures.add(runBranchCancelAsync(branchTxn, timeout, serviceInvoker));
            } catch (Throwable t) {
                LOG.error("nop.tcc.branch-cancel-dispatch-failed:txnId={},branchId={},status={}",
                        branchTxn.getTxnId(), branchTxn.getBranchId(), branchTxn.getBranchStatus(), t);
                futures.add(CompletableFuture.failedFuture(t));
            }
        }
        return FutureHelper.waitAll(futures);
    }

    /**
     * 1.6-C3-010（plan 2543 W-06）：cancel 方向聚合结算表。
     * <p>
     * 「成功」当且仅当每个分支在本方向已结算——已到 cancel 终态，或本方向本就不驱动它
     * （{@link #isBranchCancellable} 为假：TRY_FAILED/已 confirm/已 cancel/KILLED）。
     * 此前「非失败态一律成功」会把从未被 cancel 的 TRY_SUCCESS 分支折叠为 CANCEL_SUCCESS
     * （半个成功隐蔽变体）。注意 KILLED / CONFIRM_SUCCESS 必须算**已结算**——否则含这两类
     * 分支的父事务永不进 getFinishedStatus()，反向摧毁 plan 2515 裁定⑧ 的清理链。
     */
    public static TccStatus aggregateCancelBranchStatus(List<ITccBranchTransaction> branchTxns) {
        for (ITccBranchTransaction branchTxn : branchTxns) {
            TccStatus status = branchTxn.getBranchStatus();
            if (status == null) {
                LOG.warn("nop.tcc.ignore-branch-with-null-status:txnId={},branchId={}",
                        branchTxn.getTxnId(), branchTxn.getBranchId());
                continue;
            }
            if (status == TccStatus.BIZ_CANCEL_FAILED)
                return TccStatus.BIZ_CANCEL_FAILED;
            if (status == TccStatus.CANCEL_FAILED)
                return TccStatus.CANCEL_FAILED;
            // 超时取消失败聚合为可重试的 CANCEL_FAILED。此前被误聚合为 CANCEL_SUCCESS 终态，
            // 补偿被双重永久放弃，参与者预留资源悬挂
            if (status == TccStatus.TIMEOUT_FAILED)
                return TccStatus.CANCEL_FAILED;

            // 本方向不驱动它 → 已结算（TRY_FAILED 无需 cancel；已 confirm/已 cancel/KILLED 均终局）
            if (!isBranchCancellable(status))
                continue;

            // 本方向可驱动但仍在事务中态（TRY_SUCCESS/TRYING/TRY_UNKNOWN/CONFIRMING 等）
            // → 预留资源仍未释放，不得计为成功
            LOG.warn("nop.tcc.aggregate-cancel-unsettled-branch:txnId={},branchId={},status={}",
                    branchTxn.getTxnId(), branchTxn.getBranchId(), status);
            return TccStatus.CANCEL_FAILED;
        }
        return TccStatus.CANCEL_SUCCESS;
    }

    /**
     * 1.6-C3-010（plan 2543 W-06）：confirm 方向聚合结算表（同 cancel 侧口径）。
     * <p>
     * 2515 语义零回退：CONFIRM_FAILED → CONFIRM_FAILED；cancelled 分支 → 抛（状态机被破坏）。
     * 新增：除 CONFIRM_SUCCESS 与「cancelled（已抛）/CONFIRM_FAILED（已返回）」外，
     * 其余任何状态（TRY_SUCCESS/TRYING/TRY_UNKNOWN/CONFIRMING/KILLED）都不算 confirm 已完成
     * ——KILLED 例外：它是 2515 耗尽终态化的产物，父事务不再驱动它，按已结算计。
     */
    public static TccStatus aggregateConfirmBranchStatus(List<ITccBranchTransaction> branchTxns) {
        for (ITccBranchTransaction branchTxn : branchTxns) {
            TccStatus branchStatus = branchTxn.getBranchStatus();
            if (branchStatus == null) {
                LOG.warn("nop.tcc.ignore-branch-with-null-status:txnId={},branchId={}",
                        branchTxn.getTxnId(), branchTxn.getBranchId());
                continue;
            }
            if (branchStatus == TccStatus.CONFIRM_FAILED)
                return TccStatus.CONFIRM_FAILED;
            if (branchStatus.isCancelled()) {
                // 进入confirm阶段的分支理应全部allowConfirm，出现cancelled分支说明状态机被破坏
                throw new NopException(ERR_TCC_INVALID_CONFIRM_BRANCH_STATUS)
                        .param(ARG_TXN_GROUP, branchTxn.getTxnGroup())
                        .param(ARG_TXN_ID, branchTxn.getTxnId())
                        .param(ARG_TCC_STATUS, branchStatus);
            }
            if (branchStatus == TccStatus.CONFIRM_SUCCESS || branchStatus == TccStatus.KILLED)
                continue;

            // 其余状态均未完成 confirm（未驱动/驱动中/未知）——不得计为成功
            LOG.warn("nop.tcc.aggregate-confirm-unsettled-branch:txnId={},branchId={},status={}",
                    branchTxn.getTxnId(), branchTxn.getBranchId(), branchStatus);
            return TccStatus.CONFIRM_FAILED;
        }
        return TccStatus.CONFIRM_SUCCESS;
    }
}
