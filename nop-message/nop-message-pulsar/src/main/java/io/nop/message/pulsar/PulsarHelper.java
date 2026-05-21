/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.json.JSON;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.util.ApiHeaders;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.TypedMessageBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PulsarHelper {
    public static ApiMessage buildApiMessage(Message<?> message) {
        Object value = message.getValue();
        ApiRequest<Object> apiMessage = new ApiRequest<>();
        apiMessage.setData(value);

        String key = message.getKey();
        if (key != null) {
            ApiHeaders.setBizKey(apiMessage, key);
        }

        String msgId = String.valueOf(message.getMessageId());
        apiMessage.setHeader(ApiConstants.HEADER_ID, msgId);

        String topicName = message.getTopicName();
        if (topicName != null) {
            apiMessage.setHeader(ApiConstants.HEADER_TOPIC, topicName);
        }

        long eventTime = message.getEventTime();
        if (eventTime > 0) {
            apiMessage.setHeader(ApiConstants.HEADER_EVENT_TIME, eventTime);
        }

        long publishTime = message.getPublishTime();
        if (publishTime > 0) {
            apiMessage.setHeader("nop-publish-time", publishTime);
        }

        Long sequenceId = message.getSequenceId();
        if (sequenceId != null) {
            apiMessage.setHeader("nop-sequence-id", sequenceId);
        }

        Map<String, String> properties = message.getProperties();
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                apiMessage.setHeader(entry.getKey(), entry.getValue());
            }
        }

        return apiMessage;
    }

    public static void buildPulsarMessage(TypedMessageBuilder builder,
                                          Object message, MessageSendOptions options) {
        if (message instanceof ApiMessage) {
            _buildPulsarMessage(builder, (ApiMessage) message);
        } else {
            builder.value(message);
        }
        if (options != null) {
            if (options.getDelay() > 0) {
                builder.deliverAfter(options.getDelay(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private static void _buildPulsarMessage(TypedMessageBuilder builder,
                                            ApiMessage message) {
        if (message.hasHeaders()) {
            for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
                String name = entry.getKey();
                if (ApiConstants.HEADER_BIZ_KEY.equals(name))
                    continue;
                String value = encodeValue(entry.getValue());
                if (value != null) {
                    builder.property(name, value);
                }
            }
        }

        String bizKey = ApiHeaders.getBizKey(message);
        if (bizKey != null) {
            builder.key(bizKey);
        }

        builder.value(message.getData());
    }

    static String encodeValue(Object value) {
        if (value == null)
            return null;

        if (value instanceof String)
            return (String) value;

        if (value instanceof Number || value instanceof Boolean)
            return value.toString();

        return JSON.stringify(value);
    }
}
