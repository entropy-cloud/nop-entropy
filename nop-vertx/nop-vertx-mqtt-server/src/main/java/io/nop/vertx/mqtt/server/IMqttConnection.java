/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
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
