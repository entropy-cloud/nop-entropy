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
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitAssignmentProxy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 (plan 2343 Phase 2): the FLIP-27 message source semantics — serializer
 * round-trips, per-reader tokens, push→pull conversion through the bounded queue, the
 * P1-9 type-mismatch parity (typed task failure, never silent EOS), and the close
 * unsubscribe contract (R5-CON-04 parity).
 */
public class TestMessageSource {

    /** Fake message service: captures the subscription and lets the test push messages. */
    static class FakeMessageService implements IMessageService {
        final AtomicReference<IMessageConsumer> consumer = new AtomicReference<>();
        final AtomicInteger cancelled = new AtomicInteger();

        void push(Object message) {
            IMessageConsumer c = consumer.get();
            assertNotNull(c, "the source must have subscribed before messages are pushed");
            c.onMessage("fake-topic", message, null);
        }

        @Override
        public IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                              MessageSubscribeOptions options) {
            this.consumer.set(listener);
            return new IMessageSubscription() {
                @Override
                public void cancel() {
                    cancelled.incrementAndGet();
                }

                @Override
                public boolean isSuspended() {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get() > 0;
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
        public java.util.concurrent.CompletionStage<Void> sendAsync(String topic, Object message,
                                                                    MessageSendOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    /** The runner thread subscribes asynchronously — wait until it is live. */
    static void awaitSubscribed(FakeMessageService service) {
        long deadline = System.currentTimeMillis() + 5000;
        while (service.consumer.get() == null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    static SourceReaderContext readerContext() {
        return new SourceReaderContext(0, 1, new SplitAssignmentProxy() {
            @Override
            public void requestSplits(int subtaskIndex, Optional<Throwable> reason) {
            }

            @Override
            public void reportFinishedSplits(int subtaskIndex, List<String> finishedSplitIds) {
            }
        });
    }

    // ---------------- serializer round-trips ----------------

    @Test
    void splitSerializerRoundTripsToken() throws Exception {
        SimpleVersionedSerializer<MessageTopicSplit> serializer = new MessageSource.TopicSplitSerializer();
        byte[] bytes = serializer.serialize(new MessageTopicSplit("orders", 3));
        MessageTopicSplit restored = serializer.deserialize(serializer.getVersion(), bytes);
        assertEquals("orders", restored.getTopic());
        assertEquals(3, restored.getReaderIndex());
        assertEquals("orders#3", restored.splitId());
        assertThrows(Exception.class, () -> serializer.deserialize(9, bytes),
                "version mismatch must fail");
        assertThrows(Exception.class, () -> serializer.serialize(new MessageTopicSplit("or#ders", 0)),
                "a topic carrying the reserved separator must fail at serialize time");
    }

    @Test
    void enumeratorStateSerializerRoundTripsCount() throws Exception {
        SimpleVersionedSerializer<MessageSourceEnumeratorState> serializer =
                new MessageSource.EnumeratorStateSerializer();
        assertEquals(7, serializer.deserialize(1, serializer.serialize(
                new MessageSourceEnumeratorState(7))).getDeliveredTokens());
    }

    // ---------------- enumerator ----------------

    @Test
    void enumeratorIssuesOneTokenPerRegisteredReader() {
        MessageTopicSplitEnumerator enumerator = new MessageTopicSplitEnumerator("orders");
        List<List<MessageTopicSplit>> deliveries = new ArrayList<>();
        enumerator.start(new io.nop.stream.core.source.SplitEnumeratorContext<>(3,
                new io.nop.stream.core.source.AssignmentDeliveryService<MessageTopicSplit>() {
                    @Override
                    public void assignSplits(int subtaskIndex, List<MessageTopicSplit> splits) {
                        deliveries.add(splits);
                    }

                    @Override
                    public boolean isReaderRegistered(int subtaskIndex) {
                        return true;
                    }
                }));

        enumerator.addReader(0);
        enumerator.addReader(1);
        assertEquals(2, deliveries.size(), "one token per reader");
        assertEquals("orders#0", deliveries.get(0).get(0).splitId());
        assertEquals("orders#1", deliveries.get(1).get(0).splitId());
    }

    // ---------------- reader semantics ----------------

    @Test
    void readerConvertsPushToPullThroughTheQueue() throws Exception {
        FakeMessageService service = new FakeMessageService();
        MessageSourceReader<String> reader = new MessageSourceReader<>(
                new MessageSourceFunction<>(service, "orders"));
        reader.setContextForTest(readerContext());
        reader.start();
        reader.addSplits(List.of(new MessageTopicSplit("orders", 0)));
        awaitSubscribed(service);

        service.push("m1");
        service.push("m2");

        assertEquals(Optional.of("m1"), reader.pollNext());
        assertEquals(Optional.of("m2"), reader.pollNext());
        // idle poll: bounded wait, no records, not finished (UNBOUNDED)
        long start = System.currentTimeMillis();
        assertEquals(Optional.empty(), reader.pollNext());
        assertTrue(System.currentTimeMillis() - start >= 50,
                "the idle poll must block (bounded), not busy-spin");
        assertFalse(reader.isFinished());
        assertEquals(1, reader.snapshotState(1L).size(), "the token participates in checkpoints");
    }

    @Test
    void typeMismatchFailsTypedOnPollNeverSilentEos() throws Exception {
        FakeMessageService service = new FakeMessageService();
        MessageSourceReader<String> reader = new MessageSourceReader<>(
                new MessageSourceFunction<>(service, "orders", String.class));
        reader.setContextForTest(readerContext());
        reader.start();
        reader.addSplits(List.of(new MessageTopicSplit("orders", 0)));
        awaitSubscribed(service);

        service.push("ok");
        service.push(42); // payload type mismatch — P1-9 capture-and-rethrow

        assertEquals(Optional.of("ok"), reader.pollNext());
        // the runner died with the captured type mismatch; the next poll fails typed
        StreamException ex = assertThrows(StreamException.class, () -> reader.pollNext(),
                "a dead subscription must fail the task, never masquerade as EOS");
        // the P1-9 type mismatch passes through as its own typed error (StreamException
        // passthrough in asException) — surfaced, not swallowed
        assertTrue(String.valueOf(ex).contains("type-mismatch")
                        || String.valueOf(ex).contains("message source failed"),
                "the failure must be surfaced: " + ex);
    }

    @Test
    void sameTokenRedeliveryIsIdempotentAndDifferentTokenFailsTyped() throws Exception {
        FakeMessageService service = new FakeMessageService();
        MessageSourceReader<String> reader = new MessageSourceReader<>(
                new MessageSourceFunction<>(service, "orders"));
        reader.setContextForTest(readerContext());
        reader.start();
        reader.addSplits(List.of(new MessageTopicSplit("orders", 0)));
        reader.addSplits(List.of(new MessageTopicSplit("orders", 0))); // post-restore re-delivery

        assertThrows(StreamException.class,
                () -> reader.addSplits(List.of(new MessageTopicSplit("orders", 1))),
                "a second subscription token cannot be adopted — one subscription per reader");
    }

    @Test
    void closeUnsubscribesAndJoinsTheRunner() throws Exception {
        FakeMessageService service = new FakeMessageService();
        MessageSourceReader<String> reader = new MessageSourceReader<>(
                new MessageSourceFunction<>(service, "orders"));
        reader.setContextForTest(readerContext());
        reader.start();
        reader.addSplits(List.of(new MessageTopicSplit("orders", 0)));
        awaitSubscribed(service);
        assertNotNull(service.consumer.get(), "the embedded function must have subscribed");

        reader.close();

        assertTrue(service.cancelled.get() >= 1,
                "close must unsubscribe through the legacy cancel (R5-CON-04)");
        // post-close polls report empty (terminal), never records
        assertEquals(Optional.empty(), reader.pollNext());
    }
}
