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
import io.nop.core.context.IServiceContext;
import io.nop.gateway.model.GatewayRouteModel;

import java.util.concurrent.CompletionStage;

/**
 * GatewayInvocation represents the execution context for interceptor chain processing.
 * <p>
 * Interceptors receive an instance of this interface and use it to proceed to the
 * next interceptor in the chain. The invocation maintains the current route model
 * and the interceptor index to track progress through the chain.
 * <p>
 * Interceptors should call {@code proceed(request, svcCtx)} to continue execution
 * to the next interceptor. If they choose not to call proceed, they can return
 * a custom response to terminate the chain early.
 *
 * @see io.nop.gateway.model.GatewayInterceptorModel
 */
public interface GatewayInvocation {

    /**
     * Proceeds to the next interceptor in the chain.
     * <p>
     * This method should be called by each interceptor to continue the execution
     * to the next interceptor in the chain. If this is the last interceptor, it
     * will execute the route's invoke or forward logic.
     *
     * @param request  the API request to process (may be modified by interceptors)
     * @param svcCtx   the service context containing request/response information
     * @return a CompletionStage that will complete with the final ApiResponse
     * @throws io.nop.api.core.exceptions.NopException if processing fails
     */
    CompletionStage<ApiResponse<?>> proceed(ApiRequest<?> request, IServiceContext svcCtx);

    /**
     * Returns the route model associated with this invocation.
     * <p>
     * The route model contains configuration for the current route including
     * match conditions, invoke/forward configuration, request/response mappings,
     * and other route-specific settings.
     *
     * @return the GatewayRouteModel for the current route (never null)
     */
    GatewayRouteModel getRoute();

    /**
     * Returns the current interceptor index in the interceptor chain.
     * <p>
     * The index represents the position of the interceptor currently being executed.
     * This value is maintained by the invocation and starts from 0. It can be used
     * by interceptors to determine their position or for debugging purposes.
     *
     * @return the current interceptor index (0-based)
     */
    int getInterceptorIndex();
}
