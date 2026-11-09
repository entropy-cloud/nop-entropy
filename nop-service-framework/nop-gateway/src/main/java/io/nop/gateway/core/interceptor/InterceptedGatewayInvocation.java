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

    /** 2.3-C9-005：拦截器链是否存在 onStreamElement 覆写（构造期一次判定，反射声明类比对） */
    private final boolean hasStreamElementInterceptor;

    private static boolean overridesOnStreamElement(IGatewayInterceptor interceptor) {
        try {
            return interceptor.getClass()
                    .getMethod("onStreamElement", Object.class, IGatewayContext.class)
                    .getDeclaringClass() != IGatewayInterceptor.class;
        } catch (NoSuchMethodException e) {
            return true; // 不可能：接口方法必然存在；保守档
        }
    }

    public InterceptedGatewayInvocation(List<IGatewayInterceptor> interceptors, IGatewayInvocation invocation) {
        this.interceptors = interceptors;
        boolean any = false;
        if (interceptors != null) {
            for (IGatewayInterceptor interceptor : interceptors) {
                if (overridesOnStreamElement(interceptor)) {
                    any = true;
                    break;
                }
            }
        }
        // 2.3-C9-005：委托层（GatewayRouteExecution）的路由级变换同样计入
        this.hasStreamElementInterceptor = any || invocation.hasStreamElementTransform();
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

    /**
     * 2.3-C9-005（plan 2551 裁定 L）：无任何流元素变换（拦截器无覆写 + 路由无映射）时
     * 返回 false——StreamingProcessor 据此走透传快径（String 元素免 parse/再 serialize）。
     */
    @Override
    public boolean hasStreamElementTransform() {
        return hasStreamElementInterceptor;
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
        return invocation.proceedOnStreamElement(result, svcCtx);
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
