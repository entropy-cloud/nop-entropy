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
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayForwardModel;
import io.nop.gateway.model.GatewayModel;
import io.nop.gateway.model.GatewayRouteModel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.nop.gateway.GatewayErrors.ARG_ROUTE_ID;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_FORWARD_STREAMING_INCOMPATIBLE;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_ROUTE_NOT_FOUND;

/**
 * 处理路由转发逻辑
 *
 * <p>转发到已有的route，执行后返回到本route继续执行onResponse和responseMapping。</p>
 * <p>若源路由是streaming模式，则目标路由必须也为流式路由。</p>
 */
public class ForwardProcessor {
    private final GatewayModel model;

    public ForwardProcessor(GatewayModel model) {
        this.model = model;
    }

    /**
     * 执行路由转发
     *
     * @param forward 转发配置
     * @param request API请求
     * @param context 网关上下文
     * @param routeExecutor 路由执行器（用于执行目标路由）
     * @return 异步响应
     */
    public CompletionStage<ApiResponse<?>> forward(GatewayForwardModel forward, ApiRequest<?> request,
                                                   IGatewayContext context,
                                                   Function<GatewayRouteModel, CompletionStage<ApiResponse<?>>> routeExecutor) {
        if(forward == null)
            return null;

        // 1. 确定目标路由ID
        String targetRouteId = determineTargetRouteId(forward, request, context);
        if (targetRouteId == null) {
            throw new NopException(ERR_GATEWAY_ROUTE_NOT_FOUND)
                    .param(ARG_ROUTE_ID, "null");
        }

        // 2. 查找目标路由
        GatewayRouteModel targetRoute = findRouteById(targetRouteId);
        if (targetRoute == null) {
            throw new NopException(ERR_GATEWAY_ROUTE_NOT_FOUND)
                    .param(ARG_ROUTE_ID, targetRouteId);
        }

        // 3. 检查streaming兼容性
        checkStreamingCompatibility(context, targetRoute);

        // 4. 保存当前上下文状态
        GatewayRouteModel savedRoute = context.getCurrentRoute();

        // 5. 执行目标路由
        context.setCurrentRoute(targetRoute);

        // 6. 执行并恢复上下文状态
        return executeAndRestore(routeExecutor, targetRoute, savedRoute, context);
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<ApiResponse<?>> executeAndRestore(
            Function<GatewayRouteModel, CompletionStage<ApiResponse<?>>> routeExecutor,
            GatewayRouteModel targetRoute,
            GatewayRouteModel savedRoute,
            IGatewayContext context) {
        try {
            CompletionStage<ApiResponse<?>> result = routeExecutor.apply(targetRoute);
            return result.whenComplete((response, e) -> {
                context.setCurrentRoute(savedRoute);
            });
        } catch (Exception e) {
            context.setCurrentRoute(savedRoute);
            throw e;
        }
    }

    /**
     * 确定目标路由ID
     * 优先使用dynamicRoute动态计算，否则使用静态routeId
     */
    private String determineTargetRouteId(GatewayForwardModel forward, ApiRequest<?> request,
                                          IGatewayContext context) {
        if (forward.getDynamicRoute() != null) {
            // 动态计算路由ID
            IEvalScope scope = context.getEvalScope();
            Object result = forward.getDynamicRoute().call1(null, request, scope);
            return result != null ? result.toString() : null;
        }
        return forward.getRouteId();
    }

    /**
     * 根据ID查找路由
     */
    private GatewayRouteModel findRouteById(String routeId) {
        return model.getRoute(routeId);
    }

    /**
     * 检查streaming兼容性
     * 如果源路由是streaming模式，目标路由也必须是streaming模式
     */
    private void checkStreamingCompatibility(IGatewayContext context, GatewayRouteModel targetRoute) {
        if (context.isStreamingMode()) {
            boolean targetHasStreaming = targetRoute.getStreaming() != null;
            if (!targetHasStreaming) {
                throw new NopException(ERR_GATEWAY_FORWARD_STREAMING_INCOMPATIBLE)
                        .param("sourceRoute", context.getCurrentRoute())
                        .param("targetRouteId", targetRoute.getId());
            }
        }
    }
}
