/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.service;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.biz.api.IBizObject;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;

import java.util.concurrent.CompletionStage;

public class BizActionInvocation implements IRpcServiceInvocation {
    private final IBizObject bizObj;
    private final String action;
    private final ApiRequest<?> request;

    private final ICancelToken cancelToken;

    public BizActionInvocation(IBizObject bizObj, String action, ApiRequest<?> request, ICancelToken cancelToken) {
        this.bizObj = bizObj;
        this.action = action;
        this.request = request;
        this.cancelToken = cancelToken;
    }

    @Override
    public String getServiceName() {
        return bizObj.getBizObjName();
    }

    @Override
    public String getServiceMethod() {
        return action;
    }

    @Override
    public ApiRequest<?> getRequest() {
        return request;
    }

    @Override
    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    @Override
    public CompletionStage<ApiResponse<?>> proceedAsync() {
        try {
            IServiceContext ctx = (IServiceContext) ContextProvider.currentContext()
                    .getAttribute(ApiConstants.ATTR_SERVICE_CONTEXT);
            if (ctx == null) {
                ctx = new ServiceContextImpl();
                ctx.setRequestHeaders(request.getHeaders());
                ctx.setRequest(request.getData());
            }
            Object result = bizObj.invoke(action, request.getData(), request.getFieldSelection(), ctx);
            if (result instanceof CompletionStage)
                return ((CompletionStage<?>) result).thenApply(this::toResponse);
            return FutureHelper.success(toResponse(result));
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
    }

    private ApiResponse<?> toResponse(Object value) {
        if (value instanceof ApiResponse)
            return (ApiResponse<?>) value;
        return ApiResponse.buildSuccess(value);
    }

    @Override
    public boolean isInbound() {
        return true;
    }
}