/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.task;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 发出请求后允许通过cancelMethod来取消执行
 */
public class CancellableRpcClient implements IRpcService {
    private final IRpcService rpcService;
    private final String cancelMethod;

    public CancellableRpcClient(IRpcService rpcService, String cancelMethod) {
        this.rpcService = rpcService;
        this.cancelMethod = Guard.notEmpty(cancelMethod, "cancelMethod");
    }

    @Override
    public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                     ICancelToken cancelToken) {
        CompletionStage<ApiResponse<?>> future = rpcService.callAsync(serviceMethod, request, cancelToken);
        AtomicBoolean done = new AtomicBoolean();
        future.whenComplete((r, e) -> done.set(true));

        if (done.get())
            return future;

        if (cancelToken != null) {
            cancelToken.appendOnCancel(reason -> {
                // 只有当任务尚未结束，才会执行cancelMethod去尝试主动取消
                if (!done.get()) {
                    rpcService.callAsync(cancelMethod, request, null);
                }
            });
        }
        return future;
    }
}