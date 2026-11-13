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
import io.nop.api.core.message.MessageCapability;
import io.nop.api.core.message.MessageOptionsValidator;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.EnumSet;
import java.util.Set;
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
    private static final Set<MessageCapability> CAPABILITIES = EnumSet.of(
            MessageCapability.SEND, MessageCapability.SUBSCRIBE);

    /** MQTT bridge is fire-and-forget: non-basic options are warn-once (D1), never silent. */
    private static final Set<MessageCapability> WARN_ONCE = EnumSet.of(
            MessageCapability.DELAYED_SEND, MessageCapability.SEND_TIMEOUT,
            MessageCapability.BATCH_CONSUME, MessageCapability.SEEK);

    @Override
    public Set<MessageCapability> getCapabilities() {
        return CAPABILITIES;
    }

    static final Logger LOG = LoggerFactory.getLogger(MqttServerMessageService.class);

    private VertxMqttServer mqttServer;

    // 进程内订阅：filter → consumers（与 MQTT 连接订阅共用通配语义）
    private final Map<String, List<IMessageConsumer>> subscribers = new ConcurrentHashMap<>();

    // 入站分发钩子，注册到 VertxMqttServer
    private final BiConsumer<MqttPublishMessage, IMqttConnection> dispatcher = (msg, conn) -> dispatch(msg);

    // MA-09 注册配对（4.3-MA-14 / plan 2560 裁定 B）：跨结构（subscribers 表 ↔
    // VertxMqttServer.publishListeners）注册配对不再借用 subscribers.isEmpty 判定——
    // CAS 标志保证 addPublishListener/removePublishListener 恰一次配对，
    // 消除并发首订阅时双挂载、末取消时漏摘除的竞态窗口。
    // registrationLock 串行化「订阅登记 + CAS 挂载」与「取消摘除 + CAS 摘除」两个
    // 跨结构迁移，封闭「并发取消/订阅交错导致分发钩子被摘除而订阅仍在」的丢失分发窗口。
    private final Object registrationLock = new Object();
    private final AtomicBoolean dispatcherRegistered = new AtomicBoolean(false);

    @Inject
    public void setMqttServer(VertxMqttServer mqttServer) {
        this.mqttServer = mqttServer;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        MessageOptionsValidator.validateSend(CAPABILITIES, options, "mqtt-server", WARN_ONCE);
        // MA-15 同步抛统一包络（4.3-MA-14 / plan 2560 裁定 H）：返回 CompletionStage 的方法
        // 不得同步抛——mqttServer 未装配时返回 failed future（ERR_MQTT_PUBLISH_FAIL）。
        if (mqttServer == null) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new NopException(io.nop.vertx.mqtt.server.MqttErrors.ERR_MQTT_PUBLISH_FAIL)
                    .param(io.nop.vertx.mqtt.server.MqttErrors.ARG_TOPIC, topic));
            return failed;
        }

        List<IMqttConnection> connections = mqttServer.getSessionManager().findConnections(topic);
        if (connections.isEmpty())
            return FutureHelper.success(null);

        List<CompletionStage<Void>> futures = new ArrayList<>(connections.size());
        for (IMqttConnection conn : connections) {
            // 扇出 per-conn 防护（裁定 N 接缝②：与 MA-09 同文件单 diff）——单个连接的
            // 同步失败不得中断其余连接的投递，也不得以同步抛穿透 CompletionStage 契约
            try {
                futures.add(conn.sendAsync(topic, message, options));
            } catch (Exception e) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new NopException(io.nop.vertx.mqtt.server.MqttErrors.ERR_MQTT_PUBLISH_FAIL, e)
                        .param(io.nop.vertx.mqtt.server.MqttErrors.ARG_TOPIC, topic));
                futures.add(failed);
            }
        }
        return FutureHelper.waitAll(futures);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        MessageOptionsValidator.validateSubscribe(CAPABILITIES, options, "mqtt-server", WARN_ONCE);
        synchronized (registrationLock) {
            if (mqttServer != null && dispatcherRegistered.compareAndSet(false, true)) {
                // 首个订阅时挂载入站分发（CAS 恰一次，裁定 B）
                mqttServer.addPublishListener(dispatcher);
            }
            subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(listener);
        }

        return new IMessageSubscription() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                if (cancelled)
                    return;
                cancelled = true;
                synchronized (registrationLock) {
                    removeSubscriber(topic, listener);
                    if (subscribers.isEmpty() && mqttServer != null
                            && dispatcherRegistered.compareAndSet(true, false)) {
                        mqttServer.removePublishListener(dispatcher);
                    }
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
     * MA-09（4.3-MA-14 / plan 2560 裁定 B）：两参真移除——监听器摘除后列表为空即整条
     * 摘除（computeIfPresent 返回 null 触发删键），消除「空列表死条目永久滞留
     * subscribers 表」的死码面（computeIfPresent 恒返回 list 的旧形态）。
     */
    private void removeSubscriber(String topic, IMessageConsumer listener) {
        subscribers.computeIfPresent(topic, (k, list) -> {
            list.remove(listener);
            return list.isEmpty() ? null : list;
        });
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
