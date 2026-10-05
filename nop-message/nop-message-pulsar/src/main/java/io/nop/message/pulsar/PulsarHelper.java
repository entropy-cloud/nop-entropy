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
import io.nop.api.core.message.MessageValueCodec;
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

        // WI24 (F-33): upstream properties are copied FIRST — framework metadata headers
        // are written last so an upstream same-name property can never override the real
        // topic/eventTime/publishTime/sequenceId (bizKey moved after the copy too: the
        // Pulsar message key is the authoritative biz key).
        Map<String, String> properties = message.getProperties();
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                apiMessage.setHeader(entry.getKey(), entry.getValue());
            }
        }

        String key = message.getKey();
        if (key != null) {
            ApiHeaders.setBizKey(apiMessage, key);
        }

        // WI24 (F-48/B1): fill-if-absent — a producer-supplied nop-id (the message RPC
        // client's correlation UUID rides Pulsar properties exactly like Kafka headers)
        // must survive for reply correlation; only an absent id is filled with the
        // Pulsar-native messageId.
        String msgId = String.valueOf(message.getMessageId());
        if (!apiMessage.hasHeader(ApiConstants.HEADER_ID)) {
            apiMessage.setHeader(ApiConstants.HEADER_ID, msgId);
        }

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

        // WI24 (F-31): STRING-schema producer — encode non-String payloads exactly like the
        // Kafka backend (shared codec), instead of handing a raw object to the String
        // schema serializer (send-time serialization failure).
        builder.value(encodeValue(message.getData()));
    }

    /**
     * Derives the reply (ack) topic for a request topic (MSG-F-04 fix). Fully-qualified
     * Pulsar names keep their domain + namespace and only prefix the final segment:
     * {@code persistent://public/default/orders} → {@code persistent://public/default/ack-orders}.
     * Short names map to {@code ack-<topic>}. The contract guarantees a valid derived topic
     * only for a valid input topic.
     */
    public static String deriveAckTopic(String topic) {
        if (topic != null && (topic.startsWith("persistent://") || topic.startsWith("non-persistent://"))) {
            int idx = topic.lastIndexOf('/');
            if (idx >= 0) {
                return topic.substring(0, idx + 1) + "ack-" + topic.substring(idx + 1);
            }
        }
        return "ack-" + topic;
    }

    static String encodeValue(Object value) {
        return MessageValueCodec.encodeValue(value);
    }
}
