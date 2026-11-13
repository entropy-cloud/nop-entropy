package io.nop.vertx.mqtt.server.impl;

import io.nop.vertx.mqtt.server.IMqttConnection;

import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-008/C3-018/C6-020 (plan 2523, MA-01 三登记一工单): removeConnection 属主校验钉——
 * 旧连接迟到 close 不得抹掉新连接已登记订阅（下行路由静默失效面）。
 * 先行失败测试形态：缺陷态（无条件清）当前即红。
 */
public class TestMqttSessionOwnership_C1_008 {

    static IMqttConnection conn(String clientId) {
        return (IMqttConnection) Proxy.newProxyInstance(
                TestMqttSessionOwnership_C1_008.class.getClassLoader(),
                new Class<?>[]{IMqttConnection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getClientId":
                            return clientId;
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        default:
                            return null; // close() 等 void 方法
                    }
                });
    }

    @Test
    public void lateCloseOfStaleConnectionMustNotEraseNewSubscriptions() {
        MqttSessionManager manager = new MqttSessionManager();
        IMqttConnection connA = conn("dev-1");
        IMqttConnection connB = conn("dev-1");

        manager.addConnection(connA);
        manager.addConnection(connB);
        manager.subscribe("dev-1", List.of("cmd/+/status"));

        // 旧连接 A 迟到 close → removeConnection(A)：属主已是 B，订阅必须保留
        manager.removeConnection(connA);

        assertEquals(1, manager.findConnections("cmd/telemetry/status").size(),
                "MA-01: stale close must not erase the new connection's subscriptions"
                        + " (defect: unconditional remove wipes them — downlink routing silently dead)");
        assertTrue(manager.findConnections("cmd/telemetry/status").contains(connB));
    }

    @Test
    public void ownerCloseStillRemovesItsOwnSubscriptions() {
        MqttSessionManager manager = new MqttSessionManager();
        IMqttConnection conn = conn("dev-2");
        manager.addConnection(conn);
        manager.subscribe("dev-2", Collections.singletonList("alert/#"));

        manager.removeConnection(conn);

        assertTrue(manager.findConnections("alert/cpu").isEmpty(),
                "the owning connection's close must still clean up its own session+subscriptions");
    }

    @Test
    public void takeoverSequencePreservesSubscriptionsRegisteredBeforeTakeover() {
        // 窗口②形态：接管（订阅被清）后新连接重新 subscribe——旧连接迟到 close 不再破坏
        MqttSessionManager manager = new MqttSessionManager();
        IMqttConnection connA = conn("dev-3");
        manager.addConnection(connA);
        manager.subscribe("dev-3", List.of("a/#"));
        IMqttConnection connB = conn("dev-3");
        manager.addConnection(connB); // takeover clears
        manager.subscribe("dev-3", List.of("b/#"));

        manager.removeConnection(connA);

        assertEquals(1, manager.findConnections("b/x").size());
        assertEquals(0, manager.findConnections("a/x").size(),
                "only the new connection's subscription (b/#) survives — a/# was cleared by takeover itself");
    }
}
