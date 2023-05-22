/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.core.composite;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.rpc.api.IRpcService;
import io.nop.rpc.core.utils.RpcHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 将启动方法和状态检查方法组合成一个完整的RPC调用。执行启动方法之后不直接启动方法的返回结果，而是不断的调用statusMethod来获取返回结果。
 */
public class PollingRpcClient implements IRpcService {
    static final Logger LOG = LoggerFactory.getLogger(PollingRpcClient.class);

    private final IRpcService rpcService;
    private final String statusMethod;
    private final IScheduledExecutor timer;
    private final long pollInterval;

    public PollingRpcClient(IRpcService rpcService, String statusMethod, IScheduledExecutor timer, long pollInterval) {
        this.rpcService = Guard.notNull(rpcService, "rpcService");
        this.statusMethod = Guard.notEmpty(statusMethod, "statusMethod");
        this.timer = Guard.notNull(timer, "timer");
        this.pollInterval = Guard.positiveLong(pollInterval, "pollInterval");
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        return rpcService.callAsync(serviceMethod, request, cancelToken).thenCompose(ret -> {
            if (!ret.isBizSuccess()) {
                return FutureHelper.success(ret);
            } else {
                PollTask task = new PollTask(request, cancelToken);
                task.schedule();
                return task.future;
            }
        });
    }

    private class PollTask implements Callable<Void> {
        final ApiRequest<?> request;
        final ICancelToken cancelToken;
        final CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
        Future<?> timerFuture;

        public PollTask(ApiRequest<?> request, ICancelToken cancelToken) {
            this.request = request;
            this.cancelToken = cancelToken;
            if (cancelToken != null) {
                cancelToken.appendOnCancelTask(() -> cancel());
            }
        }

        public void cancel() {
            Future<?> f = this.future;
            if (f != null) {
                f.cancel(false);
            }
            future.cancel(false);
        }

        public void schedule() {
            timerFuture = timer.schedule(this, pollInterval, TimeUnit.MILLISECONDS);
        }

        public Void call() {
            rpcService.callAsync(statusMethod, request, cancelToken).whenComplete((ret, err) -> {
                if (err != null) {
                    future.completeExceptionally(err);
                } else {
                    try {
                        if (!ret.isOk()) {
                            future.complete(ret);
                        } else {
                            if (isCompleted(ret)) {
                                future.complete(ret);
                            } else {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("nop.rpc.poll-task-status:response={}", JSON.serialize(ret, true));
                                }
                                schedule();
                            }
                        }
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    }
                }
            });
            return null;
        }
    }

    protected boolean isCompleted(ApiResponse<?> ret) {
        ApiResponse<TaskStatusBean> res = RpcHelper.toTaskStatusResponse(ret);
        return res.getData() != null && res.getData().isCompleted();
    }
}
