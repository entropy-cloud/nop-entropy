/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

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
    private static final Logger LOG = LoggerFactory.getLogger(MessageSourceFunction.class);

    private final IMessageService messageService;
    private final String topic;
    private final Class<T> typeClass;

    /** Subtask index for partition-based subscription (-1 means no partitioning) */
    private final int subtaskIndex;
    /** Total parallelism for partition-based subscription */
    private final int totalParallelism;

    private volatile boolean running = true;
    private volatile boolean failed = false;
    private volatile IMessageSubscription subscription;
    private transient volatile CountDownLatch shutdownLatch = new CountDownLatch(1);

    /**
     * Creates a MessageSourceFunction that subscribes to a single topic.
     */
    public MessageSourceFunction(IMessageService messageService, String topic) {
        this(messageService, topic, null, -1, 0);
    }

    public MessageSourceFunction(IMessageService messageService, String topic, Class<T> typeClass) {
        this(messageService, topic, typeClass, -1, 0);
    }

    public MessageSourceFunction(IMessageService messageService, String topic,
                                  int subtaskIndex, int totalParallelism) {
        this(messageService, topic, null, subtaskIndex, totalParallelism);
    }

    public MessageSourceFunction(IMessageService messageService, String topic,
                                  Class<T> typeClass, int subtaskIndex, int totalParallelism) {
        if (messageService == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "messageService");
        }
        if (topic == null || topic.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "topic");
        }
        if (subtaskIndex >= 0 && totalParallelism <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "totalParallelism")
                    .param(ARG_DETAIL, "must be positive when subtaskIndex is set");
        }
        if (subtaskIndex >= 0 && subtaskIndex >= totalParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "subtaskIndex")
                    .param(ARG_DETAIL, "must be < totalParallelism");
        }
        this.messageService = messageService;
        this.topic = topic;
        this.typeClass = typeClass;
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
        if (shutdownLatch == null) {
            shutdownLatch = new CountDownLatch(1);
        }

        String effectiveTopic = getEffectiveTopic();
        subscription = messageService.subscribe(effectiveTopic, new IMessageConsumer() {
            @Override
            public Object onMessage(String t, Object msg, IMessageConsumeContext context) {
                if (typeClass != null && msg != null && !typeClass.isInstance(msg)) {
                    throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                            .param(ARG_EXPECTED_TYPE, typeClass.getName())
                            .param(ARG_ACTUAL_TYPE, msg.getClass().getName());
                }
                try {
                    ctx.collect((T) msg);
                } catch (Exception e) {
                    LOG.error("Failed to collect message from topic {}", effectiveTopic, e);
                    failed = true;
                    return null;
                }
                return null;
            }
        });

        while (running && !failed) {
            shutdownLatch.await(1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void cancel() {
        running = false;
        if (shutdownLatch != null) {
            shutdownLatch.countDown();
        }
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public SourceConsistencyCapability getSourceConsistency() {
        return SourceConsistencyCapability.AT_LEAST_ONCE;
    }
}
