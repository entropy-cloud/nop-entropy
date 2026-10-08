/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WI2 (plan 2343 Phase 2): enumerator of the message source — issues exactly one
 * subscription token per registered reader (up to the vertex parallelism). Tokens are
 * re-issued per run: each reader needs its own subscription on every (re)start, so
 * restore does not suppress delivery.
 */
public class MessageTopicSplitEnumerator
        implements SplitEnumerator<MessageTopicSplit, MessageSourceEnumeratorState> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(MessageTopicSplitEnumerator.class);

    private final String topic;

    private transient SplitEnumeratorContext<MessageTopicSplit> context;

    public MessageTopicSplitEnumerator(String topic) {
        this.topic = topic;
    }

    @Override
    public void start(SplitEnumeratorContext<MessageTopicSplit> context) {
        this.context = context;
        // no discovery phase — tokens are minted per registration
    }

    @Override
    public void addReader(int subtaskIndex) {
        deliverToken(subtaskIndex);
    }

    @Override
    public void handleSplitRequest(int subtaskIndex, java.util.Optional<Throwable> reason) {
        deliverToken(subtaskIndex);
    }

    private void deliverToken(int subtaskIndex) {
        if (context == null) {
            return;
        }
        MessageTopicSplit token = new MessageTopicSplit(topic, subtaskIndex);
        context.getDeliveryService().assignSplits(subtaskIndex, Collections.singletonList(token));
        LOG.info("MessageTopicSplitEnumerator delivered token {} to subtask {}", token.splitId(),
                subtaskIndex);
    }

    @Override
    public MessageSourceEnumeratorState snapshotState(long checkpointId) {
        return new MessageSourceEnumeratorState(context == null ? 0
                : context.getTotalParallelism());
    }

    @Override
    public void restoreState(MessageSourceEnumeratorState state) {
        // informational only — tokens are re-issued per registration
    }

    @Override
    public void close() {
        // no external resources
    }
}
