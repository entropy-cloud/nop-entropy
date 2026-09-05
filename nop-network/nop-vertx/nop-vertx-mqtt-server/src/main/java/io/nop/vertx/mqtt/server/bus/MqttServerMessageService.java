/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.bus;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.FutureHelper;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.impl.VertxMqttServer;
import io.nop.vertx.mqtt.server.router.MqttTopicMatcher;
import io.vertx.mqtt.messages.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * MQTT 接入与进程内消息总线的双向桥：
 * sendAsync = 按 topic 过滤器匹配所有连接并逐一 publish（应用 → 设备）；
 * subscribe = 注册进程内监听，入站 publish 按同一匹配器分发（设备 → 应用）。
 * 契约见 ai-dev/design/nop-network/mqtt-messaging-design.md
 */
public class MqttServerMessageService implements IMessageService {
    static final Logger LOG = LoggerFactory.getLogger(MqttServerMessageService.class);

    private VertxMqttServer mqttServer;

    // 进程内订阅：filter → consumers（与 MQTT 连接订阅共用通配语义）
    private final Map<String, List<IMessageConsumer>> subscribers = new ConcurrentHashMap<>();

    // 入站分发钩子，注册到 VertxMqttServer
    private final BiConsumer<MqttPublishMessage, IMqttConnection> dispatcher = (msg, conn) -> dispatch(msg);

    @Inject
    public void setMqttServer(VertxMqttServer mqttServer) {
        this.mqttServer = mqttServer;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        if (mqttServer == null)
            throw new NopException(io.nop.vertx.mqtt.server.MqttErrors.ERR_MQTT_PUBLISH_FAIL)
                    .param(io.nop.vertx.mqtt.server.MqttErrors.ARG_TOPIC, topic);

        List<IMqttConnection> connections = mqttServer.getSessionManager().findConnections(topic);
        if (connections.isEmpty())
            return FutureHelper.success(null);

        List<CompletionStage<Void>> futures = new ArrayList<>(connections.size());
        for (IMqttConnection conn : connections) {
            futures.add(conn.sendAsync(topic, message, options));
        }
        return FutureHelper.waitAll(futures);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        if (mqttServer != null && subscribers.isEmpty()) {
            // 首个订阅时挂载入站分发
            mqttServer.addPublishListener(dispatcher);
        }
        subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);

        return new IMessageSubscription() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                if (cancelled)
                    return;
                cancelled = true;
                subscribers.computeIfPresent(topic, (k, list) -> {
                    list.remove(listener);
                    return list;
                });
                if (subscribers.isEmpty() && mqttServer != null) {
                    mqttServer.removePublishListener(dispatcher);
                }
            }

            @Override
            public void suspend() {
            }

            @Override
            public void resume() {
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public boolean isSuspended() {
                return false;
            }
        };
    }

    /**
     * 入站分发：按订阅过滤器匹配，payload 以 UTF-8 文本传递（二进制场景直接用 IMqttHandler）
     */
    private void dispatch(MqttPublishMessage msg) {
        String topic = msg.topicName();
        for (Map.Entry<String, List<IMessageConsumer>> entry : subscribers.entrySet()) {
            if (!MqttTopicMatcher.matches(entry.getKey(), topic))
                continue;
            String payload = msg.payload() != null ? msg.payload().toString() : null;
            for (IMessageConsumer consumer : new ArrayList<>(entry.getValue())) {
                try {
                    consumer.onMessage(topic, payload, null);
                } catch (Exception e) {
                    LOG.error("nop.mqtt.dispatch-fail:topic={}", topic, e);
                }
            }
        }
    }
}
