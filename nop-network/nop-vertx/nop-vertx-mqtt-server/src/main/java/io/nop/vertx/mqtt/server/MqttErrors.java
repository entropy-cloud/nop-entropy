/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface MqttErrors {
    String ARG_TOPIC = "topic";

    ErrorCode ERR_MQTT_NOT_CONNECTED = define("nop.err.mqtt.not-connected", "MQTT连接已断开:topic={topic}");

    ErrorCode ERR_MQTT_PUBLISH_FAIL = define("nop.err.mqtt.publish-fail", "MQTT消息发布失败:topic={topic}");
}
