/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;

import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;

/**
 * Adapts nop-message's {@link IMessageService} to nop-stream's {@link SourceFunction}.
 * <p>
 * Subscribes to a message topic and emits received messages into the stream.
 * Blocks until cancelled.
 * <p>
 * Supports multi-partition subscription: when a {@code subtaskIndex} and {@code totalParallelism}
 * are provided, the function can use them to subscribe to topic partitions in a deterministic
 * manner (e.g., topic-partition-{subtaskIndex}). This enables parallel consumption across
 * multiple subtasks without overlap.
 */
public class MessageSourceFunction<T> implements SourceFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IMessageService messageService;
    private final String topic;

    /** Subtask index for partition-based subscription (-1 means no partitioning) */
    private final int subtaskIndex;
    /** Total parallelism for partition-based subscription */
    private final int totalParallelism;

    private volatile boolean running = true;
    private IMessageSubscription subscription;

    /**
     * Creates a MessageSourceFunction that subscribes to a single topic.
     */
    public MessageSourceFunction(IMessageService messageService, String topic) {
        this(messageService, topic, -1, 0);
    }

    /**
     * Creates a MessageSourceFunction with partition awareness.
     *
     * @param messageService   the message service to subscribe to
     * @param topic            the base topic name
     * @param subtaskIndex     this subtask's index (0-based)
     * @param totalParallelism total number of parallel subtasks
     */
    public MessageSourceFunction(IMessageService messageService, String topic,
                                  int subtaskIndex, int totalParallelism) {
        if (messageService == null) {
            throw new IllegalArgumentException("messageService must not be null");
        }
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("topic must not be null or empty");
        }
        if (subtaskIndex >= 0 && totalParallelism <= 0) {
            throw new IllegalArgumentException("totalParallelism must be positive when subtaskIndex is set");
        }
        if (subtaskIndex >= 0 && subtaskIndex >= totalParallelism) {
            throw new IllegalArgumentException("subtaskIndex must be < totalParallelism");
        }
        this.messageService = messageService;
        this.topic = topic;
        this.subtaskIndex = subtaskIndex;
        this.totalParallelism = totalParallelism;
    }

    /**
     * Returns the effective topic for this subtask. When partition-aware,
     * returns "{topic}-{subtaskIndex}"; otherwise returns the base topic.
     */
    public String getEffectiveTopic() {
        if (subtaskIndex >= 0) {
            return topic + "-" + subtaskIndex;
        }
        return topic;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(SourceContext<T> ctx) throws Exception {
        String effectiveTopic = getEffectiveTopic();
        subscription = messageService.subscribe(effectiveTopic, new IMessageConsumer() {
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

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.AT_LEAST_ONCE;
    }
}
