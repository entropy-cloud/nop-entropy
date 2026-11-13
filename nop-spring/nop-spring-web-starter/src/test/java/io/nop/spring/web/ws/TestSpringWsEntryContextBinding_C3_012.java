package io.nop.spring.web.ws;

import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.ws.IWebSocketHandler;
import io.nop.graphql.core.ws.JsonRpcWebSocketHandler;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 2.3-C3-012（plan 2499 Phase 3，裁定③内置统一接入形态）——第五入口 spring WS 断言。
 * <p>
 * {@code JsonRpcWebSocketEndpoint}（nop-spring-web-starter，本批零改码）的 executionService
 * lambda 经 {@code IGraphQLEngine.newRpcContext} 统一 chokepoint 构造订阅执行上下文；binder
 * 等价语义内置于该 default 方法（tenant/locale/timezone/callExpireTime 落盘 + 请求终态关闭，
 * 语义验证见 nop-graphql-core TestNewRpcContextBinder_C3_012）。本断言钉死：spring WS 消息
 * 路径经入口 lambda 到达 newRpcContext chokepoint——内置 binder 形态下 spring 站点自动
 * 覆盖，无需站点级改码。
 */
public class TestSpringWsEntryContextBinding_C3_012 {

    /** proxy engine 记录到的方法调用名 */
    static final List<String> ENGINE_CALLS = new CopyOnWriteArrayList<>();

    private Object savedProvider;

    @BeforeEach
    public void registerBeanContainer() throws Exception {
        savedProvider = providerField().get(null);
        BeanContainer.registerInstance(newContainer());
        ENGINE_CALLS.clear();
    }

    @AfterEach
    public void restoreBeanContainer() throws Exception {
        providerField().set(null, savedProvider);
    }

    private static Field providerField() throws NoSuchFieldException {
        Field f = BeanContainer.class.getDeclaredField("_provider");
        f.setAccessible(true);
        return f;
    }

    private static IBeanContainer newContainer() {
        IGraphQLEngine engine = recordingEngine();
        IUserContextExtractor extractor = proxy(IUserContextExtractor.class);
        return (IBeanContainer) Proxy.newProxyInstance(
                TestSpringWsEntryContextBinding_C3_012.class.getClassLoader(),
                new Class[]{IBeanContainer.class}, (p, method, args) -> {
                    switch (method.getName()) {
                        case "getBeanByType":
                        case "tryGetBeanByType":
                            return args[0] == IGraphQLEngine.class ? engine
                                    : args[0] == IUserContextExtractor.class ? extractor : null;
                        case "containsBean":
                            return false;
                        default:
                            return defaultFor(method.getReturnType());
                    }
                });
    }

    private static IGraphQLEngine recordingEngine() {
        return (IGraphQLEngine) Proxy.newProxyInstance(
                TestSpringWsEntryContextBinding_C3_012.class.getClassLoader(),
                new Class[]{IGraphQLEngine.class}, (p, method, args) -> {
                    ENGINE_CALLS.add(method.getName());
                    if ("subscribeRpc".equals(method.getName())) {
                        // 立即完成的空流：handler 订阅后走正常收尾
                        return (Flow.Publisher<ApiResponse<?>>) subscriber ->
                                subscriber.onSubscribe(new Flow.Subscription() {
                                    @Override
                                    public void request(long n) {
                                        subscriber.onComplete();
                                    }

                                    @Override
                                    public void cancel() {
                                    }
                                });
                    }
                    return defaultFor(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                TestSpringWsEntryContextBinding_C3_012.class.getClassLoader(),
                new Class[]{type}, (p, method, args) -> defaultFor(method.getReturnType()));
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }

    private static Session newSession(Map<String, Object> userProperties) {
        return (Session) Proxy.newProxyInstance(
                TestSpringWsEntryContextBinding_C3_012.class.getClassLoader(),
                new Class[]{Session.class}, (p, method, args) -> {
                    switch (method.getName()) {
                        case "getUserProperties":
                            return userProperties;
                        case "addMessageHandler":
                            return null;
                        case "isOpen":
                            return true;
                        default:
                            return defaultFor(method.getReturnType());
                    }
                });
    }

    private static EndpointConfig newConfig() {
        return (EndpointConfig) Proxy.newProxyInstance(
                TestSpringWsEntryContextBinding_C3_012.class.getClassLoader(),
                new Class[]{EndpointConfig.class}, (p, method, args) -> {
                    if ("getUserProperties".equals(method.getName()))
                        return new HashMap<String, Object>();
                    return defaultFor(method.getReturnType());
                });
    }

    /**
     * spring WS 消息路径（onOpen 注册的 handler.onMessage → 端点 executionService lambda）
     * 必须经 newRpcContext 统一 chokepoint——内置 binder 零改码覆盖的入口路由断言。
     */
    @Test
    public void testSpringWsMessagePathRoutesThroughNewRpcContextChokepoint() {
        JsonRpcWebSocketEndpoint endpoint = new JsonRpcWebSocketEndpoint();
        Map<String, Object> userProperties = new HashMap<>();
        Session session = newSession(userProperties);

        endpoint.onOpen(session, newConfig());

        Object handlerObj = userProperties.get("graphql.handler");
        assertTrue(handlerObj instanceof IWebSocketHandler,
                "onOpen must register the jsonrpc handler");

        JsonRpcWebSocketHandler handler = (JsonRpcWebSocketHandler) handlerObj;
        // JSON-RPC subscription 形态：携带 id 的 method 调用经 handleSubscription →
        // executionService.apply → engine.newRpcContext
        handler.onMessage("{\"id\":\"s1\",\"method\":\"Tree__subscribe\",\"params\":{}}");

        assertTrue(ENGINE_CALLS.contains("newRpcContext"),
                "spring WS entry lambda must construct the subscription context via the unified "
                        + "newRpcContext chokepoint (built-in binder), calls=" + ENGINE_CALLS);
    }

    @Test
    public void testOnCloseDoesNotLeak() {
        JsonRpcWebSocketEndpoint endpoint = new JsonRpcWebSocketEndpoint();
        Map<String, Object> userProperties = new HashMap<>();
        Session session = newSession(userProperties);

        endpoint.onOpen(session, newConfig());
        endpoint.onClose(session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "bye"));

        assertTrue(userProperties.containsKey("graphql.handler"));
    }
}
