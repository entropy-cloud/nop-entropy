/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.message;

import io.vertx.mqtt.messages.MqttPublishMessage;

public class MqttMessageBuilder {
    public static BinaryMqttMessageBean forPublish(MqttPublishMessage msg) {
        BinaryMqttMessageBean bean = new BinaryMqttMessageBean();
        bean.setMessageId(msg.messageId());
        bean.setTopic(msg.topicName());
        bean.setQosLevel(msg.qosLevel().value());
        bean.setWill(false);
        bean.setRetain(msg.isRetain());
        // vertx-mqtt 管线会在 handler 返回后释放原始 buffer，必须拷贝副本
        // Vert.x 5:Buffer 不再暴露 Netty ByteBuf(plan 2431 WI5),经字节数组拷贝
        bean.setBinaryPayload(io.netty.buffer.Unpooled.copiedBuffer(msg.payload().getBytes()));
        bean.setDup(msg.isDup());
        bean.setMqttProperties(msg.properties());
        return bean;
    }
}
