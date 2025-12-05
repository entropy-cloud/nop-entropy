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
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public class DefaultRpcServiceInvocation implements IRpcServiceInvocation {
    private final String serviceName;
    private final String serviceMethod;
    private final ApiRequest<?> request;
    private final ICancelToken cancelToken;

    private final boolean inbound;
    private final IRpcService service;

    public DefaultRpcServiceInvocation(String serviceName, String serviceMethod, ApiRequest<?> request,
                                       ICancelToken cancelToken,
                                       boolean inbound, IRpcService service) {
        this.serviceName = serviceName;
        this.serviceMethod = serviceMethod;
        this.request = request;
        this.cancelToken = cancelToken;
        this.inbound = inbound;
        this.service = service;
    }

    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getServiceMethod() {
        return serviceMethod;
    }

    @Override
    public ApiRequest<?> getRequest() {
        return request;
    }

    @Override
    public CompletionStage<ApiResponse<?>> proceedAsync() {
        return service.callAsync(serviceMethod, request, cancelToken);
    }

    public ApiResponse<?> proceed() {
        return service.call(serviceMethod, request, cancelToken);
    }

    @Override
    public boolean isInbound() {
        return inbound;
    }
}
