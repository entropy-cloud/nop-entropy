/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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