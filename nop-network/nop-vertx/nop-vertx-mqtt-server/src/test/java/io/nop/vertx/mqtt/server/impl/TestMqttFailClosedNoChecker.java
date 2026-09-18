package io.nop.vertx.mqtt.server.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-N2-1 回归：authChecker 缺席时必须拒绝连接（修复前 fail-open 全放行）。
 */
public class TestMqttFailClosedNoChecker {

    @Test
    public void testNullAuthCheckerRejectsEndpoint() throws Exception {
        VertxMqttServer server = new VertxMqttServer();
        server.setMqttHandler(new TestMqttServerFix.NoopHandler());
        // 不装配 authChecker

        AtomicBoolean rejected = new AtomicBoolean(false);
        io.vertx.mqtt.MqttEndpoint endpoint = (io.vertx.mqtt.MqttEndpoint) Proxy.newProxyInstance(
                io.vertx.mqtt.MqttEndpoint.class.getClassLoader(),
                new Class<?>[]{io.vertx.mqtt.MqttEndpoint.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("reject")) { rejected.set(true); return null; }
                    if (method.getName().equals("clientIdentifier")) return "anon-client";
                    return null;
                });

        java.lang.reflect.Method m = VertxMqttServer.class.getDeclaredMethod("handleEndpoint", io.vertx.mqtt.MqttEndpoint.class);
        m.setAccessible(true);
        m.invoke(server, endpoint);

        assertTrue(rejected.get(), "null authChecker must reject the MQTT connection (F-N2-1 fail-closed)");
    }
}
