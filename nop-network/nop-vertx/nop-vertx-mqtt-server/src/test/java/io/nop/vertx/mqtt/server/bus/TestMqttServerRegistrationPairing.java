package io.nop.vertx.mqtt.server.bus;

import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.nop.vertx.mqtt.server.impl.MqttConnection;
import io.nop.vertx.mqtt.server.impl.VertxMqttServer;
import io.vertx.core.Handler;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-09（4.3-MA-14 / plan 2560 裁定 B）注册配对载体：
 * ① CAS 注册标志——跨结构挂载/摘除恰一次，并发首订阅不双挂载（消除双份分发）；
 * ② 两参真移除——末取消后空列表死条目摘除（computeIfPresent 死码面消除）。
 */
public class TestMqttServerRegistrationPairing {

    static class EndpointRecorder implements InvocationHandler {
        final List<String> invoked = new java.util.ArrayList<>();
        final List<Object[]> invokedArgs = new java.util.ArrayList<>();
        volatile boolean connected = true;
        volatile String clientId;

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

        Handler<MqttPublishMessage> publishHandler() {
            for (int i = 0; i < invoked.size(); i++) {
                if (invoked.get(i).equals("publishHandler"))
                    return (Handler<MqttPublishMessage>) invokedArgs.get(i)[0];
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

    static MqttPublishMessage newPublishMessage(String topic, String payload) {
        return (MqttPublishMessage) Proxy.newProxyInstance(MqttPublishMessage.class.getClassLoader(),
                new Class<?>[]{MqttPublishMessage.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "topicName":
                        case "toString":
                            return topic;
                        case "payload":
                            return io.vertx.core.buffer.Buffer.buffer(payload);
                        case "qosLevel":
                            return io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
                        case "messageId":
                            return 1;
                        default:
                            return null;
                    }
                });
    }

    static VertxMqttServer serverWithEndpoint(EndpointRecorder recorder) throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(new NoopHandler());
        server.setAuthChecker((userName, password, conn) ->
                java.util.concurrent.CompletableFuture.completedFuture(true));
        java.lang.reflect.Method m = VertxMqttServer.class.getDeclaredMethod("handleEndpoint", MqttEndpoint.class);
        m.setAccessible(true);
        m.invoke(server, newEndpoint(recorder));
        return server;
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<Object>> subscribersOf(MqttServerMessageService service) throws Exception {
        Field field = MqttServerMessageService.class.getDeclaredField("subscribers");
        field.setAccessible(true);
        return (Map<String, List<Object>>) field.get(service);
    }

    @SuppressWarnings("unchecked")
    static List<Object> publishListenersOf(VertxMqttServer server) throws Exception {
        Field field = VertxMqttServer.class.getDeclaredField("publishListeners");
        field.setAccessible(true);
        return (List<Object>) field.get(server);
    }

    /** ① CAS 配对：并发首订阅不双挂载（双份分发消除） */
    @Test
    public void testConcurrentFirstSubscribeRegistersDispatcherOnce() throws Exception {
        VertxMqttServer server = serverWithEndpoint(new EndpointRecorder());
        MqttServerMessageService service = new MqttServerMessageService();
        service.setMqttServer(server);

        int n = 8;
        CyclicBarrier barrier = new CyclicBarrier(n);
        CountDownLatch done = new CountDownLatch(n);
        List<io.nop.api.core.message.IMessageSubscription> subs = new CopyOnWriteArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    barrier.await();
                    subs.add(service.subscribe("cmd/" + idx, (topic, data, ctx) -> null, null));
                } catch (Exception e) {
                    // ignore
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));

        // 挂载恰一次（CAS 标志防止并发首订阅双挂载）
        List<Object> listeners = publishListenersOf(server);
        assertEquals(1, listeners.size(), "dispatcher must be registered exactly once under concurrent first-subscribe");
        assertEquals(n, subscribersOf(service).size());
    }

    /** ①b 双份分发消除：同一入站 publish 对同一订阅者只投递一次 */
    @Test
    public void testIncomingPublishDispatchesExactlyOnce() throws Exception {
        EndpointRecorder rec = new EndpointRecorder();
        rec.clientId = "device-q";
        VertxMqttServer server = serverWithEndpoint(rec);
        Handler<MqttPublishMessage> publishHandler = rec.publishHandler();

        MqttServerMessageService service = new MqttServerMessageService();
        service.setMqttServer(server);

        List<String> received = new CopyOnWriteArrayList<>();
        service.subscribe("pair/#", (topic, data, ctx) -> {
            received.add(String.valueOf(data));
            return null;
        }, null);

        publishHandler.handle(newPublishMessage("pair/x", "p1"));
        assertEquals(List.of("p1"), received, "each incoming publish must dispatch exactly once per subscriber");
    }

    /** ② 两参真移除：末取消后空列表死条目摘除 + 分发钩子摘除 */
    @Test
    public void testCancelRemovesDeadEntryAndDispatcher() throws Exception {
        EndpointRecorder rec = new EndpointRecorder();
        rec.clientId = "device-r";
        VertxMqttServer server = serverWithEndpoint(rec);

        MqttServerMessageService service = new MqttServerMessageService();
        service.setMqttServer(server);

        AtomicReference<String> last = new AtomicReference<>();
        io.nop.api.core.message.IMessageSubscription sub =
                service.subscribe("dead/#", (topic, data, ctx) -> {
                    last.set(topic);
                    return null;
                }, null);

        // 分发在
        rec.publishHandler().handle(newPublishMessage("dead/t", "before"));
        assertEquals("dead/t", last.get());

        sub.cancel();

        // 空列表死条目必须整条摘除（两参真移除）
        Map<String, List<Object>> subscribers = subscribersOf(service);
        assertFalse(subscribers.containsKey("dead/#"),
                "empty-list dead entry must be removed (true removal, not computeIfPresent-keeps-list)");

        // 分发钩子也必须摘除
        List<Object> listeners = publishListenersOf(server);
        assertTrue(listeners.isEmpty(), "dispatcher must be removed when last subscriber cancels");
    }
}
