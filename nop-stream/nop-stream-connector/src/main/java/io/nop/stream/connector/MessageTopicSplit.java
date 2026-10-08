/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.stream.core.source.SourceSplit;

import java.io.Serializable;
import java.util.Objects;

/**
 * WI2 (plan 2343 Phase 2): the assignment TOKEN of the message FLIP-27 source. The
 * message backend (consumer group) owns real data distribution — the token carries no
 * data-ownership semantics, it just wires one subscription per reader (parity with the
 * legacy factory, which never partitioned subscriptions). Each reader gets its own
 * uniquely-identified token; the splitId is derived from the base topic + reader index
 * so a post-restore re-delivery of the SAME token is recognizable and idempotent.
 */
public final class MessageTopicSplit implements SourceSplit, Serializable {

    private static final long serialVersionUID = 1L;

    private final String topic;
    private final int readerIndex;

    public MessageTopicSplit(String topic, int readerIndex) {
        this.topic = topic;
        this.readerIndex = readerIndex;
    }

    @Override
    public String splitId() {
        return topic + "#" + readerIndex;
    }

    public String getTopic() {
        return topic;
    }

    public int getReaderIndex() {
        return readerIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageTopicSplit)) return false;
        MessageTopicSplit that = (MessageTopicSplit) o;
        return readerIndex == that.readerIndex && Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, readerIndex);
    }

    @Override
    public String toString() {
        return "MessageTopicSplit{" + splitId() + "}";
    }
}
