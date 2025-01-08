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
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.time.CoreMetrics;
import io.nop.vertx.mqtt.server.IMqttConnection;
import io.nop.vertx.mqtt.server.IMqttHandler;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

// refactor from jetlinks VertxMqttConnection

public class MqttConnection implements IMqttConnection {
    static final Logger LOG = LoggerFactory.getLogger(MqttConnection.class);

    private final MqttEndpoint endpoint;

    private long keepAliveTimeoutMs;
    private long lastPingTime = CoreMetrics.currentTimeMillis();
    private volatile boolean closed = false;

    private volatile boolean accepted = false, autoAckSub = true, autoAckUnSub = true, autoAckMsg = true;

    private final IMqttHandler handler;

    public MqttConnection(MqttEndpoint endpoint, IMqttHandler handler) {
        this.endpoint = endpoint;
        this.handler = handler;
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

                    if (autoAckSub) {
                        ack(msg);
                    }
                    handler.onSubscribe(msg, this);
                })
                .unsubscribeHandler(msg -> {
                    ping();

                    if (autoAckUnSub) {
                        ack(msg);
                    }
                    handler.onUnsubscribe(msg, this);
                });
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
        return endpoint.isConnected() && (keepAliveTimeoutMs < 0 || ((CoreMetrics.currentTimeMillis() - lastPingTime) < keepAliveTimeoutMs));
    }

    void ping() {
        lastPingTime = CoreMetrics.currentTimeMillis();
    }

    protected void handlePing() {
        ping();
        if (!endpoint.isAutoKeepAlive()) {
            endpoint.pong();
        }
        this.handler.onPing();
    }

    private void complete() {
        if (closed) {
            return;
        }
        closed = true;
        this.handler.onClose(this);
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return null;
    }
}
