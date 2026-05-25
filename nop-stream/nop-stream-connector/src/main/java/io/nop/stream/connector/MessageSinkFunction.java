/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageService;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.SinkFunction;

/**
 * Adapts nop-message's {@link IMessageService} to nop-stream's {@link SinkFunction}.
 * <p>
 * Each consumed record is sent synchronously to the specified message topic.
 */
public class MessageSinkFunction<T> implements SinkFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IMessageService messageService;
    private final String topic;

    public MessageSinkFunction(IMessageService messageService, String topic) {
        if (messageService == null) {
            throw new IllegalArgumentException("messageService must not be null");
        }
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("topic must not be null or empty");
        }
        this.messageService = messageService;
        this.topic = topic;
    }

    @Override
    public void consume(T value) {
        messageService.send(topic, value);
    }

    @Override
    public SinkConsistencyCapability getSinkConsistency() {
        return SinkConsistencyCapability.AT_LEAST_ONCE;
    }
}
