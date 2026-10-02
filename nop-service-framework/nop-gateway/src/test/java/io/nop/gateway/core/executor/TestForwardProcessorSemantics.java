package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayForwardModel;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_FORWARD_STREAMING_INCOMPATIBLE;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_ROUTE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ForwardProcessor} 转发语义：目标路由解析（静态 routeId / dynamicRoute 覆盖）、
 * 未知路由 fail-loud、streaming 兼容检查、执行后恢复当前路由上下文（含异常路径）。
 * 纯单元测试：直接构造 GatewayModel / GatewayContextImpl，不启动容器。
 */
public class TestForwardProcessorSemantics {

    // ==================== 参数与路由解析 ====================

    /**
     * forward 配置缺失必须快速失败（fail-loud），不允许 NPE 扩散。
     */
    @Test
    public void testForwardNullModelFailsFast() {
        ForwardProcessor processor = new ForwardProcessor(model());
        assertThrows(IllegalArgumentException.class,
                () -> processor.forward(null, new ApiRequest<>(), context("src"), route -> null),
                "null forward model must fail fast");
    }

    /**
     * 目标 routeId 在模型中不存在时抛 ERR_GATEWAY_ROUTE_NOT_FOUND（fail-loud，非静默丢弃）。
     */
    @Test
    public void testForwardUnknownRouteThrowsRouteNotFound() {
        GatewayRouteModel src = route("src");
        src.setForward(forward("no-such-target"));
        GatewayModel model = model(src);

        NopException ex = assertThrows(NopException.class,
                () -> new ForwardProcessor(model).forward(src.getForward(), new ApiRequest<>(),
                        context("src"), route -> null));
        assertEquals(ERR_GATEWAY_ROUTE_NOT_FOUND.getErrorCode(), ex.getErrorCode(),
                "unknown target route must fail with route-not-found");
        assertEquals("no-such-target", ex.getParams().get("routeId"),
                "error must carry the missing routeId");
    }

    /**
     * 静态 routeId：转发执行目标路由，并在完成后恢复源路由上下文。
     */
    @Test
    public void testForwardExecutesTargetRouteAndRestoresContext() throws Exception {
        GatewayRouteModel src = route("src");
        src.setForward(forward("target"));
        GatewayModel model = model(src, route("target"));

        IGatewayContext ctx = context("src");
        List<String> executed = new ArrayList<>();

        ApiResponse<?> response = new ForwardProcessor(model)
                .forward(src.getForward(), new ApiRequest<>(), ctx, route -> {
                    executed.add(route.getId());
                    return CompletableFuture.completedFuture(ApiResponse.success("ok"));
                })
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(List.of("target"), executed, "routeExecutor must receive the target route");
        assertEquals("ok", response.getData());
        assertEquals("src", ctx.getCurrentRoute().getId(),
                "current route must be restored after forward completes");
    }

    /**
     * dynamicRoute 优先于静态 routeId：动态计算结果决定目标路由。
     */
    @Test
    public void testForwardDynamicRouteOverridesStaticRouteId() throws Exception {
        GatewayRouteModel src = route("src");
        GatewayForwardModel forward = forward("static-target");
        forward.setDynamicRoute((IEvalFunction) (thisObj, args, scope) -> "dynamic-target");
        src.setForward(forward);
        GatewayModel model = model(src, route("static-target"), route("dynamic-target"));

        List<String> executed = new ArrayList<>();
        new ForwardProcessor(model).forward(forward, new ApiRequest<>(), context("src"), route -> {
            executed.add(route.getId());
            return CompletableFuture.completedFuture(ApiResponse.success("ok"));
        }).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(List.of("dynamic-target"),
                executed, "dynamicRoute result must override the static routeId");
    }

    /**
     * 动态路由计算结果为 null 时按 route-not-found 处理（不静默回落到静态 routeId）。
     */
    @Test
    public void testForwardNullDynamicRouteThrowsRouteNotFound() {
        GatewayRouteModel src = route("src");
        GatewayForwardModel forward = forward("static-target");
        forward.setDynamicRoute((IEvalFunction) (thisObj, args, scope) -> null);
        src.setForward(forward);
        GatewayModel model = model(src, route("static-target"));

        NopException ex = assertThrows(NopException.class,
                () -> new ForwardProcessor(model).forward(forward, new ApiRequest<>(),
                        context("src"), route -> null));
        assertEquals(ERR_GATEWAY_ROUTE_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    // ==================== streaming 兼容 ====================

    /**
     * 源路由处于 streaming 模式而目标路由未启用 streaming 时必须拒绝转发。
     */
    @Test
    public void testForwardStreamingIncompatibleThrows() {
        GatewayRouteModel src = route("src");
        src.setForward(forward("plain-target"));
        GatewayRouteModel target = route("plain-target");
        GatewayModel model = model(src, target);

        GatewayContextImpl ctx = context("src");
        ctx.setStreamingMode(true);

        NopException ex = assertThrows(NopException.class,
                () -> new ForwardProcessor(model).forward(src.getForward(), new ApiRequest<>(),
                        ctx, route -> null));
        assertEquals(ERR_GATEWAY_FORWARD_STREAMING_INCOMPATIBLE.getErrorCode(), ex.getErrorCode(),
                "streaming source must not forward to non-streaming target");
    }

    /**
     * 目标路由也启用 streaming 时转发放行。
     */
    @Test
    public void testForwardStreamingCompatibleWhenTargetStreams() throws Exception {
        GatewayRouteModel src = route("src");
        src.setForward(forward("stream-target"));
        GatewayRouteModel target = route("stream-target");
        target.setStreaming(new GatewayStreamingModel());
        GatewayModel model = model(src, target);

        GatewayContextImpl ctx = context("src");
        ctx.setStreamingMode(true);

        List<String> executed = new ArrayList<>();
        new ForwardProcessor(model).forward(src.getForward(), new ApiRequest<>(), ctx, route -> {
            executed.add(route.getId());
            return CompletableFuture.completedFuture(ApiResponse.success("ok"));
        }).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(List.of("stream-target"), executed);
    }

    // ==================== 异常恢复 ====================

    /**
     * 路由执行器抛异常时同样必须恢复源路由上下文（异常向上传播）。
     */
    @Test
    public void testForwardRestoresContextOnExecutorFailure() {
        GatewayRouteModel src = route("src");
        src.setForward(forward("target"));
        GatewayModel model = model(src, route("target"));

        IGatewayContext ctx = context("src");

        assertThrows(IllegalStateException.class,
                () -> new ForwardProcessor(model).forward(src.getForward(), new ApiRequest<>(), ctx,
                        route -> {
                            throw new IllegalStateException("executor boom");
                        }));
        assertEquals("src", ctx.getCurrentRoute().getId(),
                "current route must be restored even when the executor fails");
    }

    /**
     * 异步执行失败（failed future）时上下文同样恢复。
     */
    @Test
    public void testForwardRestoresContextOnAsyncFailure() throws Exception {
        GatewayRouteModel src = route("src");
        src.setForward(forward("target"));
        GatewayModel model = model(src, route("target"));

        GatewayContextImpl ctx = context("src");

        CompletionStage<ApiResponse<?>> future = new ForwardProcessor(model)
                .forward(src.getForward(), new ApiRequest<>(), ctx,
                        route -> CompletableFuture.failedFuture(new IllegalStateException("async boom")));

        assertTrue(future.toCompletableFuture().isCompletedExceptionally(),
                "async failure must propagate");
        assertEquals("src", ctx.getCurrentRoute().getId(),
                "whenComplete must restore the saved route on failure");
    }

    // ==================== fixtures ====================

    private static GatewayModel model(GatewayRouteModel... routes) {
        GatewayModel model = new GatewayModel();
        model.setRoutes(new ArrayList<>(List.of(routes)));
        return model;
    }

    private static GatewayRouteModel route(String id) {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId(id);
        GatewayInvokeModel invoke = new GatewayInvokeModel();
        invoke.setServiceName("svc-" + id);
        invoke.setServiceMethod("method-" + id);
        route.setInvoke(invoke);
        return route;
    }

    private static GatewayForwardModel forward(String routeId) {
        GatewayForwardModel forward = new GatewayForwardModel();
        forward.setRouteId(routeId);
        return forward;
    }

    private static GatewayContextImpl context(String currentRouteId) {
        GatewayContextImpl ctx = new GatewayContextImpl();
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId(currentRouteId);
        ctx.setCurrentRoute(route);
        return ctx;
    }
}
