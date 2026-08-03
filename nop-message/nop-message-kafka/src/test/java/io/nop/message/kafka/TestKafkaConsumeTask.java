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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        IMessageConsumer msgConsumer = (topic, message, context) -> new ConsumeLater(1000L);
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
    void seekToBeginHonoredNotStubbed() {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSeekMode(SeekMode.INIT_SEEK_TO_BEGIN);
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        Set<TopicPartition> assignment = new LinkedHashSet<>();
        assignment.add(new TopicPartition("test-topic", 0));
        when(consumer.assignment()).thenReturn(assignment);
        doNothing().when(consumer).seekToBeginning(any());

        task.seekToPosition();

        verify(consumer).seekToBeginning(assignment);
    }

    @Test
    void seekToMessageThrowsNotSilentlySkipped() {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSeekMode(SeekMode.DEFAULT);
        options.setSeekToMessage("some-msg-id");
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options), 100L, 100L);

        NopException real = new NopException(KafkaErrors.ERR_KAFKA_SEEK_NOT_SUPPORTED);
        when(service.error(any(), any())).thenReturn(real);

        assertThrows(NopException.class, task::seekToPosition);
    }

    @Test
    void startAndStopToggleActive() throws Exception {
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        KafkaConsumeTask task = new KafkaConsumeTask(service, executor, consumer,
                config(msgConsumer, options));

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
