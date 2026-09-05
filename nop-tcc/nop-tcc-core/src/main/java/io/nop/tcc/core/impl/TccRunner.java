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

        // 如果标记状态失败，则不会去执行服务函数
        return branchTxn.beginTryAsync().thenCompose(arg -> {
            ApiRequest<?> request = branchRecord.getRequest();
            // 将tcc事务相关信息追加到rpc请求的元数据中
            if (!TccHelper.isDefaultTxnGroup(branchTxn.getTxnGroup())) {
                ApiHeaders.setTxnGroup(request, branchTxn.getTxnGroup());
            }
            ApiHeaders.setTxnId(request, branchTxn.getTxnId());
            ApiHeaders.setTxnBranchId(request, branchTxn.getBranchId());
            ApiHeaders.setTxnBranchNo(request, branchTxn.getBranchNo());

            // 如果执行失败，则需要更新txn状态
            CompletableFuture<T> future = new CompletableFuture<>();
            task.apply(branchTxn).whenComplete((ret, ex) -> {
                branchTxn.finishTryAsync(normalizer.toApiResponse(ret), ex).whenComplete((ret2, ex2) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else if (ex2 != null) {
                        future.completeExceptionally(ex2);
                    } else {
                        future.complete(ret);
                    }
                });
            });
            return future;
        });
    }

    public static CompletionStage<ApiResponse<?>> runBranchConfirmAsync(ITccBranchTransaction branchTxn,
                                                                        IRpcServiceInvoker serviceInvoker) {
        ITccBranchRecord branchRecord = branchTxn.getBranchRecord();
        if (branchRecord.getBranchStatus().isConfirmed())
            return FutureHelper.success(null);

        // 如果标记状态失败，则不会去执行服务函数
        return branchTxn.beginConfirmAsync().thenCompose(arg -> {
            // 如果执行失败，则需要更新txn状态
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getConfirmMethod(), branchRecord.getRequest(), null)
                    .whenComplete((ret, ex) -> {
                        branchTxn.finishConfirmAsync(ret, ex).whenComplete((ret2, ex2) -> {
                            if (ex != null) {
                                future.completeExceptionally(ex);
                            } else if (ex2 != null) {
                                future.completeExceptionally(ex2);
                            } else {
                                future.complete(ret);
                            }
                        });
                    });
            return future;
        });
    }

    public static CompletionStage<ApiResponse<?>> runBranchCancelAsync(ITccBranchTransaction branchTxn, boolean timeout,
                                                                       IRpcServiceInvoker serviceInvoker) {
        ITccBranchRecord branchRecord = branchTxn.getBranchRecord();
        if (branchRecord.getBranchStatus().isCancelled())
            return FutureHelper.success(null);

        // 如果标记状态失败，则不会去执行服务函数
        return branchTxn.beginCancelAsync(timeout).thenCompose(arg -> {
            // 如果执行失败，则需要更新txn状态
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getCancelMethod(), branchRecord.getRequest(), null)
                    .whenComplete((ret, ex) -> {
                        branchTxn.finishCancelAsync(timeout, ret, ex).whenComplete((ret2, ex2) -> {
                            if (ex != null) {
                                future.completeExceptionally(ex);
                            } else if (ex2 != null) {
                                future.completeExceptionally(ex2);
                            } else {
                                future.complete(ret);
                            }
                        });
                    });
            return future;
        });
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
            futures.add(runBranchConfirmAsync(branchTxn, serviceInvoker));
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
            futures.add(runBranchCancelAsync(branchTxn, timeout, serviceInvoker));
        }
        return FutureHelper.waitAll(futures);
    }

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
        }
        return TccStatus.CANCEL_SUCCESS;
    }

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
        }
        return TccStatus.CONFIRM_SUCCESS;
    }
}
