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
import io.nop.gateway.core.context.IGatewayContext;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class InterceptedGatewayInvocation implements IGatewayInvocation {

    private final List<IGatewayInterceptor> interceptors;
    private final IGatewayInvocation invocation;
    private int currentInterceptorIndex = 0;

    public InterceptedGatewayInvocation(List<IGatewayInterceptor> interceptors, IGatewayInvocation invocation) {
        this.interceptors = interceptors;
        this.invocation = invocation;
    }

    @Override
    public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> request, IGatewayContext svcCtx) {
        if (currentInterceptorIndex < interceptors.size()) {
            IGatewayInterceptor interceptor = interceptors.get(currentInterceptorIndex++);
            return interceptor.invoke(this, request, svcCtx);
        }
        return invocation.proceedInvoke(request, svcCtx);
    }



    @Override
    public ApiRequest<?> proceedOnRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        ApiRequest<?> req = request;
        for (int i = 0, n = interceptors.size(); i < n; i++) {
            IGatewayInterceptor interceptor = interceptors.get(i);
            try {
                ApiRequest<?> ret = interceptor.onRequest(req, svcCtx);
                if (ret != null)
                    req = ret;
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
        return invocation.proceedOnRequest(req, svcCtx);
    }

    @Override
    public ApiResponse<?> proceedOnResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
        // Response处理按逆序执行
        ApiResponse<?> resp = invocation.proceedOnResponse(response, svcCtx);
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            IGatewayInterceptor interceptor = interceptors.get(i);
            ApiResponse<?> ret = interceptor.onResponse(resp, svcCtx);
            if (ret != null)
                resp = ret;
        }
        return resp;
    }

    @Override
    public ApiResponse<?> proceedOnError(Throwable exception, IGatewayContext svcCtx) {
        // Error处理按逆序执行
        try {
            return invocation.proceedOnError(exception, svcCtx);
        } catch (Exception e) {
            exception = e;
        }
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            IGatewayInterceptor interceptor = interceptors.get(i);
            try {
                ApiResponse<?> resp = interceptor.onError(exception, svcCtx);
                if (resp != null) {
                    return resp;
                }
            } catch (Exception e) {
                exception = e;
            }
        }
        throw NopException.adapt(exception);
    }

    @Override
    public void proceedOnStreamStart(ApiRequest<?> request, IGatewayContext svcCtx) {
        for (int i = 0, n = interceptors.size(); i < n; i++) {
            IGatewayInterceptor interceptor = interceptors.get(i);
            interceptor.onStreamStart(request, svcCtx);
        }
        invocation.proceedOnStreamStart(request, svcCtx);
    }

    @Override
    public Object proceedOnStreamElement(Object element, IGatewayContext svcCtx) {
        Object result = element;
        for (int i = 0, n = interceptors.size(); i < n; i++) {
            IGatewayInterceptor interceptor = interceptors.get(i);
            result = interceptor.onStreamElement(result, svcCtx);
            if (result == null) {
                return result;
            }
        }
        return invocation.proceedOnStreamElement(element, svcCtx);
    }

    @Override
    public Object proceedOnStreamError(Throwable exception, IGatewayContext svcCtx) {
        try {
            return invocation.proceedOnStreamError(exception, svcCtx);
        } catch (Exception e) {
            exception = e;
        }
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            IGatewayInterceptor interceptor = interceptors.get(i);
            try {
                Object resp = interceptor.onStreamError(exception, svcCtx);
                if (resp != null) {
                    return resp;
                }
            } catch (Exception e) {
                exception = e;
            }
        }
        throw NopException.adapt(exception);
    }

    @Override
    public void proceedOnStreamComplete(IGatewayContext svcCtx) {
        invocation.proceedOnStreamComplete(svcCtx);

        for (int i = interceptors.size() - 1; i >= 0; i--) {
            IGatewayInterceptor interceptor = interceptors.get(i);
            try {
                interceptor.onStreamComplete(svcCtx);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }
}
