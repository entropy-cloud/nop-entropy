/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * R-16 (runtime audit 2026-09-01): malformed wire payloads must be discarded
 * observably. The string codecs let JSON parse errors throw from
 * {@code fromWire} instead of honoring the documented "return null when the
 * message cannot be decoded" contract — the adapter previously called
 * {@code fromWire} bare, so the escape was backend-dependent (LocalMessageService
 * caught + dropped; a threaded backend could stall its dispatch thread). The
 * adapter now treats a throw exactly like an undecodable message: discard +
 * WARN. Pre-existing wire-codec tests were round-trip only; these are the first
 * malformed-input tests.
 */
class TestDataPlaneWireCodecMalformedInput {

    /** Stub backend that captures the adapted consumer so the test can deliver raw wire messages. */
    static class CapturingMessageService implements IMessageService {
        final AtomicReference<IMessageConsumer> capturedConsumer = new AtomicReference<>();

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
            capturedConsumer.set(listener);
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }
            };
        }

        @Override
        public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class CountingConsumer implements IMessageConsumer {
        final AtomicInteger deliveries = new AtomicInteger();
        final List<Object> envelopes = new CopyOnWriteArrayList<>();

        @Override
        public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext context) {
            deliveries.incrementAndGet();
            envelopes.add(message);
            return null;
        }
    }

    @Test
    void malformedJsonPayloadIsDiscardedWithoutThrowing() {
        CapturingMessageService backend = new CapturingMessageService();
        CountingConsumer inner = new CountingConsumer();
        DataPlaneMessageServiceAdapter adapter =
                new DataPlaneMessageServiceAdapter(backend, KafkaStringWireCodec.INSTANCE);
        adapter.subscribe("topic-bad", inner, null);
        IMessageConsumer adapted = backend.capturedConsumer.get();
        assertNotNull(adapted, "adapter must wrap the inner consumer");

        // Malformed JSON: the string codec itself throws from fromWire; the
        // adapter must convert that into the documented discard semantics.
        assertDoesNotThrow(() -> adapted.onMessage("topic-bad", "{not json at all", null),
                "a throwing codec must not propagate out of the adapter (backend-dependent escape)");
        assertEquals(0, inner.deliveries.get(), "the inner data-plane consumer must not see the bad payload");

        // Non-string, non-map, non-envelope payload: null-decode per contract.
        assertDoesNotThrow(() -> adapted.onMessage("topic-bad", Integer.valueOf(42), null));
        assertEquals(0, inner.deliveries.get());
    }

    @Test
    void validRoundTripStillDeliveredThroughAdapter() {
        CapturingMessageService backend = new CapturingMessageService();
        CountingConsumer inner = new CountingConsumer();
        DataPlaneMessageServiceAdapter adapter =
                new DataPlaneMessageServiceAdapter(backend, KafkaStringWireCodec.INSTANCE);
        adapter.subscribe("topic-ok", inner, null);
        IMessageConsumer adapted = backend.capturedConsumer.get();

        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                7L, StreamMessageEnvelope.TYPE_STREAM_RECORD, String.class.getName(), "\"hello\"");
        Object wire = KafkaStringWireCodec.INSTANCE.toWire(envelope);
        assertNotNull(wire);

        adapted.onMessage("topic-ok", wire, null);

        assertEquals(1, inner.deliveries.get(), "a valid wire message must still reach the inner consumer");
        Object delivered = inner.envelopes.get(0);
        assertNotNull(delivered);
        assertFalse(delivered instanceof String, "the inner consumer must receive the envelope, not the wire string");
    }

    /**
     * F-10: undecodable-message discard must be visible at WARN (was DEBUG —
     * invisible at default production log level, contradicting the
     * "explicit, observable, not silently swallowed" javadoc).
     */
    @Test
    void discardIsLoggedAtWarnLevel() {
        ch.qos.logback.classic.Logger adapterLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DataPlaneMessageServiceAdapter.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        adapterLogger.addAppender(appender);
        try {
            CapturingMessageService backend = new CapturingMessageService();
            CountingConsumer inner = new CountingConsumer();
            DataPlaneMessageServiceAdapter adapter =
                    new DataPlaneMessageServiceAdapter(backend, KafkaStringWireCodec.INSTANCE);
            adapter.subscribe("topic-warn", inner, null);
            IMessageConsumer adapted = backend.capturedConsumer.get();

            adapted.onMessage("topic-warn", "{bad json", null);
            adapted.onMessage("topic-warn", Integer.valueOf(7), null);

            List<ch.qos.logback.classic.spi.ILoggingEvent> warns = appender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .collect(java.util.stream.Collectors.toList());
            assertEquals(2, warns.size(),
                    "both the codec-throw discard and the null-decode discard must log at WARN");
        } finally {
            adapterLogger.detachAppender(appender);
        }
    }
}
