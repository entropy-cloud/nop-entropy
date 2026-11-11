package io.nop.message.kafka;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-MA-01（plan 2558 裁定 A）carrier：bounded {@code consumeAwaitTimeoutMs} on the
 * Kafka consume await points.
 *
 * <ul>
 * <li>A handler future that never completes no longer wedges the poll thread: the
 * dedicated timeout branch seeks the record back for redelivery and SKIPS the round
 * commit — attempt=0 semantics, never the maxConsumeAttempts/poison budget.</li>
 * <li>A {@code CompletionStage} that cannot convert to a {@code CompletableFuture} is a
 * typed error ({@code ERR_KAFKA_CONSUME_AWAIT_NOT_ASYNC}), never a silent unbounded
 * wait.</li>
 * <li>The StackOverflowError loop-barrier (isActive misreport) face is the 2496
 * regression pin family — covered by {@code TestKafkaConsumeTaskErrorBarrier}; not
 * duplicated here.</li>
 * </ul>
 */
class TestConsumeAwaitTimeout {

    private KafkaMessageService service;
    private Consumer<String, String> consumer;
    private java.util.concurrent.ExecutorService executor;

    @BeforeEach
    void setUp() {
        service = mock(KafkaMessageService.class);
        consumer = mock(Consumer.class);
        executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    }

    private MessageSubscriptionConfig config(IMessageConsumer msgConsumer) {
        return new MessageSubscriptionConfig("test-topic", msgConsumer, new MessageSubscribeOptions());
    }

    private ConsumerRecords records(ConsumerRecord<String, String>... recs) {
        Map<TopicPartition, List<ConsumerRecord<String, String>>> map = new HashMap<>();
        for (ConsumerRecord<String, String> r : recs) {
            map.computeIfAbsent(new TopicPartition(r.topic(), r.partition()), k -> new java.util.ArrayList<>())
                    .add(r);
        }
        return new ConsumerRecords(map);
    }

    private ConsumerRecord<String, String> record(long offset, String value) {
        return new ConsumerRecord<>("test-topic", 0, offset, null, value);
    }

    @Test
    void neverCompletingFutureTimesOutAndSeeksBackWithoutPoisonBudget() throws Exception {
        IMessageConsumer handler = (topic, message, context) -> new CompletableFuture<>(); // never completes

        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler), 100L, 100L);
        task.setConsumeAwaitTimeoutMs(150L);

        when(consumer.poll(any(Duration.class))).thenReturn(records(record(7, "stuck")));

        long start = System.nanoTime();
        // Dedicated timeout branch: returns (no throw), seek back, NO commit.
        task.pollAndConsume();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        verify(consumer).seek(new TopicPartition("test-topic", 0), 7L);
        verify(consumer).seek(new TopicPartition("test-topic", 0), 7L);
        verify(consumer, never()).commitSync();
        assertTrue(elapsedMs < 5000, "wait must be bounded, took " + elapsedMs + "ms");
        // attempt=0 semantics: the poison budget was never consulted.
        verify(service, never()).maxConsumeAttempts();
    }

    @Test
    void batchNeverCompletingFutureTimesOutAndRewindsWithoutCommit() throws Exception {
        IMessageConsumer handler = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message,
                                    io.nop.api.core.message.IMessageConsumeContext ctx) {
                return null;
            }

            @Override
            public List<Object> onMessageBatch(List<io.nop.api.core.message.TopicMessage> msgList,
                                               io.nop.api.core.message.IMessageConsumeContext ctx) {
                List<Object> responses = new java.util.ArrayList<>();
                responses.add(null);
                responses.add(new CompletableFuture<>()); // record 1 never completes
                responses.add("never reached");
                return responses;
            }
        };
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(10);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler, options), 100L, 100L);
        task.setConsumeAwaitTimeoutMs(150L);

        when(consumer.poll(any(Duration.class))).thenReturn(
                records(record(3, "a"), record(4, "stuck"), record(5, "c")));

        boolean dispatched = task.batchConsume();

        // Record 0 was dispatched (and replied) before record 1's await timed out —
        // dispatched reflects the earlier records; the F-08 endpoint form still holds:
        assertTrue(dispatched);
        // the timed-out record re-seeks, nothing is committed.
        verify(consumer).seek(new TopicPartition("test-topic", 0), 4L);
        verify(consumer, never()).commitSync();
    }

    @Test
    void nonCompletableFutureStageIsTypedRejected() {
        IMessageConsumer handler = (topic, message, context) -> {
            // A minimal CompletionStage that refuses CompletableFuture conversion — the
            // 非法形态 from 裁定 A (never a silent unbounded wait).
            return new java.util.concurrent.CompletableFuture<Void>() {
                @Override
                public CompletableFuture<Void> toCompletableFuture() {
                    throw new UnsupportedOperationException("not a CompletableFuture");
                }
            };
        };

        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler), 100L, 100L);
        task.setConsumeAwaitTimeoutMs(150L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(record(7, "weird")));

        NopException e = assertThrows(NopException.class, task::pollAndConsume);
        assertEquals(KafkaErrors.ERR_KAFKA_CONSUME_AWAIT_NOT_ASYNC.getErrorCode(), e.getErrorCode());
        // Normal failure path applies (typed configuration error, budget counts): seek back.
        verify(consumer).seek(new TopicPartition("test-topic", 0), 7L);
    }

    private MessageSubscriptionConfig config(IMessageConsumer msgConsumer, MessageSubscribeOptions options) {
        return new MessageSubscriptionConfig("test-topic", msgConsumer, options);
    }

    @Test
    void completingFutureStillResolvesWithoutTimeoutBranch() throws Exception {
        IMessageConsumer handler = (topic, message, context) ->
                CompletableFuture.completedFuture("done");

        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(handler), 100L, 100L);
        task.setConsumeAwaitTimeoutMs(150L);
        when(consumer.poll(any(Duration.class))).thenReturn(records(record(1, "ok")));

        assertTrue(task.pollAndConsume());
        verify(consumer).commitSync();
    }
}
