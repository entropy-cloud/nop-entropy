/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.service;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.biz.api.IBizObject;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.api.core.rpc.IRpcServiceInvocation;

import java.util.concurrent.CompletionStage;

import static io.nop.biz.BizErrors.ARG_ACTION_NAME;
import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_ASYNC_INVOCATION_NO_CONTEXT;

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
            // C3-007 fail-fast：无线附着时语义化错误（非静默附着，也不允许无上下文NPE）——
            // 异步任务体必须经 BizActionService 的受管提交（快照+executeWithContext）携带
            // 调用方 context 执行
            IContext current = ContextProvider.currentContext();
            if (current == null) {
                throw new NopException(ERR_BIZ_ASYNC_INVOCATION_NO_CONTEXT)
                        .param(ARG_BIZ_OBJ_NAME, getServiceName())
                        .param(ARG_ACTION_NAME, action);
            }
            IServiceContext ctx = (IServiceContext) current.getAttribute(ApiConstants.ATTR_SERVICE_CONTEXT);
            if (ctx == null) {
                ctx = new ServiceContextImpl();
                ctx.setRequestHeaders(request.getHeaders());
                ctx.setRequest(request.getData());
            }
            Object result = bizObj.invoke(action, request.getData(), request.getSelection(), ctx);
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
        return ApiResponse.success(value);
    }

    @Override
    public boolean isInbound() {
        return true;
    }
}