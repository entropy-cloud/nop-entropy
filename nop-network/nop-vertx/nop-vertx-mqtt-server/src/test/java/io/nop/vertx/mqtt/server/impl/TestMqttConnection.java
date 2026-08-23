/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.impl;

import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.vertx.core.Handler;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMqttConnection {

    static class RecordingHandler implements IMqttHandler {
        int closeCount;

        @Override
        public void onClose(IMqttConnection conn) {
            closeCount++;
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
    static class RecordingEndpoint implements InvocationHandler {
        final List<String> invoked = new ArrayList<>();
        final List<Object[]> invokedArgs = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            invoked.add(method.getName());
            invokedArgs.add(args);

            Class<?> ret = method.getReturnType();
            if (ret == MqttEndpoint.class)
                return proxy;
            if (ret == boolean.class)
                return Boolean.FALSE;
            if (ret == int.class)
                return 0;
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

    @Test
    public void testEndpointAcceptedOnConnect() {
        RecordingEndpoint recorder = new RecordingEndpoint();
        MqttEndpoint endpoint = newEndpoint(recorder);
        RecordingHandler handler = new RecordingHandler();

        new MqttConnection(endpoint, handler);

        // MQTT 服务端必须调用 accept() 向客户端发送 CONNACK，否则客户端永远无法完成连接
        assertTrue(recorder.invoked("accept"), "MqttConnection must call endpoint.accept() on connect");

        // 修复不得丢失既有的断连/消息处理订阅逻辑
        assertTrue(recorder.invoked("disconnectHandler"));
        assertTrue(recorder.invoked("closeHandler"));
        assertTrue(recorder.invoked("publishHandler"));
        assertTrue(recorder.invoked("subscribeHandler"));
        assertTrue(recorder.invoked("unsubscribeHandler"));
    }

    @Test
    public void testCloseHandlerNotifiesMqttHandlerOnce() {
        RecordingEndpoint recorder = new RecordingEndpoint();
        MqttEndpoint endpoint = newEndpoint(recorder);
        RecordingHandler handler = new RecordingHandler();

        MqttConnection connection = new MqttConnection(endpoint, handler);

        Handler<Void> closeHandler = recorder.handlerArg("closeHandler");
        closeHandler.handle(null);
        // 重复触发只通知一次
        closeHandler.handle(null);
        assertEquals(1, handler.closeCount);

        Handler<Void> disconnectHandler = recorder.handlerArg("disconnectHandler");
        disconnectHandler.handle(null);
        assertEquals(1, handler.closeCount);
    }
}
