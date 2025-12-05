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
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.IApiResponseNormalizer;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceLocator;
import io.nop.tcc.api.ITccBranchRecord;
import io.nop.tcc.api.ITccBranchTransaction;
import io.nop.tcc.api.TccStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * TccEngine内部实现所用到的帮助类。
 */
public class TccRunner {

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
                                                                        IRpcServiceLocator serviceLocator) {
        ITccBranchRecord branchRecord = branchTxn.getBranchRecord();
        if (branchRecord.getBranchStatus().isConfirmed())
            return FutureHelper.success(null);

        IRpcService service = serviceLocator.getService(branchRecord.getServiceName());

        // 如果标记状态失败，则不会去执行服务函数
        return branchTxn.beginConfirmAsync().thenCompose(arg -> {
            // 如果执行失败，则需要更新txn状态
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            service.callAsync(branchRecord.getConfirmMethod(), branchRecord.getRequest(), null)
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
                                                                       IRpcServiceLocator serviceLocator) {
        ITccBranchRecord branchRecord = branchTxn.getBranchRecord();
        if (branchRecord.getBranchStatus().isCancelled())
            return FutureHelper.success(null);

        IRpcService service = serviceLocator.getService(branchRecord.getServiceName());

        // 如果标记状态失败，则不会去执行服务函数
        return branchTxn.beginCancelAsync(timeout).thenCompose(arg -> {
            // 如果执行失败，则需要更新txn状态
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            service.callAsync(branchRecord.getCancelMethod(), branchRecord.getRequest(), null)
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
            if (!status.isAllowConfirm())
                return false;
        }
        return true;
    }

    public static CompletionStage<Void> confirmAllAsync(List<ITccBranchTransaction> branchTxns,
                                                        IRpcServiceLocator serviceLocator) {
        List<CompletionStage<?>> futures = new ArrayList<>(branchTxns.size());
        for (ITccBranchTransaction branchTxn : branchTxns) {
            if (branchTxn.getBranchStatus() == TccStatus.CONFIRM_SUCCESS)
                continue;
            futures.add(runBranchConfirmAsync(branchTxn, serviceLocator));
        }
        return FutureHelper.waitAll(futures);
    }

    public static CompletionStage<Void> cancelAllAsync(List<ITccBranchTransaction> branchTxns, boolean timeout,
                                                       IRpcServiceLocator serviceLocator) {
        List<CompletionStage<?>> futures = new ArrayList<>(branchTxns.size());
        for (ITccBranchTransaction branchTxn : branchTxns) {
            if (branchTxn.getBranchStatus().isCancelled())
                continue;
            futures.add(runBranchCancelAsync(branchTxn, timeout, serviceLocator));
        }
        return FutureHelper.waitAll(futures);
    }

    public static TccStatus aggregateCancelBranchStatus(List<ITccBranchTransaction> branchTxns) {
        for (ITccBranchTransaction branchTxn : branchTxns) {
            if (branchTxn.getBranchStatus() == TccStatus.BIZ_CANCEL_FAILED)
                return TccStatus.BIZ_CANCEL_FAILED;
            if (branchTxn.getBranchStatus() == TccStatus.CANCEL_FAILED)
                return TccStatus.CANCEL_FAILED;
        }
        return TccStatus.CANCEL_SUCCESS;
    }

    public static TccStatus aggregateConfirmBranchStatus(List<ITccBranchTransaction> branchTxns) {
        for (ITccBranchTransaction branchTxn : branchTxns) {
            if (branchTxn.getBranchStatus() == TccStatus.CONFIRM_FAILED)
                return TccStatus.CONFIRM_FAILED;
            if (branchTxn.getBranchStatus().isCancelled())
                return branchTxn.getBranchStatus();
        }
        return TccStatus.CONFIRM_SUCCESS;
    }
}
