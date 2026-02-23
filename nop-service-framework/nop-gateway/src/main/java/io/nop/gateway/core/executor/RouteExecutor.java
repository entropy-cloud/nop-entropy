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
import io.nop.api.core.util.FutureHelper;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.interceptor.InterceptedGatewayInvocation;
import io.nop.gateway.model.GatewayRouteModel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 路由执行器，负责编排路由的完整执行流程
 *
 * <p>执行流程：</p>
 * <pre>
 * requestMapping => onRequest => invoke|forward => onResponse => responseMapping
 * </pre>
 *
 * <p>流式执行流程：</p>
 * <pre>
 * requestMapping => onRequest => streaming invoke
 *   [onStreamElement] => [elementMapping]
 * </pre>
 */
public class RouteExecutor {

    private final MappingProcessor mappingProcessor;
    private final InvokeProcessor invokeProcessor;
    private final ForwardProcessor forwardProcessor;
    private final StreamingProcessor streamingProcessor;

    public RouteExecutor(MappingProcessor mappingProcessor,
                         InvokeProcessor invokeProcessor,
                         ForwardProcessor forwardProcessor,
                         StreamingProcessor streamingProcessor) {
        this.mappingProcessor = mappingProcessor;
        this.invokeProcessor = invokeProcessor;
        this.forwardProcessor = forwardProcessor;
        this.streamingProcessor = streamingProcessor;
    }

    /**
     * 执行路由
     *
     * @param route        路由配置
     * @param request      API请求
     * @param context      网关上下文
     * @param interceptors 拦截器列表
     * @return 异步响应
     */
    public CompletionStage<ApiResponse<?>> execute(GatewayRouteModel route,
                                                   ApiRequest<?> request,
                                                   IGatewayContext context,
                                                   List<IGatewayInterceptor> interceptors) {
        IGatewayInvocation invocation = buildInvocation(route, interceptors);
        try {

            request = invocation.proceedOnRequest(request, context);

            if (route.getStreaming() != null && isStreamingEnabled(route, context))
                return executeStreaming(route, request, context, invocation);

            CompletionStage<ApiResponse<?>> future = executeRouteLogic(route, request, context)
                    .thenApply(response -> {
                        return invocation.proceedOnResponse(response, context);
                    });

            future = future.exceptionally(err -> {
                return invocation.proceedOnError(err, context);
            });
            return future;
        } catch (Exception e) {
            try {
                return FutureHelper.toCompletionStage(invocation.proceedOnError(e, context));
            } catch (Exception e2) {
                return FutureHelper.reject(e2);
            }
        }
    }

    IGatewayInvocation buildInvocation(GatewayRouteModel route, List<IGatewayInterceptor> interceptors) {
        IGatewayInvocation routeExecution = new GatewayRouteExecution(route, mappingProcessor);
        if (interceptors.isEmpty())
            return routeExecution;

        // 创建拦截器调用链
        return new InterceptedGatewayInvocation(interceptors, routeExecution);
    }

    /**
     * 检查流式模式是否启用
     */
    private boolean isStreamingEnabled(GatewayRouteModel route, IGatewayContext context) {
        if (route.getStreaming().getEnabled() != null) {
            Object result = route.getStreaming().getEnabled().invoke(context.getEvalScope());
            return ConvertHelper.toBoolean(result);
        }
        return true;
    }

    /**
     * 执行流式路由
     */
    private CompletionStage<ApiResponse<?>> executeStreaming(GatewayRouteModel route, ApiRequest<?> request,
                                                             IGatewayContext context, IGatewayInvocation invocation) {
        if (streamingProcessor == null) {
            throw new NopException(io.nop.gateway.GatewayErrors.ERR_GATEWAY_STREAMING_NOT_ENABLED)
                    .param("reason", "StreamingProcessor not configured");
        }
        return streamingProcessor.executeStreaming(route, request, context, invocation);
    }

    /**
     * 执行路由逻辑（invoke或forward）
     */
    private CompletionStage<ApiResponse<?>> executeRouteLogic(GatewayRouteModel route, ApiRequest<?> request,
                                                              IGatewayContext context) {
        // 优先使用 forward
        if (route.getForward() != null) {
            // forward 不再执行interceptor
            return forwardProcessor.forward(route.getForward(), request, context,
                    targetRoute -> execute(targetRoute, request, context, Collections.emptyList()));
        }

        // 使用 invoke
        if (route.getInvoke() != null) {
            return invokeProcessor.invoke(route, request, context);
        }

        throw new NopException(io.nop.gateway.GatewayErrors.ERR_GATEWAY_NO_RPC_SUPPORT)
                .param("reason", "Neither invoke nor forward is configured for route: " + route.getId());
    }
}
