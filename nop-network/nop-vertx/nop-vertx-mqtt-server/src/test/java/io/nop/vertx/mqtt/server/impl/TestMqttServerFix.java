package io.nop.vertx.mqtt.server.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.nop.vertx.mqtt.server.auth.SimpleMqttAuthChecker;
import io.vertx.core.Handler;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMqttServerFix {

    @SuppressWarnings("unchecked")
    static class RecordingEndpoint implements InvocationHandler {
        final List<String> invoked = new ArrayList<>();
        final List<Object[]> invokedArgs = new ArrayList<>();
        volatile int keepAliveSeconds = 30;
        volatile boolean connected = true;
        volatile MqttAuth auth;
        volatile String clientId = "client-a";

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            invoked.add(method.getName());
            invokedArgs.add(args);
            Class<?> ret = method.getReturnType();
            if (ret == MqttEndpoint.class)
                return proxy;
            if (ret == boolean.class)
                return method.getName().equals("isConnected") ? connected : Boolean.FALSE;
            if (ret == int.class)
                return keepAliveSeconds;
            if (ret == MqttAuth.class)
                return auth;
            if (method.getName().equals("clientIdentifier"))
                return clientId;
            return null;
        }

        boolean invoked(String name) {
            return invoked.contains(name);
        }

        <T> Handler<T> handlerArg(String name) {
            for (int i = 0; i < invoked.size(); i++) {
                if (invoked.get(i).equals(name))
                    return (Handler<T>) invokedArgs.get(i)[0];
            }
            return null;
        }
    }

    static MqttEndpoint newEndpoint(RecordingEndpoint recorder) {
        return (MqttEndpoint) Proxy.newProxyInstance(MqttEndpoint.class.getClassLoader(),
                new Class<?>[]{MqttEndpoint.class}, recorder);
    }

    static MqttAuth newAuth(String username, String password) {
        return new MqttAuth(username, password);
    }

    static class NoopHandler implements IMqttHandler {
        @Override
        public void onClose(IMqttConnection conn) {
        }

        @Override
        public void onPing() {
        }

        @Override
        public void onPublish(MqttPublishMessage msg, IMqttConnection conn) {
        }

        @Override
        public void onSubscribe(MqttSubscribeMessage msg, IMqttConnection conn) {
        }

        @Override
        public void onUnsubscribe(MqttUnsubscribeMessage msg, IMqttConnection conn) {
        }
    }

    @Test
    public void testIsAliveWithZeroKeepAlive() {
        RecordingEndpoint recorder = new RecordingEndpoint();
        recorder.keepAliveSeconds = 0; // KeepAlive=0 表示无需保活
        MqttEndpoint endpoint = newEndpoint(recorder);

        MqttConnection connection = new MqttConnection(endpoint, new NoopHandler());
        try {
            // KeepAlive=0 的健康连接不应被判为失效
            assertTrue(connection.isAlive(), "keepAlive=0 must mean no keep-alive timeout");
        } finally {
            connection.close();
        }
    }

    @Test
    public void testSendAsyncFailsOnClosedConnection() {
        RecordingEndpoint recorder = new RecordingEndpoint();
        recorder.connected = false;
        MqttConnection connection = new MqttConnection(newEndpoint(recorder), new NoopHandler());
        try {
            // sendAsync 已实现：断开连接时显式失败（不再返回 null / 抛 UnsupportedOperation）
            NopException e = assertThrows(NopException.class,
                    () -> connection.sendAsync("topic", "msg", null));
            assertEquals("nop.err.mqtt.not-connected", e.getErrorCode());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testSimpleMqttAuthCheckerRejectsAnonymous() throws Exception {
        SimpleMqttAuthChecker checker = new SimpleMqttAuthChecker();
        Map<String, String> users = new java.util.HashMap<>();
        users.put("alice", "secret");
        checker.setUsers(users);

        // 匿名（null/null）凭据不得通过
        Boolean anonymous = checker.checkAuthAsync(null, null, null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertFalse(anonymous);

        Boolean wrongUser = checker.checkAuthAsync("bob", null, null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertFalse(wrongUser);

        Boolean ok = checker.checkAuthAsync("alice", "secret", null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(ok);
    }

    @Test
    public void testSessionTakeoverClosesOldConnection() {
        MqttSessionManager manager = new MqttSessionManager();

        RecordingEndpoint firstRecorder = new RecordingEndpoint();
        MqttConnection first = new MqttConnection(newEndpoint(firstRecorder), new NoopHandler());

        RecordingEndpoint secondRecorder = new RecordingEndpoint();
        MqttConnection second = new MqttConnection(newEndpoint(secondRecorder), new NoopHandler());

        manager.addConnection(first);
        // 相同 clientId 的新连接必须导致旧连接被关闭（会话接管），否则两条 TCP 连接同时存活
        manager.addConnection(second);
        assertTrue(firstRecorder.invoked("close"), "old connection must be closed on session takeover");
    }

    static Map<String, IMqttConnection> sessionsOf(VertxMqttServer server) throws Exception {
        java.lang.reflect.Field field = VertxMqttServer.class.getDeclaredField("sessionManager");
        field.setAccessible(true);
        MqttSessionManager manager = (MqttSessionManager) field.get(server);
        java.lang.reflect.Field sessions = MqttSessionManager.class.getDeclaredField("sessions");
        sessions.setAccessible(true);
        return (Map<String, IMqttConnection>) sessions.get(manager);
    }

    static void handleEndpoint(VertxMqttServer server, MqttEndpoint endpoint) throws Exception {
        java.lang.reflect.Method method = VertxMqttServer.class.getDeclaredMethod("handleEndpoint", MqttEndpoint.class);
        method.setAccessible(true);
        method.invoke(server, endpoint);
    }

    @Test
    public void testSessionRemovedOnConnectionClose() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(new NoopHandler());

        RecordingEndpoint recorder = new RecordingEndpoint();
        recorder.auth = null;
        MqttEndpoint endpoint = newEndpoint(recorder);

        handleEndpoint(server, endpoint);

        // 无 authChecker 时保持既有行为：accept 并注册会话
        assertTrue(recorder.invoked("accept"));
        Map<String, IMqttConnection> sessions = sessionsOf(server);
        assertTrue(sessions.containsKey("client-a"));

        // 连接断开后会话必须被移除，不能永久滞留
        Handler<Void> closeHandler = recorder.handlerArg("closeHandler");
        closeHandler.handle(null);
        assertFalse(sessions.containsKey("client-a"), "session must be removed on connection close");
    }

    @Test
    public void testAuthCheckerRejectsInvalidCredentials() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(new NoopHandler());
        // 注入总是拒绝的 authChecker
        java.lang.reflect.Field field = VertxMqttServer.class.getDeclaredField("authChecker");
        field.setAccessible(true);
        field.set(server, new io.nop.vertx.mqtt.server.auth.IMqttAuthChecker() {
            @Override
            public java.util.concurrent.CompletionStage<Boolean> checkAuthAsync(String userName, String password,
                                                                                IMqttConnection conn) {
                return CompletableFuture.completedFuture(false);
            }
        });

        RecordingEndpoint recorder = new RecordingEndpoint();
        recorder.auth = newAuth("alice", "wrong");
        MqttEndpoint endpoint = newEndpoint(recorder);

        handleEndpoint(server, endpoint);

        // 校验失败必须 reject，且不能 accept
        assertTrue(recorder.invoked("reject"), "endpoint.reject must be called when auth fails");
        assertFalse(recorder.invoked("accept"), "endpoint.accept must NOT be called when auth fails");
    }

    @Test
    public void testAuthCheckerAcceptsValidCredentials() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(new NoopHandler());
        java.lang.reflect.Field field = VertxMqttServer.class.getDeclaredField("authChecker");
        field.setAccessible(true);
        field.set(server, new io.nop.vertx.mqtt.server.auth.IMqttAuthChecker() {
            @Override
            public java.util.concurrent.CompletionStage<Boolean> checkAuthAsync(String userName, String password,
                                                                                IMqttConnection conn) {
                return CompletableFuture.completedFuture(true);
            }
        });

        RecordingEndpoint recorder = new RecordingEndpoint();
        recorder.auth = newAuth("alice", "secret");
        MqttEndpoint endpoint = newEndpoint(recorder);

        handleEndpoint(server, endpoint);

        assertTrue(recorder.invoked("accept"));
    }
}
