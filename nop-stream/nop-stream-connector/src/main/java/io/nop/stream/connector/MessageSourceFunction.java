/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.stream.core.common.functions.source.SourceFunction;

/**
 * Adapts nop-message's {@link IMessageService} to nop-stream's {@link SourceFunction}.
 * <p>
 * Subscribes to a message topic and emits received messages into the stream.
 * Blocks until cancelled.
 */
public class MessageSourceFunction<T> implements SourceFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IMessageService messageService;
    private final String topic;

    private volatile boolean running = true;
    private IMessageSubscription subscription;

    public MessageSourceFunction(IMessageService messageService, String topic) {
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
    @SuppressWarnings("unchecked")
    public void run(SourceContext<T> ctx) throws Exception {
        subscription = messageService.subscribe(topic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext context) {
                ctx.collect((T) msg);
                return null;
            }
        });

        while (running) {
            Thread.sleep(1000);
        }
    }

    @Override
    public void cancel() {
        running = false;
        if (subscription != null) {
            subscription.cancel();
        }
    }
}
