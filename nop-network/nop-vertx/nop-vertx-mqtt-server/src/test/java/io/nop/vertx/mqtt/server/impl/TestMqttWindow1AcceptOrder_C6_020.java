package io.nop.vertx.mqtt.server.impl;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-020 窗口①（plan 2523 R1 Major-F2 绿路径）：acceptEndpoint 集成级钉——
 * 顺序不变式「accept（CONNACK）时 sessions 已注册」+ accept 后注入 SUBSCRIBE→
 * 迟到 stale close 订阅保留（属主校验合成）+ 并发 subscribe×N 防过修钉。
 */
@Timeout(30)
public class TestMqttWindow1AcceptOrder_C6_020 {

    static IMqttHandler noopHandler() {
        return new IMqttHandler() {
            @Override
            public void onClose(IMqttConnection conn) {
            }

            @Override
            public void onPing() {
            }

            @Override
            public void onPublish(io.vertx.mqtt.messages.MqttPublishMessage msg, IMqttConnection conn) {
            }

            @Override
            public void onSubscribe(MqttSubscribeMessage msg, IMqttConnection conn) {
            }

            @Override
            public void onUnsubscribe(io.vertx.mqtt.messages.MqttUnsubscribeMessage msg, IMqttConnection conn) {
            }
        };
    }

    static IMqttConnection conn(String clientId) {
        return (IMqttConnection) java.lang.reflect.Proxy.newProxyInstance(
                TestMqttWindow1AcceptOrder_C6_020.class.getClassLoader(),
                new Class<?>[]{IMqttConnection.class},
                (proxy, method, args) -> {
                    if ("getClientId".equals(method.getName()))
                        return clientId;
                    if ("equals".equals(method.getName()))
                        return proxy == args[0];
                    if ("hashCode".equals(method.getName()))
                        return System.identityHashCode(proxy);
                    return null;
                });
    }

    @Test
    public void acceptOrderInvariantAndLateCloseOwnership() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(noopHandler());
        MqttSessionManager manager = server.getSessionManager();
        assertNotNull(manager);

        AtomicBoolean registeredAtAccept = new AtomicBoolean(false);
        AtomicInteger acceptCount = new AtomicInteger();
        List<Object> captured = new CopyOnWriteArrayList<>(); // io.vertx.core.Handler<MqttSubscribeMessage>

        MqttEndpoint endpoint = (MqttEndpoint) java.lang.reflect.Proxy.newProxyInstance(
                TestMqttWindow1AcceptOrder_C6_020.class.getClassLoader(),
                new Class<?>[]{MqttEndpoint.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "clientIdentifier":
                            return "dev-w1";
                        case "subscribeHandler":
                            captured.add(args[0]);
                            return proxy;
                        case "accept":
                            acceptCount.incrementAndGet();
                            registeredAtAccept.set(manager.getConnection("dev-w1") != null);
                            return null;
                        case "closeHandler":
                        case "exceptionHandler":
                        case "pingHandler":
                        case "publishHandler":
                        case "publishAcknowledgeHandler":
                        case "publishReceivedHandler":
                        case "publishReleaseHandler":
                        case "publishCompletionHandler":
                        case "unsubscribeHandler":
                        case "pongHandler":
                        case "disconnectHandler":
                            return proxy;
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            // 原生/装箱返回类型兜底（构造链未知方法调用防 NPE）
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class || rt == Boolean.class)
                                return false;
                            if (rt == int.class || rt == Integer.class)
                                return 0;
                            if (rt == long.class || rt == Long.class)
                                return 0L;
                            return null;
                    }
                });

        Method acceptEndpoint = VertxMqttServer.class.getDeclaredMethod("acceptEndpoint", MqttEndpoint.class);
        acceptEndpoint.setAccessible(true);
        AtomicReference<Exception> invokeError = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                acceptEndpoint.invoke(server, endpoint);
            } catch (Exception e) {
                invokeError.set(e);
            }
        });
        t.start();
        for (int i = 0; i < 200 && acceptCount.get() == 0; i++)
            Thread.sleep(10);
        assertTrue(acceptCount.get() > 0, "acceptEndpoint must reach accept() — invokeError="
                + invokeError.get());

        // 顺序不变式：accept（CONNACK）时 sessions 已注册
        assertTrue(registeredAtAccept.get(),
                "C6-020 窗口①：accept() 时会话必须已注册（先注册后 CONNACK）——"
                        + "否则 CONNACK 后客户端 SUBSCRIBE 先落表、随后 addConnection 接管清空");

        // accept 后注入 SUBSCRIBE（subscribeHandler 构造期已捕获）
        assertEquals(1, captured.size(), "subscribeHandler must be wired during construction");
        Object subscribeHandler = captured.get(0);

        MqttSubscribeMessage subMsg = (MqttSubscribeMessage) java.lang.reflect.Proxy.newProxyInstance(
                TestMqttWindow1AcceptOrder_C6_020.class.getClassLoader(),
                new Class<?>[]{MqttSubscribeMessage.class},
                (proxy, method, args) -> {
                    if ("topicSubscriptions".equals(method.getName())) {
                        List<MqttTopicSubscription> subs = new ArrayList<>();
                        for (String topic : new String[]{"cmd/w1/#"}) {
                            subs.add((MqttTopicSubscription) java.lang.reflect.Proxy.newProxyInstance(
                                    TestMqttWindow1AcceptOrder_C6_020.class.getClassLoader(),
                                    new Class<?>[]{MqttTopicSubscription.class},
                                    (p2, m2, a2) -> "topicName".equals(m2.getName()) ? topic : null));
                        }
                        return subs;
                    }
                    if ("messageId".equals(method.getName()))
                        return 1;
                    return null;
                });
        // io.vertx.core.Handler<MqttSubscribeMessage>.handle(msg)
        subscribeHandler.getClass().getMethod("handle", Object.class).invoke(subscribeHandler, subMsg);

        // 迟到 stale close：同 clientId 不同身份 → 属主校验保留订阅
        manager.removeConnection(conn("dev-w1"));

        assertEquals(1, manager.findConnections("cmd/w1/x").size(),
                "C6-020: SUBSCRIBE injected after accept must survive a stale close"
                        + " (window① + ownership combined)");
        t.join(5000);
    }

    /** 防过修钉：并发 subscribe×N（不同过滤器）全部落表，任一 filter 均命中且仅命中一次连接。 */
    @Test
    public void concurrentSubscribesAllLand() throws Exception {
        MqttSessionManager manager = new MqttSessionManager();
        IMqttConnection conn = conn("dev-n");
        manager.addConnection(conn);
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    manager.subscribe("dev-n", Collections.singletonList("f" + idx + "/#"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        IMqttConnection found = manager.findConnections("f3/anything").get(0);
        assertEquals(conn, found, "anti-overfit: every concurrently-registered filter must route");
    }
}
