/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine.impl;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ICancelToken;
import jakarta.inject.Singleton;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of IRpcServiceInvoker for testing RetryEngineImpl.
 *
 * <p>Uses a {@link ConcurrentLinkedQueue} so each {@link #invokeAsync} call
 * atomically polls its own response.</p>
 */
@Singleton
public class MockRpcServiceInvoker implements IRpcServiceInvoker {

    private volatile ApiResponse<?> fallbackResponse;
    private final Queue<ApiResponse<?>> responseQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger invocationCount = new AtomicInteger(0);

    private String lastServiceName;
    private String lastServiceMethod;

    public void setResponse(ApiResponse<?> response) {
        this.fallbackResponse = response;
        this.responseQueue.clear();
    }

    public void setResponses(ApiResponse<?>... responses) {
        this.responseQueue.clear();
        this.fallbackResponse = null;
        for (ApiResponse<?> r : responses) {
            this.responseQueue.offer(r);
        }
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public String getLastServiceName() {
        return lastServiceName;
    }

    public String getLastServiceMethod() {
        return lastServiceMethod;
    }

    public void reset() {
        this.fallbackResponse = null;
        this.responseQueue.clear();
        this.invocationCount.set(0);
        this.lastServiceName = null;
        this.lastServiceMethod = null;
    }

    @Override
    public CompletionStage<ApiResponse<?>> invokeAsync(
            String serviceName,
            String serviceMethod,
            ApiRequest<?> request,
            ICancelToken cancelToken) {

        invocationCount.incrementAndGet();
        lastServiceName = serviceName;
        lastServiceMethod = serviceMethod;

        ApiResponse<?> resp = responseQueue.poll();
        if (resp == null) {
            resp = fallbackResponse;
        }
        if (resp == null) {
            resp = ApiResponse.success(null);
        }

        return CompletableFuture.completedFuture(resp);
    }
}
