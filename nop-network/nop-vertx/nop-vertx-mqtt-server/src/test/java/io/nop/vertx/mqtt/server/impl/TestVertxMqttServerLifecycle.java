package io.nop.vertx.mqtt.server.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetServer;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-14 + C6-028（4.3-MA-14 / plan 2560 裁定 G）VertxMqttServer 启停两面载体：
 * ① listen 失败 → doStart 抛出（fail-fast，C10-007①；经 setVertx 注入缝注入 listen 必败 stub）；
 * ② doStop 等待 close 完成（带超时 await）+ sessionManager 两表清空。
 */
public class TestVertxMqttServerLifecycle {

    // ---------- stub 基础设施 ----------

    /**
     * NetServer stub：listen() 返回可配置 future，close() 返回可观测 future。
     * closeFuture 由调用方通过 CompletableFuture 控制——doStop 必须等它完成。
     */
    static NetServer newNetServer(AtomicReference<Future<NetServer>> listenResult,
                                  AtomicReference<CompletableFuture<Void>> closeResult) {
        return (NetServer) Proxy.newProxyInstance(NetServer.class.getClassLoader(),
                new Class<?>[]{NetServer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "listen":
                            Future<NetServer> f = listenResult.get();
                            return f != null ? f : Future.failedFuture(new IllegalStateException("no listen stub"));
                        case "close":
                            CompletableFuture<Void> c = closeResult.get();
                            return c != null
                                    ? Future.fromCompletionStage(c)
                                    : Future.succeededFuture();
                        case "actualPort":
                            return 18830;
                        case "isMetricsEnabled":
                            return Boolean.FALSE;
                        default:
                            Class<?> ret = method.getReturnType();
                            if (ret == boolean.class)
                                return Boolean.FALSE;
                            if (ret == int.class)
                                return 0;
                            return null;
                    }
                });
    }

    /**
     * Vertx stub：createNetServer 返回注入的 NetServer stub（MqttServerImpl 构造器
     * checkcast VertxInternal，故代理需同时实现该接口）。
     */
    static Vertx newVertxReturning(NetServer netServer) {
        return (Vertx) Proxy.newProxyInstance(Vertx.class.getClassLoader(),
                new Class<?>[]{Vertx.class, VertxInternal.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createNetServer"))
                        return netServer;
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class)
                        return Boolean.FALSE;
                    if (ret == int.class)
                        return 0;
                    return null;
                });
    }

    // ---------- 端点 stub（最小化，供 MqttConnection 构造/会话注册） ----------

    static class EndpointRecorder implements InvocationHandler {
        final List<String> invoked = new java.util.ArrayList<>();
        final List<Object[]> invokedArgs = new java.util.ArrayList<>();
        volatile boolean connected = true;
        volatile String clientId = "client-lc";

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            invoked.add(method.getName());
            invokedArgs.add(args);
            Class<?> ret = method.getReturnType();
            if (ret == MqttEndpoint.class)
                return proxy;
            if (method.getName().equals("clientIdentifier"))
                return clientId;
            if (ret == boolean.class)
                return method.getName().equals("isConnected") ? connected : Boolean.FALSE;
            if (ret == int.class)
                return 30;
            return null;
        }

        <T> Handler<T> handlerArg(String name) {
            for (int i = 0; i < invoked.size(); i++) {
                if (invoked.get(i).equals(name))
                    return (Handler<T>) invokedArgs.get(i)[0];
            }
            return null;
        }
    }

    static MqttEndpoint newEndpoint(EndpointRecorder recorder) {
        return (MqttEndpoint) Proxy.newProxyInstance(MqttEndpoint.class.getClassLoader(),
                new Class<?>[]{MqttEndpoint.class}, recorder);
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

    // ---------- 载体①：listen 失败 → doStart 抛出（C10-007①） ----------

    @Test
    public void testDoStartThrowsOnListenFailure() {
        VertxMqttServer server = new VertxMqttServer();
        server.setServerOptions(new MqttServerOptions());
        // setVertx 注入缝（裁定 G Phase 0）：注入 listen 必败 stub
        AtomicReference<Future<NetServer>> listenResult =
                new AtomicReference<>(Future.failedFuture(new RuntimeException("port in use")));
        // close stub 立即完成（doStart 失败路径会 close 已创建的 MqttServer 并触发 stop）
        CompletableFuture<Void> closeDone = new CompletableFuture<>();
        closeDone.complete(null);
        AtomicReference<CompletableFuture<Void>> closeResult = new AtomicReference<>(closeDone);
        server.setVertx(newVertxReturning(newNetServer(listenResult, closeResult)));

        NopException e = assertThrows(NopException.class, server::start);
        assertEquals("nop.err.mqtt.listen-fail", e.getErrorCode());

        // 失败后必须收敛半启动状态：触发 stop（mqttServer 置空、sessionManager 清空）
        assertEquals(io.nop.commons.service.ServiceStatus.CREATED, server.getStatus());
        assertTrue(server.getSessionManager().getConnection("any") == null);
    }

    // ---------- 载体②：doStop 等待 close 完成 + sessionManager 清空 ----------

    @Test
    public void testDoStopAwaitsCloseAndClearsSessionManager() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setServerOptions(new MqttServerOptions());

        // close future 延迟 200ms 完成——doStop 必须等待，不能立即返回
        CompletableFuture<Void> closeDeferred = new CompletableFuture<>();
        AtomicBoolean closeObservedDone = new AtomicBoolean(false);
        closeDeferred.whenComplete((v, err) -> closeObservedDone.set(true));
        CompletableFuture<Void> closeTrigger = new CompletableFuture<>();
        AtomicReference<CompletableFuture<Void>> closeResult = new AtomicReference<>();
        closeTrigger.whenComplete((v, err) -> {
            closeResult.set(closeDeferred);
            // 200ms 后完成 close future
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    // 测试辅助线程：中断仅提前完成 close future，无需恢复标志
                    Thread.currentThread().interrupt();
                }
                closeDeferred.complete(null);
            }).start();
        });

        AtomicReference<Future<NetServer>> listenResult = new AtomicReference<>();
        listenResult.set(Future.succeededFuture(
                newNetServer(listenResult, new AtomicReference<>(new CompletableFuture<>()))));
        server.setVertx(newVertxReturning(newNetServer(listenResult, closeResult)));

        server.start();
        assertTrue(server.isActive());

        // 注册会话 + 订阅（两表都非空）
        MqttSessionManager sessionManager = server.getSessionManager();
        EndpointRecorder recorder = new EndpointRecorder();
        MqttConnection conn = new MqttConnection(newEndpoint(recorder), new NoopHandler(), sessionManager);
        sessionManager.addConnection(conn);
        sessionManager.subscribe("client-lc", List.of("a/+", "b/#"));
        assertTrue(sessionManager.getConnection("client-lc") != null);

        // 触发 close（close future 200ms 后才完成）
        closeTrigger.complete(null);

        // stop 必须等待 close 完成后才返回
        server.stop();

        assertTrue(closeObservedDone.get(),
                "doStop must await mqttServer.close() completion (CountDownLatch + timeout await)");
        assertEquals(io.nop.commons.service.ServiceStatus.CREATED, server.getStatus());

        // sessionManager 两表同清（裁定 G：sessions + subscriptions）
        assertNull(sessionManager.getConnection("client-lc"), "sessions table must be cleared on stop");
        Map<String, IMqttConnection> sessions = sessionsOf(server);
        assertTrue(sessions.isEmpty(), "sessions table must be empty after stop");
        Map<String, ?> subscriptions = subscriptionsOf(server);
        assertTrue(subscriptions.isEmpty(), "subscriptions table must be empty after stop");

        conn.close();
    }

    static Map<String, IMqttConnection> sessionsOf(VertxMqttServer server) throws Exception {
        java.lang.reflect.Field field = VertxMqttServer.class.getDeclaredField("sessionManager");
        field.setAccessible(true);
        MqttSessionManager manager = (MqttSessionManager) field.get(server);
        java.lang.reflect.Field sessions = MqttSessionManager.class.getDeclaredField("sessions");
        sessions.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, IMqttConnection> map = (Map<String, IMqttConnection>) sessions.get(manager);
        return map;
    }

    @SuppressWarnings("unchecked")
    static Map<String, ?> subscriptionsOf(VertxMqttServer server) throws Exception {
        java.lang.reflect.Field field = VertxMqttServer.class.getDeclaredField("sessionManager");
        field.setAccessible(true);
        MqttSessionManager manager = (MqttSessionManager) field.get(server);
        java.lang.reflect.Field subscriptions = MqttSessionManager.class.getDeclaredField("subscriptions");
        subscriptions.setAccessible(true);
        return (Map<String, ?>) subscriptions.get(manager);
    }
}
