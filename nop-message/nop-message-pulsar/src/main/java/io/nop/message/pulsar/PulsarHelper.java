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
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.util.ApiHeaders;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.TypedMessageBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PulsarHelper {
    public static ApiMessage buildApiMessage(Message message) {
        message.getKey();
        message.getValue();
        message.getMessageId();
        message.getTopicName();
        message.getEventTime();
        message.getProperties();
        message.getPublishTime();
        message.getSequenceId();
        return null;
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
        if (message.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : message.getHeaders().entrySet()) {
                String value = encodeValue(entry.getValue());
                String name = entry.getKey();
                if (!ApiConstants.HEADER_BIZ_KEY.equals(name)) {
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
        return null;
    }
}
