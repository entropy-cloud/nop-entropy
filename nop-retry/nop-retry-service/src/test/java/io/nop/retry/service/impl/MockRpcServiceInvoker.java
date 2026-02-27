/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.service.impl;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ICancelToken;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock implementation of IRpcServiceInvoker for testing RetryServiceImpl
 */
@Singleton
public class MockRpcServiceInvoker implements IRpcServiceInvoker {

    private ApiResponse<?> response;
    private final List<ApiResponse<?>> responses = new ArrayList<>();
    private final AtomicInteger responseIndex = new AtomicInteger(0);
    private final AtomicInteger invocationCount = new AtomicInteger(0);

    private String lastServiceName;
    private String lastServiceMethod;

    public void setResponse(ApiResponse<?> response) {
        this.response = response;
        this.responses.clear();
    }

    public void setResponses(ApiResponse<?>... responses) {
        this.responses.clear();
        for (ApiResponse<?> r : responses) {
            this.responses.add(r);
        }
        this.response = null;
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
        this.response = null;
        this.responses.clear();
        this.responseIndex.set(0);
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

        ApiResponse<?> resp;
        if (!responses.isEmpty()) {
            int idx = responseIndex.getAndIncrement();
            resp = idx < responses.size() ? responses.get(idx) : response;
        } else {
            resp = response;
        }

        return CompletableFuture.completedFuture(resp);
    }
}
