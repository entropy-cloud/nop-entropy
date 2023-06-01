package io.nop.vertx.mqtt.server;

import io.nop.api.core.message.IMessageConsumeContext;
import io.vertx.mqtt.messages.MqttMessage;

public interface IMqttConnection extends IMessageConsumeContext, AutoCloseable {
    String getClientId();

    void close();

    boolean isClosed();

    boolean isAlive();

    void ack(MqttMessage message);
}
