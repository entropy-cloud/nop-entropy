package io.nop.vertx.mqtt.server;

import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

public interface IMqttHandler {
    void onClose(IMqttConnection conn);

    void onPing();

    void onPublish(MqttPublishMessage msg, IMqttConnection conn);

    void onSubscribe(MqttSubscribeMessage msg, IMqttConnection conn);

    void onUnsubscribe(MqttUnsubscribeMessage msg, IMqttConnection conn);
}