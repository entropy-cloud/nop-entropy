/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitEnumerator;

/**
 * WI2 (plan 2343 Phase 2): FLIP-27 form of the message source — the connector factory
 * now produces this class; the legacy {@code MessageSourceFunction} keeps its
 * programmatic API (direct constructions in tests) but is no longer produced by the
 * factory.
 *
 * <p>Semantics preserved (R3 baseline): AT_LEAST_ONCE declared, recovery=NONE — the
 * message backend carries delivery semantics, there is no offset checkpoint. Each
 * reader holds exactly one subscription token; real data distribution belongs to the
 * backend consumer group.
 */
public class MessageSource<T> implements Source<T, MessageTopicSplit, MessageSourceEnumeratorState> {

    private static final long serialVersionUID = 1L;

    private final io.nop.api.core.message.IMessageService messageService;
    private final String topic;
    private final Class<?> typeClass;

    public MessageSource(io.nop.api.core.message.IMessageService messageService, String topic,
                         Class<?> typeClass) {
        if (messageService == null) {
            throw new IllegalArgumentException("messageService is required");
        }
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("topic must not be null or empty");
        }
        this.messageService = messageService;
        this.topic = topic;
        this.typeClass = typeClass;
    }

    /** Payload-decode-free convenience form (parity with the legacy two-arg factory). */
    public MessageSource(io.nop.api.core.message.IMessageService messageService, String topic) {
        this(messageService, topic, null);
    }

    /** Test visibility: the constructor-injected service. */
    io.nop.api.core.message.IMessageService getMessageServiceForTest() {
        return messageService;
    }

    String getTopicForTest() {
        return topic;
    }

    Class<?> getTypeClassForTest() {
        return typeClass;
    }

    @Override
    public SplitEnumerator<MessageTopicSplit, MessageSourceEnumeratorState> createEnumerator() {
        return new MessageTopicSplitEnumerator(topic);
    }

    @Override
    public SplitEnumerator<MessageTopicSplit, MessageSourceEnumeratorState> restoreEnumerator(
            MessageSourceEnumeratorState state) {
        // tokens are re-issued per reader registration — no assignment truth to restore
        return new MessageTopicSplitEnumerator(topic);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SourceReader<T, MessageTopicSplit> createReader(SourceReaderContext readerContext) {
        return new MessageSourceReader<>(newEmbeddedFunction());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SourceFunction<T> newEmbeddedFunction() {
        Class rawTypeClass = typeClass;
        if (rawTypeClass != null) {
            return new MessageSourceFunction<>(messageService, topic, rawTypeClass);
        }
        return new MessageSourceFunction<>(messageService, topic);
    }

    @Override
    public SimpleVersionedSerializer<MessageSourceEnumeratorState> getEnumeratorStateSerializer() {
        return new EnumeratorStateSerializer();
    }

    @Override
    public SimpleVersionedSerializer<MessageTopicSplit> getSplitSerializer() {
        return new TopicSplitSerializer();
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    // ====================== Serializers ======================

    /** Encodes the token as {@code topic#readerIndex} text. Versioned at 1. */
    static final class TopicSplitSerializer implements SimpleVersionedSerializer<MessageTopicSplit> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(MessageTopicSplit split) throws IOException {
            if (split.getTopic().indexOf('#') >= 0) {
                throw new IOException("MessageTopicSplit topic contains the reserved '#' separator: "
                        + split.getTopic());
            }
            return split.splitId().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public MessageTopicSplit deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported MessageTopicSplit serializer version: " + version);
            }
            String id = new String(bytes, StandardCharsets.UTF_8);
            int hash = id.lastIndexOf('#');
            if (hash <= 0) {
                throw new IOException("Malformed MessageTopicSplit payload: " + id);
            }
            try {
                return new MessageTopicSplit(id.substring(0, hash),
                        Integer.parseInt(id.substring(hash + 1)));
            } catch (NumberFormatException e) {
                throw new IOException("Malformed MessageTopicSplit reader index: " + id, e);
            }
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }

    /** Encodes the informational token count as decimal text. Versioned at 1. */
    static final class EnumeratorStateSerializer
            implements SimpleVersionedSerializer<MessageSourceEnumeratorState> {

        private static final long serialVersionUID = 1L;
        private static final int VERSION = 1;

        @Override
        public byte[] serialize(MessageSourceEnumeratorState state) throws IOException {
            return String.valueOf(state.getDeliveredTokens()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public MessageSourceEnumeratorState deserialize(int version, byte[] bytes) throws IOException {
            if (version != VERSION) {
                throw new IOException("Unsupported MessageSourceEnumeratorState serializer version: "
                        + version);
            }
            try {
                return new MessageSourceEnumeratorState(
                        Integer.parseInt(new String(bytes, StandardCharsets.UTF_8)));
            } catch (NumberFormatException e) {
                throw new IOException("Malformed MessageSourceEnumeratorState payload", e);
            }
        }

        @Override
        public int getVersion() {
            return VERSION;
        }
    }
}
