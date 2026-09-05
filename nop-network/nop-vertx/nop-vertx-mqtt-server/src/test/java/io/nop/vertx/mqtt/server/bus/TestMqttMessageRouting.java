package io.nop.vertx.mqtt.server.bus;

import io.nop.api.core.exceptions.NopException;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.nop.vertx.mqtt.server.impl.MqttConnection;
import io.nop.vertx.mqtt.server.impl.VertxMqttServer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MQTT 下行推送 + 订阅路由 + 消息总线桥接回归。
 * 契约见 ai-dev/design/nop-network/mqtt-messaging-design.md
 */
public class TestMqttMessageRouting {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @SuppressWarnings("unchecked")
    static class RoutingEndpoint implements InvocationHandler {
        final List<String> invoked = new ArrayList<>();
        final List<Object[]> invokedArgs = new ArrayList<>();
        // publish 调用记录：topic/qos/payload
        final List<String> publishedTopics = new CopyOnWriteArrayList<>();
        final List<String> publishedPayloads = new CopyOnWriteArrayList<>();
        volatile boolean connected = true;
        volatile String clientId;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            invoked.add(method.getName());
            invokedArgs.add(args);
            Class<?> ret = method.getReturnType();
            if (method.getName().equals("publish")) {
                publishedTopics.add((String) args[0]);
                publishedPayloads.add(args[1] instanceof io.vertx.core.buffer.Buffer
                        ? ((io.vertx.core.buffer.Buffer) args[1]).toString() : String.valueOf(args[1]));
                return Future.succeededFuture(1);
            }
            if (method.getName().equals("clientIdentifier"))
                return clientId;
            if (method.getName().equals("isConnected"))
                return connected;
            if (ret == MqttEndpoint.class)
                return proxy;
            if (ret == boolean.class)
                return Boolean.FALSE;
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

    static MqttEndpoint newEndpoint(RoutingEndpoint recorder) {
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

    @SuppressWarnings("unchecked")
    static void handleEndpoint(VertxMqttServer server, MqttEndpoint endpoint) throws Exception {
        java.lang.reflect.Method m = VertxMqttServer.class.getDeclaredMethod("handleEndpoint", MqttEndpoint.class);
        m.setAccessible(true);
        m.invoke(server, endpoint);
    }

    @Test
    public void testConnectionSendAsyncPublishesQoS1() throws Exception {
        RoutingEndpoint recorder = new RoutingEndpoint();
        recorder.clientId = "c1";
        MqttConnection connection = new MqttConnection(newEndpoint(recorder), new NoopHandler());
        try {
            connection.sendAsync("a/b", "hello", null).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(List.of("a/b"), recorder.publishedTopics);
            assertEquals(List.of("hello"), recorder.publishedPayloads);

            // byte[] 原样、对象 JSON 序列化
            connection.sendAsync("a/c", new byte[]{1, 2}, null).toCompletableFuture().get(5, TimeUnit.SECONDS);
            connection.sendAsync("a/d", java.util.Map.of("name", "x"), null)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(3, recorder.publishedTopics.size());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testSendAsyncOnClosedConnectionFails() {
        RoutingEndpoint recorder = new RoutingEndpoint();
        recorder.clientId = "c1";
        recorder.connected = false;
        MqttConnection connection = new MqttConnection(newEndpoint(recorder), new NoopHandler());
        NopException e = assertThrows(NopException.class, () -> connection.sendAsync("t", "m", null));
        assertEquals("nop.err.mqtt.not-connected", e.getErrorCode());
    }

    @Test
    public void testMessageServiceRoutesToSubscribedConnections() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(new NoopHandler());

        RoutingEndpoint recA = new RoutingEndpoint();
        recA.clientId = "device-a";
        handleEndpoint(server, newEndpoint(recA));
        // device-a 订阅 a/+
        Handler<io.vertx.mqtt.messages.MqttSubscribeMessage> subA = recA.handlerArg("subscribeHandler");

        RoutingEndpoint recB = new RoutingEndpoint();
        recB.clientId = "device-b";
        handleEndpoint(server, newEndpoint(recB));

        MqttServerMessageService service = new MqttServerMessageService();
        service.setMqttServer(server);

        // 未订阅：无命中连接，正常完成（空广播）
        service.sendAsync("a/b", "nobody", null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(recA.publishedTopics.isEmpty());

        // device-a 订阅 a/+ 后命中
        io.vertx.mqtt.MqttTopicSubscription topicSub = (io.vertx.mqtt.MqttTopicSubscription)
                Proxy.newProxyInstance(io.vertx.mqtt.MqttTopicSubscription.class.getClassLoader(),
                        new Class<?>[]{io.vertx.mqtt.MqttTopicSubscription.class},
                        (proxy, method, args) -> method.getName().equals("topicName") ? "a/+" : null);
        io.vertx.mqtt.messages.MqttSubscribeMessage subMsg =
                (io.vertx.mqtt.messages.MqttSubscribeMessage) Proxy.newProxyInstance(
                        io.vertx.mqtt.messages.MqttSubscribeMessage.class.getClassLoader(),
                        new Class<?>[]{io.vertx.mqtt.messages.MqttSubscribeMessage.class},
                        (proxy, method, args) -> method.getName().equals("topicSubscriptions")
                                ? List.of(topicSub) : Integer.valueOf(1));
        subA.handle(subMsg);

        service.sendAsync("a/b", "hello-devices", null).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(List.of("a/b"), recA.publishedTopics);
        assertEquals(List.of("hello-devices"), recA.publishedPayloads);
        // device-b 未订阅 a/+，不接收
        assertTrue(recB.publishedTopics.isEmpty());
    }

    @Test
    public void testMessageServiceSubscribeDispatchesIncoming() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(new NoopHandler());

        RoutingEndpoint rec = new RoutingEndpoint();
        rec.clientId = "device-x";
        handleEndpoint(server, newEndpoint(rec));
        Handler<MqttPublishMessage> publishHandler = rec.handlerArg("publishHandler");

        MqttServerMessageService service = new MqttServerMessageService();
        service.setMqttServer(server);

        List<String> received = new CopyOnWriteArrayList<>();
        AtomicReference<String> receivedTopic = new AtomicReference<>();
        io.nop.api.core.message.IMessageSubscription subscription = service.subscribe("cmd/#",
                (topic, data, context) -> {
                    receivedTopic.set(topic);
                    received.add(String.valueOf(data));
                    return null;
                });

        // 模拟设备上行 publish cmd/open
        MqttPublishMessage msg = newPublishMessage("cmd/open", "payload-1");
        publishHandler.handle(msg);

        assertEquals("cmd/open", receivedTopic.get());
        assertEquals(List.of("payload-1"), received);

        // 取消订阅后不再分发
        subscription.cancel();
        received.clear();
        publishHandler.handle(newPublishMessage("cmd/close", "payload-2"));
        assertTrue(received.isEmpty());
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
}
