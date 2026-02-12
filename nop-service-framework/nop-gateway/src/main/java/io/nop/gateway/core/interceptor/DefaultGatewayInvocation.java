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
import io.nop.core.context.IServiceContext;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayModel;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Default implementation of GatewayInvocation that manages the interceptor chain execution.
 * <p>
 * This class maintains the current state of interceptor processing and provides
 * the proceed() method to continue execution through the chain. When all interceptors
 * have been executed, it delegates to the route execution function to handle the
 * actual invoke or forward logic.
 */
public class DefaultGatewayInvocation implements GatewayInvocation {

    private final GatewayRouteModel route;
    private final List<IGatewayInterceptor> interceptors;
    private final Function<ApiRequest<?>, CompletionStage<ApiResponse<?>>> routeExecution;
    private int interceptorIndex = 0;

    /**
     * Creates a new DefaultGatewayInvocation with the specified route, interceptors, and execution function.
     *
     * @param route         the route model containing configuration
     * @param interceptors  the list of interceptors to execute
     * @param routeExecution the function that executes the actual route logic (invoke/forward)
     */
    public DefaultGatewayInvocation(GatewayRouteModel route,
                                   List<IGatewayInterceptor> interceptors,
                                   Function<ApiRequest<?>, CompletionStage<ApiResponse<?>>> routeExecution) {
        this.route = route;
        this.interceptors = interceptors;
        this.routeExecution = routeExecution;
    }

    @Override
    public CompletionStage<ApiResponse<?>> proceed(ApiRequest<?> request, IServiceContext svcCtx) {
        if (interceptorIndex < interceptors.size()) {
            // Execute the next interceptor
            IGatewayInterceptor interceptor = interceptors.get(interceptorIndex);
            interceptorIndex++;
            return executeInterceptor(interceptor, request, svcCtx);
        } else {
            // All interceptors executed, proceed with route execution
            return routeExecution.apply(request);
        }
    }

    /**
     * Proceeds to the next interceptor in the chain without calling onResponse.
     * <p>
     * This method is used by the route execution to proceed after invoking/forwarding,
     * without triggering onResponse callbacks again.
     *
     * @param request  the API request to process
     * @param svcCtx   the service context containing request/response information
     * @return a CompletionStage that will complete with the final ApiResponse
     */
    public CompletionStage<ApiResponse<?>> proceedWithoutResponse(ApiRequest<?> request, IServiceContext svcCtx) {
        return proceed(request, svcCtx);
    }

    @Override
    public GatewayRouteModel getRoute() {
        return route;
    }

    @Override
    public int getInterceptorIndex() {
        return interceptorIndex;
    }

    /**
     * Executes a single interceptor by calling its onRequest method and then proceeding.
     * <p>
     * This method wraps the interceptor execution in a try-catch block to handle
     * exceptions appropriately. If an interceptor throws an exception, it will be
     * propagated as a failed CompletionStage.
     * <p>
     * The onResponse callback is applied after the response is received, in reverse
     * order of interceptor execution.
     *
     * @param interceptor the interceptor to execute
     * @param request    the current API request
     * @param svcCtx     the service context
     * @return a CompletionStage that completes with the result of the interceptor execution
     */
    private CompletionStage<ApiResponse<?>> executeInterceptor(IGatewayInterceptor interceptor,
                                                               ApiRequest<?> request,
                                                               IServiceContext svcCtx) {
        try {
            // Call onRequest before proceeding
            interceptor.onRequest(request, this, svcCtx);
        } catch (Exception e) {
            // Handle onRequest exceptions
            try {
                interceptor.onError(e, this, svcCtx);
            } catch (Exception onErrorEx) {
                // If onError throws, use the new exception
                throw NopException.adapt(onErrorEx);
            }
            throw NopException.adapt(e);
        }

        // Proceed to next interceptor or route execution
        CompletionStage<ApiResponse<?>> future = proceed(request, svcCtx)
                .thenApply(response -> {
                    // Call onResponse after receiving response
                    try {
                        interceptor.onResponse(response, this, svcCtx);
                        return response;
                    } catch (Exception e) {
                        throw NopException.adapt(e);
                    }
                });

        // Handle exceptions separately to avoid type inference issues
        return future.exceptionally(e -> {
            // Call onError on exception
            try {
                interceptor.onError(e, this, svcCtx);
            } catch (Exception onErrorEx) {
                // If onError throws, use the new exception as the cause
                if (onErrorEx instanceof RuntimeException) {
                    throw (RuntimeException) onErrorEx;
                }
                throw new RuntimeException(onErrorEx);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        });
    }
}
