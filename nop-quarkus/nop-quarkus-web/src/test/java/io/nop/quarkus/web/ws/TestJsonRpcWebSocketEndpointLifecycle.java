package io.nop.quarkus.web.ws;

import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.ws.IWebSocketHandler;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.2-001 (plan 2406): lowest-level WS session lifecycle coverage for the quarkus endpoint.
 * Mirrors {@code io.nop.spring.web.ws.TestJsonRpcWebSocketEndpointLifecycle}. No live container.
 */
public class TestJsonRpcWebSocketEndpointLifecycle {

    private Object savedProvider;

    @BeforeEach
    public void registerBeanContainer() throws Exception {
        savedProvider = providerField().get(null);
        BeanContainer.registerInstance(newContainer());
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
        IGraphQLEngine engine = proxy(IGraphQLEngine.class);
        IUserContextExtractor extractor = proxy(IUserContextExtractor.class);
        return (IBeanContainer) Proxy.newProxyInstance(
                TestJsonRpcWebSocketEndpointLifecycle.class.getClassLoader(),
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

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(TestJsonRpcWebSocketEndpointLifecycle.class.getClassLoader(),
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

    static class SessionStub {
        final Map<String, Object> userProperties = new HashMap<>();
        final List<Class<?>> messageHandlerTypes = new ArrayList<>();
        boolean closed;

        Session newSession() {
            return (Session) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{Session.class}, (p, method, args) -> {
                        switch (method.getName()) {
                            case "getUserProperties":
                                return userProperties;
                            case "addMessageHandler":
                                messageHandlerTypes.add((Class<?>) args[0]);
                                return null;
                            case "isOpen":
                                return !closed;
                            case "close":
                                closed = true;
                                return null;
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        EndpointConfig newConfig() {
            return (EndpointConfig) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{EndpointConfig.class}, (p, method, args) -> {
                        if (method.getName().equals("getUserProperties"))
                            return new HashMap<String, Object>();
                        return defaultFor(method.getReturnType());
                    });
        }
    }

    @Test
    public void testOnOpenRegistersHandlerAndOnCloseClosesSession() {
        JsonRpcWebSocketEndpoint endpoint = new JsonRpcWebSocketEndpoint();
        SessionStub stub = new SessionStub();
        Session session = stub.newSession();

        endpoint.onOpen(session, stub.newConfig());

        Object handler = stub.userProperties.get("graphql.handler");
        assertInstanceOf(IWebSocketHandler.class, handler, "onOpen must register the jsonrpc handler");
        assertTrue(stub.messageHandlerTypes.contains(String.class), "String message handler must be registered");

        endpoint.onClose(session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "bye"));
        assertTrue(stub.closed, "onClose must close the websocket session");
    }

    @Test
    public void testOnErrorDoesNotLeak() {
        JsonRpcWebSocketEndpoint endpoint = new JsonRpcWebSocketEndpoint();
        SessionStub stub = new SessionStub();
        Session session = stub.newSession();

        endpoint.onOpen(session, stub.newConfig());
        endpoint.onError(session, new RuntimeException("boom"));

        // onThrowable cancels the keep-alive timer; the session is not force-closed on error
        assertTrue(stub.userProperties.containsKey("graphql.handler"));
    }

    @Test
    public void testOnCloseWithoutHandlerIsNoop() {
        JsonRpcWebSocketEndpoint endpoint = new JsonRpcWebSocketEndpoint();
        SessionStub stub = new SessionStub();

        endpoint.onClose(stub.newSession(), new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "bye"));

        assertTrue(stub.userProperties.isEmpty());
    }
}
