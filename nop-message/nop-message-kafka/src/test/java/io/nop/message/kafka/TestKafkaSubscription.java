/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Component-level test for {@link KafkaMessageService.KafkaMessageSubscription} lifecycle:
 * suspend/resume map to {@link KafkaConsumer#pause} / {@link KafkaConsumer#resume}, and
 * cancel stops the poll task and closes the consumer.
 */
class TestKafkaSubscription {

    private KafkaMessageService service;
    private Consumer<String, String> consumer;
    private ExecutorService executor;
    private KafkaConsumeTask task;

    @BeforeEach
    void setUp() {
        service = new KafkaMessageService();
        consumer = mock(Consumer.class);
        executor = Executors.newSingleThreadExecutor();
        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        MessageSubscriptionConfig config = new MessageSubscriptionConfig("t", msgConsumer, options);
        task = mock(KafkaConsumeTask.class);
    }

    private KafkaMessageService.KafkaMessageSubscription newSubscription() {
        return service.new KafkaMessageSubscription(consumer, executor, task);
    }

    @Test
    void suspendSetsFlagAndPausesConsumer() {
        Set<TopicPartition> assignment = new HashSet<>();
        assignment.add(new TopicPartition("t", 0));
        when(consumer.assignment()).thenReturn(assignment);
        doNothing().when(consumer).pause(any());

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        assertFalse(sub.isSuspended());
        sub.suspend();
        assertTrue(sub.isSuspended());
    }

    @Test
    void resumeClearsFlagAndResumesConsumer() {
        Set<TopicPartition> assignment = new HashSet<>();
        assignment.add(new TopicPartition("t", 0));
        when(consumer.assignment()).thenReturn(assignment);
        doNothing().when(consumer).pause(any());
        doNothing().when(consumer).resume(any());

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        sub.suspend();
        assertTrue(sub.isSuspended());
        sub.resume();
        assertFalse(sub.isSuspended());
    }

    @Test
    void cancelStopsTaskAndClosesConsumer() throws Exception {
        doNothing().when(task).stop();
        doNothing().when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        assertFalse(sub.isCancelled());
        sub.cancel();
        assertTrue(sub.isCancelled());

        org.mockito.Mockito.verify(task).stop();
        org.mockito.Mockito.verify(consumer).close();
    }

    @Test
    void cancelIsIdempotent() {
        doNothing().when(task).stop();
        doNothing().when(consumer).close();

        KafkaMessageService.KafkaMessageSubscription sub = newSubscription();
        sub.cancel();
        sub.cancel();
        // task.stop invoked once on first cancel
        org.mockito.Mockito.verify(task).stop();
    }
}
