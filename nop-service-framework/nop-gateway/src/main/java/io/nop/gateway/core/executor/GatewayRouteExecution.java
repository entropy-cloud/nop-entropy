/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.model.GatewayRouteModel;

/**
 * IGatewayInvocation implementation that wraps a GatewayRouteModel.
 * Uses composition instead of modifying GatewayRouteModel directly.
 * <p>
 * Execution flow: requestMapping → onRequest → invoke|forward → onResponse → responseMapping
 */
public class GatewayRouteExecution implements IGatewayInvocation {

    private final GatewayRouteModel route;
    private final MappingProcessor mappingProcessor;

    public GatewayRouteExecution(GatewayRouteModel route,
                                 MappingProcessor mappingProcessor) {
        this.route = route;
        this.mappingProcessor = Guard.notNull(mappingProcessor,"mappingProcessor");
    }

    public GatewayRouteModel getRoute() {
        return route;
    }

    @Override
    public ApiRequest<?> proceedOnRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        if (route.getRequestMapping() != null) {
            request = mappingProcessor.mapRequest(route.getRequestMapping(), request, svcCtx);
        }

        IEvalFunction onRequest = route.getOnRequest();
        if (onRequest != null) {
            Object result = onRequest.call2(null, request, svcCtx, svcCtx.getEvalScope());
            if (result instanceof ApiRequest) {
                request = (ApiRequest<?>) result;
            }
        }

        return request;
    }

    @Override
    public ApiResponse<?> proceedOnResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
        IEvalFunction onResponse = route.getOnResponse();
        if (onResponse != null) {
            Object result = onResponse.call2(null, response, svcCtx, svcCtx.getEvalScope());
            if (result instanceof ApiResponse) {
                response = (ApiResponse<?>) result;
            }
        }

        if (route.getResponseMapping() != null) {
            response = mappingProcessor.mapResponse(route.getResponseMapping(), response, svcCtx);
        }

        return response;
    }

    @Override
    public ApiResponse<?> proceedOnError(Throwable exception, IGatewayContext svcCtx) {
        IEvalFunction onError = route.getOnError();
        if (onError != null) {
            Object result = onError.call2(null, exception, svcCtx, svcCtx.getEvalScope());
            if (result instanceof ApiResponse) {
                return (ApiResponse<?>) result;
            }
        }

        throw NopException.adapt(exception);
    }

    @Override
    public void proceedOnStreamStart(ApiRequest<?> request, IGatewayContext svcCtx) {
        if (route.getStreaming() != null) {
            IEvalFunction onStreamStart = route.getStreaming().getOnStreamStart();
            if (onStreamStart != null) {
                onStreamStart.call2(null, request, svcCtx, svcCtx.getEvalScope());
            }
        }
    }

    @Override
    public Object proceedOnStreamElement(Object element, IGatewayContext svcCtx) {
        if (route.getStreaming() == null) {
            return element;
        }

        IEvalFunction onStreamElement = route.getStreaming().getOnStreamElement();
        if (onStreamElement != null) {
            element = onStreamElement.call2(null, element, svcCtx, svcCtx.getEvalScope());
        }

        if (route.getStreaming().getElementMapping() != null ) {
            element = mappingProcessor.mapElement(
                    route.getStreaming().getElementMapping(), element, svcCtx);
        }

        return element;
    }

    @Override
    public Object proceedOnStreamError(Throwable exception, IGatewayContext svcCtx) {
        if (route.getStreaming() != null) {
            IEvalFunction onStreamError = route.getStreaming().getOnStreamError();
            if (onStreamError != null) {
                Object result = onStreamError.call2(null, exception, svcCtx, svcCtx.getEvalScope());
                if (result != null) {
                    return result;
                }
            }
        }

        throw NopException.adapt(exception);
    }

    @Override
    public void proceedOnStreamComplete(IGatewayContext svcCtx) {
        if (route.getStreaming() != null) {
            IEvalFunction onStreamComplete = route.getStreaming().getOnStreamComplete();
            if (onStreamComplete != null) {
                onStreamComplete.call1(null, svcCtx, svcCtx.getEvalScope());
            }
        }
    }

    public boolean hasForward() {
        return route.getForward() != null;
    }

    public boolean hasInvoke() {
        return route.getInvoke() != null;
    }

    public boolean isStreamingEnabled(IGatewayContext svcCtx) {
        if (route.getStreaming() == null) {
            return false;
        }
        IEvalAction enabled = route.getStreaming().getEnabled();
        if (enabled != null) {
            Object result =  enabled.invoke(svcCtx.getEvalScope());
            return ConvertHelper.toBoolean(result);
        }
        return true;
    }
}
