/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class AopRpcServiceInvocation implements IRpcServiceInvocation {
    private final IRpcServiceInvocation invocation;
    private final List<IRpcServiceInterceptor> interceptors;

    private int index;

    public AopRpcServiceInvocation(IRpcServiceInvocation invocation,
                                   List<IRpcServiceInterceptor> interceptors) {
        this.invocation = Guard.notNull(invocation, "invocation is null");
        this.interceptors = Guard.notEmpty(interceptors, "interceptors is empty");
    }

    @Override
    public String getServiceName() {
        return invocation.getServiceName();
    }

    @Override
    public String getServiceMethod() {
        return invocation.getServiceMethod();
    }

    @Override
    public ApiRequest<?> getRequest() {
        return invocation.getRequest();
    }

    @Override
    public ICancelToken getCancelToken() {
        return invocation.getCancelToken();
    }

    @Override
    public CompletionStage<ApiResponse<?>> proceedAsync() {
        if (index >= interceptors.size()) {
            return invocation.proceedAsync();
        }

        IRpcServiceInterceptor interceptor = interceptors.get(index);
        index++;
        return interceptor.interceptAsync(this);
    }

    @Override
    public ApiResponse<?> proceed() {
        if (index >= interceptors.size()) {
            return invocation.proceed();
        }

        IRpcServiceInterceptor interceptor = interceptors.get(index);
        index++;
        return interceptor.intercept(this);
    }

    public boolean isInbound() {
        return invocation.isInbound();
    }
}
