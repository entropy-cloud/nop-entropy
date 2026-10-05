/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.Acknowledge;
import io.nop.api.core.message.ConsumeLater;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import io.nop.api.core.message.SeekMode;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Component-level test for {@link KafkaConsumeTask}. Drives a single poll cycle with a
 * mocked {@link Consumer} and asserts the return-value branches of
 * {@code IMessageConsumer.onMessage} are dispatched correctly. Also covers seek policy
 * (not a stub) and start/stop lifecycle.
 */
class TestKafkaConsumeTask {

    private KafkaMessageService service;
    private Consumer<String, String> consumer;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        service = mock(KafkaMessageService.class);
        consumer = mock(Consumer.class);
        executor = Executors.newSingleThreadExecutor();
    }

    private MessageSubscriptionConfig config(IMessageConsumer msgConsumer, MessageSubscribeOptions options) {
        return new MessageSubscriptionConfig("test-topic", msgConsumer, options);
    }

    private ConsumerRecords<String, String> records(ConsumerRecord<String, String>... recs) {
        Map<TopicPartition, List<ConsumerRecord<String, String>>> map = new HashMap<>();
        for (ConsumerRecord<String, String> r : recs) {
            map.computeIfAbsent(new TopicPartition(r.topic(), r.partition()), k -> new ArrayList<>())
                    .add(r);
        }
        return new ConsumerRecords<>(map);
    }

    private ConsumerRecord<String, String> record(int partition, long offset, String value) {
        return new ConsumerRecord<>("test-topic", partition, offset, null, value);
    }

    @Test
    void nullResponseCommitsOffset() throws Exception {
        AtomicInteger invoked = new AtomicInteger();
        IMessageConsumer msgConsumer = (topic, message, context) -> {
            invoked.incrementAndGet();
            return null;
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 0, "a"), record(0, 1, "b")))
                .thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();

        assertEquals(2, invoked.get());
        verify(consumer).commitSync();
    }

    @Test
    void consumeLaterSeeksAndSkipsCommit() throws Exception {
        IMessageConsumer msgConsumer = (topic, message, context) -> new ConsumeLater(5L);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 5, "retry")))
                .thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();

        verify(consumer).seek(new TopicPartition("test-topic", 0), 5L);
        verify(consumer, never()).commitSync();
    }

    @Test
    void acknowledgeReplyCommitsOffset() throws Exception {
        IMessageConsumer msgConsumer = (topic, message, context) -> new Acknowledge("reply");
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 0, "req")))
                .thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();

        verify(consumer).commitSync();
    }

    @Test
    void nonNullResponseCommitsOffset() throws Exception {
        IMessageConsumer msgConsumer = (topic, message, context) -> "computed-reply";
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 0, "req")))
                .thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();

        verify(consumer).commitSync();
    }

    @Test
    void terminateConsumeCommitsWithoutReply() throws Exception {
        IMessageConsumer msgConsumer = (topic, message, context) ->
                new io.nop.api.core.message.TerminateConsume("poison");
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 3, "poison")))
                .thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();

        // Acknowledged (committed) and never redelivered (no seek back).
        verify(consumer).commitSync();
        verify(consumer, never()).seek(any(TopicPartition.class), any(long.class));
        // DLQ off (mock service resolves null): no republish attempt.
        verify(service, never()).sendAsync(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // WI21 (D6 layer two): application-level dead-letter republish
    // ------------------------------------------------------------------

    @Test
    void terminateConsumeRepublishesToDeadLetterWhenConfigured() throws Exception {
        when(service.resolveDeadLetterTopic("test-topic")).thenReturn("dlq-topic");
        when(service.sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        IMessageConsumer msgConsumer = (topic, message, context) ->
                new io.nop.api.core.message.TerminateConsume("poison");
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 3, "poison")))
                .thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();

        // The message was republished to the DLQ topic with its payload intact...
        org.mockito.ArgumentCaptor<Object> payload =
                org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(service).sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"),
                payload.capture(), any());
        org.junit.jupiter.api.Assertions.assertEquals("poison",
                ((io.nop.api.core.beans.ApiMessage) payload.getValue()).getData());
        // ...and the terminate semantics still hold: committed, never re-seeked.
        verify(consumer).commitSync();
        verify(consumer, never()).seek(any(TopicPartition.class), any(long.class));
    }

    @Test
    void deadLetterPublishFailureTakesTheRedeliveryPath() throws Exception {
        when(service.resolveDeadLetterTopic("test-topic")).thenReturn("dlq-topic");
        when(service.sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("dlq-broker-down")));
        IMessageConsumer msgConsumer = (topic, message, context) ->
                new io.nop.api.core.message.TerminateConsume("poison");
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 3, "poison")));

        // At-least-once: a failed DLQ publish must NOT commit — the record is re-seeked and
        // the failure escapes to the run loop.
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, task::pollAndConsume);
        verify(consumer, never()).commitSync();
        verify(consumer).seek(new TopicPartition("test-topic", 0), 3L);
    }

    // ------------------------------------------------------------------
    // WI22: retry cap (failure endpoint) and escalating backoff
    // ------------------------------------------------------------------

    @Test
    void computeBackoffEscalatesAndCaps() {
        assertEquals(1000L, KafkaConsumeTask.computeBackoffMs(1000, 30000, 0));
        assertEquals(2000L, KafkaConsumeTask.computeBackoffMs(1000, 30000, 1));
        assertEquals(4000L, KafkaConsumeTask.computeBackoffMs(1000, 30000, 2));
        assertEquals(30000L, KafkaConsumeTask.computeBackoffMs(1000, 30000, 5));
        assertEquals(30000L, KafkaConsumeTask.computeBackoffMs(1000, 30000, 50));
    }

    @Test
    void consumeAttemptCapTriggersFailureEndpoint() throws Exception {
        when(service.maxConsumeAttempts()).thenReturn(2); // DLQ off: resolveDeadLetterTopic → null
        java.util.List<Integer> endpointAttempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        RuntimeException boom = new IllegalStateException("handler-boom");
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                throw boom;
            }

            @Override
            public void onConsumeFailure(io.nop.api.core.message.ConsumeFailure failure) {
                endpointAttempts.add(failure.getAttempt());
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler, options), 100L, 100L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(record(0, 3, "poison")));

        // Rounds 1-2: within the budget — seek back and rethrow (normal redelivery).
        for (int i = 0; i < 2; i++) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    task::pollAndConsume);
        }
        verify(consumer, times(2)).seek(new TopicPartition("test-topic", 0), 3L);
        verify(consumer, never()).commitSync();

        // Round 3: attempt 3 > cap 2 → failure endpoint: no throw, no seek, the record
        // leaves the loop and is committed with the round; the callback sees attempt=3.
        task.pollAndConsume();
        verify(consumer, times(2)).seek(new TopicPartition("test-topic", 0), 3L);
        verify(consumer).commitSync();
        assertEquals(java.util.Collections.singletonList(3), endpointAttempts);

        // Budget cleared: the next failure starts fresh (attempt 1 would retry, not endpoint).
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::pollAndConsume);
        verify(consumer, times(3)).seek(new TopicPartition("test-topic", 0), 3L);
    }

    @Test
    void batchAttemptCapEndpointsFailedRecordAndCommitsBoundary() throws Exception {
        when(service.maxConsumeAttempts()).thenReturn(1);
        IMessageConsumer msgConsumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                List<Object> responses = new ArrayList<>();
                // Record 0's response fails → progress=0, attempt bumps each round.
                responses.add(java.util.concurrent.CompletableFuture
                        .failedFuture(new IllegalStateException("handler-boom")));
                responses.add(null);
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);
        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 3, "a"), record(0, 4, "b")));

        // Round 1: attempt 1 (not > cap 1) → normal rewind to the failed record.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::batchConsume);
        verify(consumer, times(1)).seek(new TopicPartition("test-topic", 0), 3L);

        // Round 2: attempt 2 > cap 1 → endpoint: the boundary (record0 offset+1 = 4) is
        // committed explicitly, then the TAIL is rewound (record1 offset 4), never record 0.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::batchConsume);
        org.mockito.ArgumentCaptor<Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>> committed =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(consumer).commitSync(committed.capture());
        org.junit.jupiter.api.Assertions.assertEquals(4L,
                committed.getValue().get(new TopicPartition("test-topic", 0)).offset());
        verify(consumer, times(1)).seek(new TopicPartition("test-topic", 0), 3L); // unchanged
        verify(consumer, times(1)).seek(new TopicPartition("test-topic", 0), 4L);
    }

    @Test
    void batchEndpointPublishFailureFallsBackToFullRewind() throws Exception {
        when(service.maxConsumeAttempts()).thenReturn(1);
        when(service.resolveDeadLetterTopic("test-topic")).thenReturn("dlq-topic");
        when(service.sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("dlq-down")));
        IMessageConsumer msgConsumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                List<Object> responses = new ArrayList<>();
                // Record 0's response fails → progress=0 (dispatched) → the attempt bumps
                // and the endpoint branch is reachable (a handler throw inside
                // onMessageBatch would be framework-grade with progress=-1 instead).
                responses.add(java.util.concurrent.CompletableFuture
                        .failedFuture(new IllegalStateException("handler-boom")));
                responses.add(null);
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);
        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 3, "a"), record(0, 4, "b")));

        // Round 1: attempt 1 (not > cap 1) → normal rewind to the failed record.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::batchConsume);
        verify(consumer, times(1)).seek(new TopicPartition("test-topic", 0), 3L);
        // Round 2: attempt 2 > cap 1 → the endpoint tries the DLQ publish, which FAILS →
        // fall back to the normal rewind(progress) path (never a silent drop, never a
        // boundary commit).
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::batchConsume);
        verify(consumer, times(2)).seek(new TopicPartition("test-topic", 0), 3L);
        verify(consumer, never()).commitSync(any(java.util.Map.class));
    }

    @Test
    void singleEndpointRepublishesToDeadLetterWhenConfigured() throws Exception {
        when(service.maxConsumeAttempts()).thenReturn(1);
        when(service.resolveDeadLetterTopic("test-topic")).thenReturn("dlq-topic");
        when(service.sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        IMessageConsumer msgConsumer = (topic, message, context) -> {
            throw new IllegalStateException("handler-boom");
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(record(0, 3, "poison")));

        // Round 1: attempt 1 (not > cap 1) → normal redelivery.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::pollAndConsume);
        // Round 2: attempt 2 > cap 1 → the endpoint republishes to the DLQ topic (the WI21
        // mechanism is reused, not reimplemented) and commits the round. Round 1's single
        // redelivery seek is the only seek — the endpoint record is NOT re-seeked.
        task.pollAndConsume();
        verify(service).sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any());
        verify(consumer).commitSync();
        verify(consumer, times(1)).seek(new TopicPartition("test-topic", 0), 3L);
    }

    @Test
    void singleEndpointPublishFailureFallsBackToSeekAndRethrow() throws Exception {
        when(service.maxConsumeAttempts()).thenReturn(1);
        when(service.resolveDeadLetterTopic("test-topic")).thenReturn("dlq-topic");
        when(service.sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("dlq-down")));
        IMessageConsumer msgConsumer = (topic, message, context) -> {
            throw new IllegalStateException("handler-boom");
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(record(0, 3, "poison")));

        // Round 1: within budget → seek + rethrow.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::pollAndConsume);
        // Round 2: poisoned but the DLQ publish fails → seek + rethrow (at-least-once, the
        // record is NOT silently dropped by the failed endpoint).
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                task::pollAndConsume);
        verify(consumer, times(2)).seek(new TopicPartition("test-topic", 0), 3L);
        verify(consumer, never()).commitSync();
    }

    @Test
    void consumeAttemptCapZeroMeansUnlimitedRedelivery() throws Exception {
        when(service.maxConsumeAttempts()).thenReturn(0); // unlimited (pre-WI22 behavior)
        IMessageConsumer msgConsumer = (topic, message, context) -> {
            throw new IllegalStateException("handler-boom");
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(record(0, 3, "poison")));

        for (int i = 0; i < 3; i++) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    task::pollAndConsume);
        }
        // Every round took the redelivery path — no endpoint, no commit.
        verify(consumer, times(3)).seek(new TopicPartition("test-topic", 0), 3L);
        verify(consumer, never()).commitSync();
    }

    @Test
    void batchTerminateConsumeRepublishesToDeadLetterWhenConfigured() throws Exception {
        when(service.resolveDeadLetterTopic("test-topic")).thenReturn("dlq-topic");
        when(service.sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        IMessageConsumer msgConsumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                List<Object> responses = new ArrayList<>();
                responses.add(null);
                responses.add(new io.nop.api.core.message.TerminateConsume("poison"));
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 3, "a"), record(0, 4, "poison")));

        task.batchConsume();

        // Record 0 committed; the terminated record 1 republished to the DLQ topic...
        verify(service).sendAsync(org.mockito.ArgumentMatchers.eq("dlq-topic"), any(), any());
        // ...and the batch still commits through the converged boundary.
        verify(consumer).commitSync(org.mockito.ArgumentMatchers.<java.util.Map<org.apache.kafka.common.TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>>any());
    }

    @Test
    void partialCommitInBatchCommitsBoundaryOnly() throws Exception {
        AtomicInteger batchCalls = new AtomicInteger();
        IMessageConsumer msgConsumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                batchCalls.incrementAndGet();
                List<Object> responses = new ArrayList<>();
                responses.add(null); // record 0 committed
                responses.add(null); // record 1 committed
                // record 2 carries the boundary: [0..1] committed, 2 onward not
                responses.add(new io.nop.api.core.message.PartialCommit(2));
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class)))
                .thenReturn(records(record(0, 10, "a"), record(0, 11, "b"), record(0, 12, "c")))
                .thenReturn(ConsumerRecords.empty());

        task.batchConsume();

        assertEquals(1, batchCalls.get());
        @SuppressWarnings("unchecked")
        org.apache.kafka.clients.consumer.OffsetAndMetadata nextOffset =
                new org.apache.kafka.clients.consumer.OffsetAndMetadata(12L); // record 1 (offset 11) + 1
        verify(consumer).commitSync(Collections.singletonMap(new TopicPartition("test-topic", 0), nextOffset));
    }

    // ------------------------------------------------------------------
    // WI7/D3: seek origins resolved in the rebalance callback (MSG-F-01 fix).
    // Main assertions use kafka-clients' own MockConsumer driven through a real
    // poll() (rebalance inside poll triggers the listener) — no Mockito mocks.
    // ------------------------------------------------------------------

    private org.apache.kafka.clients.consumer.MockConsumer<String, String> newMockConsumer() {
        return new org.apache.kafka.clients.consumer.MockConsumer<>(
                org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST);
    }

    private KafkaConsumeTask taskOn(org.apache.kafka.clients.consumer.MockConsumer<String, String> mc,
                                    MessageSubscribeOptions options) {
        KafkaConsumeTask task = new KafkaConsumeTask(service, mc,
                config((t, m, c) -> null, options), 100L);
        // Wire the D3 rebalance listener the same way doSubscribe does.
        mc.subscribe(Collections.singletonList("test-topic"), task.rebalanceListener());
        return task;
    }

    @Test
    void pollFirst_initSeekToBeginAppliesWhenNoCommittedOffset() throws Exception {
        org.apache.kafka.clients.consumer.MockConsumer<String, String> mc = newMockConsumer();
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSeekMode(SeekMode.INIT_SEEK_TO_BEGIN);
        KafkaConsumeTask task = taskOn(mc, options);
        mc.schedulePollTask(() -> mc.rebalance(Collections.singletonList(new TopicPartition("test-topic", 0))));
        mc.updateBeginningOffsets(Collections.singletonMap(new TopicPartition("test-topic", 0), 7L));
        mc.schedulePollTask(mc::wakeup);

        try {
            task.runTaskForTest();
        } catch (org.apache.kafka.common.errors.WakeupException ignored) {
            // second poll task wakes the loop
        }

        assertEquals(7L, mc.position(new TopicPartition("test-topic", 0)),
                "INIT_SEEK_TO_BEGIN with no committed offset must rewind to the beginning offset");
    }

    @Test
    void pollFirst_initSeekSkippedWhenCommittedOffsetExists() throws Exception {
        // MockConsumer has no committed() setter: subclass overrides committed(Set) to
        // simulate an existing group commit — the restart-resumes-from-committed contract.
        // LATEST reset: a correct (gated) implementation leaves the position at the end
        // offset; a buggy (ungated) one would rewind it to the beginning offset.
        org.apache.kafka.clients.consumer.MockConsumer<String, String> mc =
                new org.apache.kafka.clients.consumer.MockConsumer<String, String>(
                        org.apache.kafka.clients.consumer.OffsetResetStrategy.LATEST) {
                    @Override
                    public synchronized Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>
                            committed(Set<TopicPartition> partitions) {
                        return partitions.stream().collect(java.util.stream.Collectors.toMap(
                                tp -> tp, tp -> new org.apache.kafka.clients.consumer.OffsetAndMetadata(42L)));
                    }
                };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSeekMode(SeekMode.INIT_SEEK_TO_BEGIN);
        KafkaConsumeTask task = taskOn(mc, options);
        mc.schedulePollTask(() -> mc.rebalance(Collections.singletonList(new TopicPartition("test-topic", 0))));
        mc.updateBeginningOffsets(Collections.singletonMap(new TopicPartition("test-topic", 0), 7L));
        mc.updateEndOffsets(Collections.singletonMap(new TopicPartition("test-topic", 0), 100L));
        mc.schedulePollTask(mc::wakeup);

        try {
            task.runTaskForTest();
        } catch (org.apache.kafka.common.errors.WakeupException ignored) {
            // wakeup ends the loop
        }

        // No rewind to the beginning offset (7): the group's committed offset (42) wins
        // on restart, so INIT_SEEK must not apply (MockConsumer resolves the ungated
        // default position to the end offset 100).
        assertEquals(100L, mc.position(new TopicPartition("test-topic", 0)),
                "INIT_SEEK must not rewind a partition that already has a committed offset");
    }

    @Test
    void pollFirst_alwaysSeekToEndReappliesOnEveryAssignment() throws Exception {
        org.apache.kafka.clients.consumer.MockConsumer<String, String> mc = newMockConsumer();
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSeekMode(SeekMode.ALWAYS_SEEK_TO_END);
        KafkaConsumeTask task = taskOn(mc, options);
        TopicPartition tp = new TopicPartition("test-topic", 0);
        mc.updateEndOffsets(Collections.singletonMap(tp, 100L));
        mc.schedulePollTask(() -> mc.rebalance(Collections.singletonList(tp)));
        mc.schedulePollTask(() -> {
            // Simulate a second assignment (rebalance): shrink then grow again.
            mc.rebalance(Collections.emptyList());
            mc.rebalance(Collections.singletonList(tp));
        });
        mc.schedulePollTask(mc::wakeup);

        try {
            task.runTaskForTest();
        } catch (org.apache.kafka.common.errors.WakeupException ignored) {
            // wakeup ends the loop
        }

        assertEquals(100L, mc.position(tp),
                "ALWAYS_SEEK_TO_END must re-apply seekToEnd on every assignment");
    }

    @Test
    void listenerWiredThroughSubscribeCall() {
        org.apache.kafka.clients.consumer.MockConsumer<String, String> mc =
                org.mockito.Mockito.mock(org.apache.kafka.clients.consumer.MockConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSeekMode(SeekMode.INIT_SEEK_TO_BEGIN);

        class TestService extends KafkaMessageService {
            @Override
            protected Consumer<String, String> createConsumer(java.util.Properties props,
                                                              StringDeserializer keyDeserializer,
                                                              StringDeserializer valueDeserializer) {
                return mc;
            }
        }
        TestService testService = new TestService();
        testService.config = new KafkaClientConfig();
        testService.config.setBootstrapServers("localhost:9092");

        MessageSubscribeOptions subOptions = new MessageSubscribeOptions();
        subOptions.setSubscribeName("test-group");
        subOptions.setSeekMode(SeekMode.INIT_SEEK_TO_BEGIN);
        testService.doSubscribe(new MessageSubscriptionConfig("test-topic", (t, m, c) -> null, subOptions));

        org.mockito.ArgumentCaptor<org.apache.kafka.clients.consumer.ConsumerRebalanceListener> captor =
                org.mockito.ArgumentCaptor.forClass(org.apache.kafka.clients.consumer.ConsumerRebalanceListener.class);
        org.mockito.Mockito.verify(mc).subscribe(org.mockito.Mockito.anyCollection(), captor.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(captor.getValue(),
                "doSubscribe must register the D3 rebalance listener with the subscription");
    }

    @Test
    void seekToMessageRejectedSynchronouslyAtSubscribe() {
        TestSeekService testService = new TestSeekService();
        testService.config = new KafkaClientConfig();
        testService.config.setBootstrapServers("localhost:9092");

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("test-group");
        options.setSeekToMessage("some-msg-id");

        NopException e = assertThrows(NopException.class,
                () -> testService.doSubscribe(new MessageSubscriptionConfig("test-topic", (t, m, c) -> null, options)));
        assertEquals(KafkaErrors.ERR_KAFKA_SEEK_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void seekToTimeAppliesOnlyOnFirstAssignment() {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSeekToTime(1234567890L);
        KafkaConsumeTask task = new KafkaConsumeTask(service, consumer,
                config(msgConsumer, options), 100L);
        Set<TopicPartition> parts = Collections.singleton(new TopicPartition("test-topic", 0));
        when(consumer.assignment()).thenReturn(parts);
        when(consumer.offsetsForTimes(org.mockito.ArgumentMatchers.<Map<TopicPartition, Long>>any()))
                .thenReturn(Collections.emptyMap());

        task.onPartitionsAssigned(parts);
        task.onPartitionsAssigned(parts);

        // seekToTime replays would repeatedly rewind to the same timestamp: first
        // assignment only (adjudication note in the WI7 plan).
        verify(consumer, org.mockito.Mockito.times(1)).offsetsForTimes(
                org.mockito.ArgumentMatchers.<Map<TopicPartition, Long>>any());
    }

    /** Service stub that skips real consumer creation for subscribe-path validation tests. */
    static class TestSeekService extends KafkaMessageService {
        @Override
        protected Consumer<String, String> createConsumer(java.util.Properties props,
                                                          StringDeserializer keyDeserializer,
                                                          StringDeserializer valueDeserializer) {
            throw new IllegalStateException("consumer must not be constructed: seekToMessage is validated earlier");
        }
    }

    // ------------------------------------------------------------------
    // WI11: batch boundary semantics (F-06 / F-07 / F-08 / F-58)
    // ------------------------------------------------------------------

    @Test
    void batchFailureRewindsEachPartitionToFirstIncompleteRecord() throws Exception {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        doThrow(new IllegalStateException("batch-boom")).when(msgConsumer).onMessageBatch(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        ConsumerRecord<String, String> p0First = record(0, 10, "a");
        ConsumerRecord<String, String> p1First = record(1, 5, "b");
        when(consumer.poll(any(Duration.class))).thenReturn(
                records(p0First, record(0, 11, "a2"), p1First));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, task::batchConsume);

        verify(consumer).seek(new TopicPartition("test-topic", 0), 10L);
        verify(consumer).seek(new TopicPartition("test-topic", 1), 5L);
        verify(consumer, never()).commitSync();
        verify(consumer, never()).commitSync(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void batchCommitNeverCrossesUndispatchedRecords() throws Exception {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        when(msgConsumer.onMessageBatch(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new java.util.ArrayList<>(List.of("r0")));
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class))).thenReturn(
                records(record(0, 10, "a"), record(0, 11, "b")));

        task.batchConsume();

        org.apache.kafka.clients.consumer.OffsetAndMetadata nextOffset =
                new org.apache.kafka.clients.consumer.OffsetAndMetadata(11L);
        verify(consumer).commitSync(Collections.singletonMap(new TopicPartition("test-topic", 0), nextOffset));
        verify(consumer, never()).commitSync();
        verify(consumer).seek(new TopicPartition("test-topic", 0), 11L);
    }

    @Test
    void consumeLaterStopsBatchDispatchAndSkipsCommit() throws Exception {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        when(msgConsumer.onMessageBatch(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new java.util.ArrayList<>(java.util.Arrays.asList(
                null, new ConsumeLater(5L), "late-reply")));
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.poll(any(Duration.class))).thenReturn(
                records(record(0, 10, "a"), record(0, 11, "b"), record(0, 12, "c")));

        task.batchConsume();

        verify(consumer).seek(new TopicPartition("test-topic", 0), 11L);
        verify(consumer, never()).commitSync();
        verify(consumer, never()).commitSync(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void consumeLaterDelayHonoredBeforeRedelivery() throws Exception {
        // MSG-F-09 (WI12): the requested delay must elapse before the record is
        // re-delivered — no immediate re-poll spin. Lower-bound assertion only (sleep
        // guarantees at-least), CI-stable.
        long delayMs = 200L;
        java.util.List<Long> handledAt = new java.util.concurrent.CopyOnWriteArrayList<>();
        IMessageConsumer handler = (topic, message, context) -> {
            handledAt.add(System.nanoTime());
            return handledAt.size() == 1 ? new ConsumeLater(delayMs) : null;
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler, options), 100L, 50L);

        ConsumerRecord<String, String> redelivered = record(0, 7, "deferred");
        when(consumer.poll(any(Duration.class))).thenReturn(
                records(redelivered)).thenReturn(
                records(redelivered)).thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();
        task.pollAndConsume();

        org.junit.jupiter.api.Assertions.assertEquals(2, handledAt.size());
        long gapMs = (handledAt.get(1) - handledAt.get(0)) / 1_000_000L;
        org.junit.jupiter.api.Assertions.assertTrue(gapMs >= delayMs,
                "redelivery must wait for the requested delay, gap=" + gapMs + "ms");
    }

    @Test
    void batchConsumeLaterHonorsDelayBeforeReturn() throws Exception {
        long delayMs = 150L;
        java.util.List<Long> handledAt = new java.util.concurrent.CopyOnWriteArrayList<>();
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                handledAt.add(System.nanoTime());
                return new java.util.ArrayList<>(java.util.Arrays.asList(
                        null, new ConsumeLater(delayMs), "late-reply"));
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler, options), 100L, 50L);

        when(consumer.poll(any(Duration.class))).thenReturn(
                records(record(0, 10, "a"), record(0, 11, "b"), record(0, 12, "c")));

        long before = System.nanoTime();
        task.batchConsume();
        long after = System.nanoTime();

        long elapsedMs = (after - before) / 1_000_000L;
        org.junit.jupiter.api.Assertions.assertTrue(elapsedMs >= delayMs,
                "batch ConsumeLater must honor the delay before returning, elapsed=" + elapsedMs + "ms");
    }

    // ------------------------------------------------------------------
    // WI13: D7 group identity + F-28 extraProps ordering
    // ------------------------------------------------------------------

    @Test
    void resolveGroupIdPrefixesGlobalGroupIdWithSubscribeName() {
        KafkaMessageService svc = new KafkaMessageService();
        KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();
        consumerConfig.setGroupId("global-ns");
        svc.setDefaultConsumerConfig(consumerConfig);

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("orders-sub");
        org.junit.jupiter.api.Assertions.assertEquals("global-ns.orders-sub",
                svc.resolveGroupId(options));
    }

    @Test
    void subscribeNameIsTheEffectiveIdentityByDefault() {
        KafkaMessageService svc = new KafkaMessageService();
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("orders-sub");
        org.junit.jupiter.api.Assertions.assertEquals("orders-sub", svc.resolveGroupId(options));
    }

    @Test
    void missingSubscribeNameIsTypedRejectedEvenWithGlobalGroupId() {
        KafkaMessageService svc = new KafkaMessageService();
        KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();
        consumerConfig.setGroupId("global-ns");
        svc.setDefaultConsumerConfig(consumerConfig);

        io.nop.api.core.exceptions.NopException e =
                org.junit.jupiter.api.Assertions.assertThrows(io.nop.api.core.exceptions.NopException.class,
                        () -> svc.resolveGroupId(new MessageSubscribeOptions()));
        org.junit.jupiter.api.Assertions.assertEquals(
                KafkaErrors.ERR_GROUP_ID_NOT_CONFIGURED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void extraPropsCannotOverrideManagedKeys() {
        // F-28: group.id / bootstrap.servers / managed tuning keys win over extraProps.
        KafkaMessageService svc = new KafkaMessageService();
        svc.config = new KafkaClientConfig();
        svc.config.setBootstrapServers("kafka-1:9092");
        java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();
        extra.put("group.id", "injected-group");
        extra.put("bootstrap.servers", "injected:9092");
        extra.put("max.poll.records", "1");
        extra.put("sasl.mechanism", "PLAIN");
        svc.config.setExtraProps(extra);
        KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();
        consumerConfig.setMaxPollRecords(500);
        svc.setDefaultConsumerConfig(consumerConfig);

        java.util.Properties props = svc.buildConsumerProps("resolved-group");
        org.junit.jupiter.api.Assertions.assertEquals("resolved-group", props.getProperty("group.id"));
        org.junit.jupiter.api.Assertions.assertEquals("kafka-1:9092", props.getProperty("bootstrap.servers"));
        org.junit.jupiter.api.Assertions.assertEquals(500, props.get("max.poll.records"));
        // Unmanaged keys (e.g. SASL) remain the documented extraProps use case.
        org.junit.jupiter.api.Assertions.assertEquals("PLAIN", props.getProperty("sasl.mechanism"));
    }

    @Test
    void differentSubscribesDoNotShareAssignmentDomain() throws Exception {
        // D7: heterogeneous subscriptions must land in disjoint consumer groups.
        KafkaMessageService realService = new KafkaMessageService();
        KafkaMessageService spyService = org.mockito.Mockito.spy(realService);
        spyService.config = new KafkaClientConfig();
        spyService.config.setBootstrapServers("localhost:9092");
        KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();
        consumerConfig.setGroupId("global-ns");
        spyService.setDefaultConsumerConfig(consumerConfig);

        java.util.List<String> groupIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.Properties props = inv.getArgument(0);
            groupIds.add(props.getProperty("group.id"));
            return mock(Consumer.class);
        }).when(spyService).createConsumer(
                org.mockito.ArgumentMatchers.any(java.util.Properties.class),
                org.mockito.ArgumentMatchers.any(StringDeserializer.class),
                org.mockito.ArgumentMatchers.any(StringDeserializer.class));
        org.mockito.Mockito.doNothing().when(spyService).warnIfAutoCommit();

        MessageSubscribeOptions optA = new MessageSubscribeOptions();
        optA.setSubscribeName("topic-a-sub");
        MessageSubscribeOptions optB = new MessageSubscribeOptions();
        optB.setSubscribeName("topic-b-sub");
        io.nop.api.core.message.IMessageSubscription subA = spyService.doSubscribe(
                new MessageSubscriptionConfig("topic-a", (t, m, c) -> null, optA));
        io.nop.api.core.message.IMessageSubscription subB = spyService.doSubscribe(
                new MessageSubscriptionConfig("topic-b", (t, m, c) -> null, optB));
        try {
            org.junit.jupiter.api.Assertions.assertEquals(2, groupIds.size());
            org.junit.jupiter.api.Assertions.assertEquals("global-ns.topic-a-sub", groupIds.get(0));
            org.junit.jupiter.api.Assertions.assertEquals("global-ns.topic-b-sub", groupIds.get(1));
            org.junit.jupiter.api.Assertions.assertNotEquals(groupIds.get(0), groupIds.get(1));
        } finally {
            subA.cancel();
            subB.cancel();
        }
    }

    @Test
    void doSubscribeWarnsOnceWhenAutoCommitEnabled() throws Exception {
        KafkaMessageService realService = new KafkaMessageService();
        KafkaMessageService spyService = org.mockito.Mockito.spy(realService);
        spyService.config = new KafkaClientConfig();
        spyService.config.setBootstrapServers("localhost:9092");
        KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();
        consumerConfig.setEnableAutoCommit(true);
        spyService.setDefaultConsumerConfig(consumerConfig);

        Consumer<String, String> mockConsumer = mock(Consumer.class);
        org.mockito.Mockito.doReturn(mockConsumer).when(spyService).createConsumer(
                org.mockito.ArgumentMatchers.any(java.util.Properties.class),
                org.mockito.ArgumentMatchers.any(StringDeserializer.class),
                org.mockito.ArgumentMatchers.any(StringDeserializer.class));
        org.mockito.Mockito.doNothing().when(spyService).warnIfAutoCommit();

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("test-group");
        io.nop.api.core.message.IMessageSubscription subscription = spyService.doSubscribe(
                new MessageSubscriptionConfig("test-topic", (t, m, c) -> null, options));
        try {
            org.mockito.Mockito.verify(spyService).warnIfAutoCommit();
        } finally {
            subscription.cancel();
        }
    }

    @Test
    void consumeFailureAttemptsIncrementPerLocation() throws Exception {
        // WI16: consecutive failures on the same record increment the attempt counter.
        java.util.List<Integer> attempts = new java.util.concurrent.CopyOnWriteArrayList<>();
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, io.nop.api.core.message.IMessageConsumeContext ctx) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onConsumeFailure(io.nop.api.core.message.ConsumeFailure failure) {
                attempts.add(failure.getAttempt());
                org.junit.jupiter.api.Assertions.assertNotNull(failure.getLocation());
                org.junit.jupiter.api.Assertions.assertTrue(failure.getLocation().contains("test-topic-0@"));
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler, options), 0L, 50L);

        ConsumerRecord<String, String> record = record(0, 3, "poison");
        when(consumer.poll(any(Duration.class))).thenReturn(
                records(record)).thenReturn(
                records(record)).thenThrow(new org.apache.kafka.common.errors.WakeupException());

        try {
            task.runTaskForTest();
        } catch (org.apache.kafka.common.errors.WakeupException ignored) {
            // third poll wakes the loop
        }

        org.junit.jupiter.api.Assertions.assertEquals(java.util.Arrays.asList(1, 2), attempts,
                "attempt must increment across consecutive failures of the same record");
    }

    @Test
    void maxPollIntervalIsConfigurableAndManaged() {
        // WI17 (F-10): the key is configurable and a managed key (extraProps cannot
        // override it — see F-28 ordering).
        KafkaMessageService svc = new KafkaMessageService();
        svc.config = new KafkaClientConfig();
        svc.config.setBootstrapServers("kafka-1:9092");
        java.util.Map<String, String> extra = new java.util.LinkedHashMap<>();
        extra.put("max.poll.interval.ms", "1");
        svc.config.setExtraProps(extra);
        KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();
        consumerConfig.setMaxPollIntervalMs(450000);
        svc.setDefaultConsumerConfig(consumerConfig);

        java.util.Properties props = svc.buildConsumerProps("g");
        org.junit.jupiter.api.Assertions.assertEquals(450000, props.get("max.poll.interval.ms"));
    }

    @Test
    void slowHandlerCompletesBatchWithoutError() throws Exception {
        // WI17 (F-10/F-11): a handler slower than the poll timeout completes the batch and
        // commits without exceptions (wiring/tolerance smoke under mock semantics; the real
        // broker kick timing is WI33's gated scope).
        java.util.concurrent.atomic.AtomicInteger handled = new java.util.concurrent.atomic.AtomicInteger();
        IMessageConsumer slowHandler = (topic, message, context) -> {
            handled.incrementAndGet();
            try {
                Thread.sleep(100L); // > pollTimeoutMs (50)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(slowHandler, options), 100L, 50L);

        when(consumer.poll(any(Duration.class))).thenReturn(
                records(record(0, 0, "slow"))).thenReturn(ConsumerRecords.empty());

        task.pollAndConsume();

        org.junit.jupiter.api.Assertions.assertEquals(1, handled.get());
        verify(consumer).commitSync();
    }


    @Test
    void startAndStopToggleActive() throws Exception {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        when(consumer.assignment()).thenReturn(Collections.emptySet());
        when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());

        assertFalse(task.isActive());
        task.start();
        Thread.sleep(200);
        assertTrue(task.isActive());
        task.stop();
        assertFalse(task.isActive());
    }
}
