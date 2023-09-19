package io.nop.vertx.mqtt.server.bus;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.vertx.mqtt.server.impl.VertxMqttServer;

import jakarta.inject.Inject;
import java.util.concurrent.CompletionStage;

public class MqttServerMessageService implements IMessageService {
    private VertxMqttServer mqttServer;

    @Inject
    public void setMqttServer(VertxMqttServer mqttServer) {
        this.mqttServer = mqttServer;
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return null;
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return null;
    }
}
