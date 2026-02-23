/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayInterceptorModel;

public class ModelBasedGatewayInterceptor implements IGatewayInterceptor {

    private final GatewayInterceptorModel model;

    public ModelBasedGatewayInterceptor(GatewayInterceptorModel model) {
        this.model = model;
    }

    @Override
    public ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        IEvalFunction onRequest = model.getOnRequest();
        if (onRequest != null) {
            Object result = onRequest.call2(null, request, svcCtx, svcCtx.getEvalScope());
            if (result instanceof ApiRequest) {
                return (ApiRequest<?>) result;
            }
        }
        return request;
    }

    @Override
    public ApiResponse<?> onResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
        IEvalFunction onResponse = model.getOnResponse();
        if (onResponse != null) {
            Object result = onResponse.call2(null, response, svcCtx, svcCtx.getEvalScope());
            if (result instanceof ApiResponse) {
                return (ApiResponse<?>) result;
            }
        }
        return response;
    }

    @Override
    public ApiResponse<?> onError(Throwable exception, IGatewayContext svcCtx) {
        IEvalFunction onError = model.getOnError();
        if (onError != null) {
            Object result = onError.call2(null, exception, svcCtx, svcCtx.getEvalScope());
            if (result instanceof ApiResponse) {
                return (ApiResponse<?>) result;
            }
        }
        return null;
    }

    @Override
    public void onStreamStart(ApiRequest<?> request, IGatewayContext svcCtx) {
        IEvalFunction onStreamStart = model.getOnStreamStart();
        if (onStreamStart != null) {
            onStreamStart.call2(null, request, svcCtx, svcCtx.getEvalScope());
        }
    }

    @Override
    public Object onStreamElement(Object element, IGatewayContext svcCtx) {
        IEvalFunction onStreamElement = model.getOnStreamElement();
        if (onStreamElement != null) {
            return onStreamElement.call2(null, element, svcCtx, svcCtx.getEvalScope());
        }
        return element;
    }

    @Override
    public Object onStreamError(Throwable exception, IGatewayContext svcCtx) {
        IEvalFunction onStreamError = model.getOnStreamError();
        if (onStreamError != null) {
            return onStreamError.call2(null, exception, svcCtx, svcCtx.getEvalScope());
        }
        throw NopException.adapt(exception);
    }

    @Override
    public void onStreamComplete(IGatewayContext svcCtx) {
        IEvalFunction onStreamComplete = model.getOnStreamComplete();
        if (onStreamComplete != null) {
            onStreamComplete.call1(null, svcCtx, svcCtx.getEvalScope());
        }
    }
}
