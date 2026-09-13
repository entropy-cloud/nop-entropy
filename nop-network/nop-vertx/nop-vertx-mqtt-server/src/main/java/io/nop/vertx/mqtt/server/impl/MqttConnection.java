/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.vertx.mqtt.server.impl;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.time.CoreMetrics;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.nop.vertx.mqtt.server.MqttErrors;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

// refactor from jetlinks VertxMqttConnection

public class MqttConnection implements IMqttConnection {
    static final Logger LOG = LoggerFactory.getLogger(MqttConnection.class);

    private final MqttEndpoint endpoint;

    private long keepAliveTimeoutMs;
    private long lastPingTime = CoreMetrics.currentTimeMillis();
    private volatile boolean closed = false;

    private volatile boolean autoAckSub = true, autoAckUnSub = true, autoAckMsg = true;

    private final IMqttHandler handler;

    private volatile Runnable onCloseCallback;

    private final MqttSessionManager sessionManager;

    public MqttConnection(MqttEndpoint endpoint, IMqttHandler handler) {
        this(endpoint, handler, null);
    }

    public MqttConnection(MqttEndpoint endpoint, IMqttHandler handler, MqttSessionManager sessionManager) {
        this.endpoint = endpoint;
        this.handler = handler;
        this.sessionManager = sessionManager;
        this.keepAliveTimeoutMs = endpoint.keepAliveTimeSeconds() * 1000L;
        init();
    }

    void init() {
        this.endpoint
                .disconnectHandler(ignore -> this.complete())
                .closeHandler(ignore -> this.complete())
                .exceptionHandler(error -> {
                    LOG.error("nop.mqtt.error", error);
                })
                .pingHandler(ignore -> {
                    this.handlePing();
                })
                .publishHandler(msg -> {
                    ping();
                    if (autoAckMsg) {
                        ack(msg);
                    }

                    handler.onPublish(msg, this);
                })
                //QoS 1 PUBACK
                .publishAcknowledgeHandler(messageId -> {
                    ping();
                    LOG.debug("PUBACK mqtt[{}] message[{}]", getClientId(), messageId);
                })
                //QoS 2  PUBREC
                .publishReceivedHandler(messageId -> {
                    ping();
                    LOG.debug("PUBREC mqtt[{}] message[{}]", getClientId(), messageId);
                    endpoint.publishRelease(messageId);
                })
                //QoS 2  PUBREL
                .publishReleaseHandler(messageId -> {
                    ping();
                    LOG.debug("PUBREL mqtt[{}] message[{}]", getClientId(), messageId);
                    endpoint.publishComplete(messageId);
                })
                //QoS 2  PUBCOMP
                .publishCompletionHandler(messageId -> {
                    ping();
                    LOG.debug("PUBCOMP mqtt[{}] message[{}]", getClientId(), messageId);
                })
                .subscribeHandler(msg -> {
                    ping();

                    if (sessionManager != null) {
                        sessionManager.subscribe(getClientId(),
                                msg.topicSubscriptions().stream()
                                        .map(MqttTopicSubscription::topicName)
                                        .collect(Collectors.toList()));
                    }
                    if (autoAckSub) {
                        ack(msg);
                    }
                    handler.onSubscribe(msg, this);
                })
                .unsubscribeHandler(msg -> {
                    ping();

                    if (sessionManager != null) {
                        sessionManager.unsubscribe(getClientId(), msg.topics());
                    }
                    if (autoAckUnSub) {
                        ack(msg);
                    }
                    handler.onUnsubscribe(msg, this);
                });

        // 注册完所有 handler 后发送 CONNACK，完成与客户端的连接建立
        endpoint.accept();
    }

    public void ack(MqttMessage message) {
        if (message instanceof MqttPublishMessage) {
            ack((MqttPublishMessage) message);
        } else if (message instanceof MqttSubscribeMessage) {
            ack((MqttSubscribeMessage) message);
        } else if (message instanceof MqttUnsubscribeMessage) {
            ack((MqttUnsubscribeMessage) message);
        }
    }

    private void ack(MqttPublishMessage message) {
        if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
            LOG.debug("nop.mqtt.pub-ack:qos=QoS1,clientId={},messageId={}", getClientId(), message.messageId());
            endpoint.publishAcknowledge(message.messageId());
        } else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
            LOG.debug("nop.mqtt.pub-recv:qos=QoS2,clientId={},messageId={}", getClientId(), message.messageId());
            endpoint.publishReceived(message.messageId());
        }
    }

    private void ack(MqttSubscribeMessage message) {
        endpoint.subscribeAcknowledge(message.messageId(), message
                .topicSubscriptions()
                .stream()
                .map(MqttTopicSubscription::qualityOfService)
                .collect(Collectors.toList()));
    }

    private void ack(MqttUnsubscribeMessage message) {
        endpoint.unsubscribeAcknowledge(message.messageId());
    }

    @Override
    public String getClientId() {
        return endpoint.clientIdentifier();
    }

    @Override
    public void close() {
        endpoint.close();
    }

    @Override
    public boolean isClosed() {
        return !endpoint.isConnected();
    }

    public boolean isAlive() {
        // KeepAlive=0 按规范表示客户端无需保活、连接永不过期
        return endpoint.isConnected() && (keepAliveTimeoutMs <= 0
                || ((CoreMetrics.currentTimeMillis() - lastPingTime) < keepAliveTimeoutMs));
    }

    void ping() {
        lastPingTime = CoreMetrics.currentTimeMillis();
    }

    protected void handlePing() {
        ping();
        if (!endpoint.isAutoKeepAlive()) {
            endpoint.pong();
        }
        this.handler.onPing(this);
    }

    public void setOnCloseCallback(Runnable onCloseCallback) {
        this.onCloseCallback = onCloseCallback;
    }

    private void complete() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            this.handler.onClose(this);
        } finally {
            Runnable callback = this.onCloseCallback;
            if (callback != null) {
                callback.run();
            }
        }
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        // 下行推送：QoS1（AT_LEAST_ONCE），PUBACK 确认后 future 完成
        if (!endpoint.isConnected()) {
            throw new NopException(MqttErrors.ERR_MQTT_NOT_CONNECTED).param(MqttErrors.ARG_TOPIC, topic);
        }
        io.vertx.core.buffer.Buffer buffer = toBuffer(message);
        CompletableFuture<Void> ret = new CompletableFuture<>();
        endpoint.publish(topic, buffer, MqttQoS.AT_LEAST_ONCE, false, false)
                .onSuccess(id -> ret.complete(null))
                .onFailure(err -> ret.completeExceptionally(
                        new NopException(MqttErrors.ERR_MQTT_PUBLISH_FAIL, err).param(MqttErrors.ARG_TOPIC, topic)));
        return ret;
    }

    /**
     * payload 转换契约：String 按 UTF-8 文本，byte[] 原样，其余对象 JSON 序列化为文本
     */
    static io.vertx.core.buffer.Buffer toBuffer(Object message) {
        if (message instanceof io.vertx.core.buffer.Buffer)
            return (io.vertx.core.buffer.Buffer) message;
        if (message instanceof byte[])
            return io.vertx.core.buffer.Buffer.buffer((byte[]) message);
        if (message instanceof String)
            return io.vertx.core.buffer.Buffer.buffer((String) message, "UTF-8");
        return io.vertx.core.buffer.Buffer.buffer(JSON.stringify(message), "UTF-8");
    }
}
