/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WI19 (MSG-F-24): consume threads are daemon, named, and fan-out distinguishable; and
 * (LifeCycleSupport adoption) {@code init}/{@code destroy} run on the platform lifecycle
 * state machine — state transitions observable via {@code isActive()}, restart after
 * destroy allowed, destroy-before-init a no-op.
 */
class TestKafkaServiceLifecycle {

    private KafkaMessageService configuredService() {
        KafkaMessageService service = new KafkaMessageService();
        KafkaClientConfig config = new KafkaClientConfig();
        config.setBootstrapServers("localhost:9092");
        service.setConfig(config);
        return service;
    }

    @Test
    void initDestroyRunOnLifecycleStateMachineAndSupportRestart() {
        KafkaMessageService service = configuredService();
        assertFalse(service.isActive(), "a fresh service must not be active");

        service.init();
        assertTrue(service.isActive(), "init must go through LifeCycleSupport.start()");
        assertNotNull(service.getProducer());

        service.destroy();
        assertFalse(service.isActive(), "destroy must go through LifeCycleSupport.stop()");

        // allowRestart defaults to true: redeploy-style re-init after destroy is supported.
        service.init();
        assertTrue(service.isActive());
        service.destroy();
        assertFalse(service.isActive());
    }

    @Test
    void initFailureLeavesServiceInactive() {
        KafkaMessageService service = new KafkaMessageService();
        service.setConfig(new KafkaClientConfig()); // bootstrapServers missing
        assertThrows(NopException.class, service::init);
        assertFalse(service.isActive(), "a failed start must not leave the service active");
    }

    @Test
    void destroyWithoutInitIsNoOp() {
        KafkaMessageService service = new KafkaMessageService();
        service.destroy(); // ServiceStatus.CREATED disallows stop — must not throw / NPE
        assertFalse(service.isActive());
    }

    /**
     * WI21: the DLQ self-loop guard — a dead-letter topic equal to the consumed topic is
     * disabled (null) instead of republishing a terminate into its own live topic forever.
     */
    @Test
    void resolveDeadLetterTopicDisablesSelfLoopAndUnconfiguredIsNull() {
        KafkaMessageService service = new KafkaMessageService();
        assertNull(service.resolveDeadLetterTopic("t"), "no consumer config → DLQ off");

        KafkaConsumerConfig config = new KafkaConsumerConfig();
        config.setDeadLetterTopic("dlq-topic");
        service.setDefaultConsumerConfig(config);
        assertEquals("dlq-topic", service.resolveDeadLetterTopic("t"));

        config.setDeadLetterTopic("t"); // self-loop
        assertNull(service.resolveDeadLetterTopic("t"),
                "dlq == consumed topic must degrade to off, never loop");
    }

    @Test
    void consumeThreadFactoryNamesAreDaemonAndFanOutDistinct() {
        KafkaMessageService service = new KafkaMessageService();
        // Two factories for the SAME group (fan-out members) must produce distinct names.
        Thread t1 = service.consumeThreadFactory("orders").newThread(() -> {
        });
        Thread t2 = service.consumeThreadFactory("orders").newThread(() -> {
        });
        assertTrue(t1.isDaemon(), "consume thread must be daemon (F-24)");
        assertTrue(t1.getName().startsWith("nop-msg-kafka-consume-orders-"),
                "unexpected thread name: " + t1.getName());
        // poolNumber is a JVM-wide counter: assert the prefix, never a full name.
        assertNotEquals(t1.getName(), t2.getName(), "fan-out threads must be distinguishable");
    }

    /**
     * Wiring proof: subscribe(concurrency=2) really starts two poll threads whose names are
     * distinguishable, and cancel terminates both — the shutdown-sequencing face of WI19.
     */
    @Test
    void doSubscribeRunsOnNamedDaemonThreadsAndCancelTerminatesThem() throws Exception {
        KafkaMessageService spyService = org.mockito.Mockito.spy(new KafkaMessageService());
        spyService.config = new KafkaClientConfig();
        spyService.config.setBootstrapServers("localhost:9092");

        Consumer<String, String> mockConsumer = mock(Consumer.class);
        // Gentle bounded poll loop (50ms rounds, empty records) so the threads stay alive
        // until cancel; wakeup() on a mock is a no-op, so exit is driven by the active flag
        // within one poll round — which is exactly the bounded-shutdown property under test.
        when(mockConsumer.poll(any(Duration.class))).thenAnswer(inv -> {
            Thread.sleep(50);
            return ConsumerRecords.EMPTY;
        });
        org.mockito.Mockito.doReturn(mockConsumer).when(spyService).createConsumer(
                any(Properties.class),
                any(StringDeserializer.class),
                any(StringDeserializer.class));

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-threads");
        options.setConcurrency(2);

        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        io.nop.api.core.message.IMessageSubscription subscription =
                spyService.subscribe("t-threads", (t, m, c) -> null, options);
        try {
            long deadline = System.currentTimeMillis() + 5000;
            while (threadNames.size() < 2 && System.currentTimeMillis() < deadline) {
                threadNames = Thread.getAllStackTraces().keySet().stream()
                        .map(Thread::getName)
                        .filter(n -> n.startsWith("nop-msg-kafka-consume-sub-threads-"))
                        .collect(Collectors.toSet());
                if (threadNames.size() < 2)
                    Thread.sleep(20);
            }
            assertEquals(2, threadNames.size(),
                    "fan-out members must run on distinct named threads, got: " + threadNames);
        } finally {
            subscription.cancel();
        }

        // Shutdown sequencing: after cancel returns (executor awaited inside), the consume
        // threads are gone — bounded, no lingering poller.
        long deadline = System.currentTimeMillis() + 5000;
        long remaining = 1;
        while (remaining > 0 && System.currentTimeMillis() < deadline) {
            remaining = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.getName().startsWith("nop-msg-kafka-consume-sub-threads-"))
                    .count();
            if (remaining > 0)
                Thread.sleep(20);
        }
        assertEquals(0, remaining, "cancel must terminate the fan-out consume threads");
    }

    /** No real producer in doStart (offline test); consumer creation injected. */
    static class DestroyOrderService extends KafkaMessageService {
        Consumer<String, String> consumerForTest;

        @Override
        protected Consumer<String, String> createConsumer(java.util.Properties props,
                                                          StringDeserializer keyDeserializer,
                                                          StringDeserializer valueDeserializer) {
            return consumerForTest;
        }

        @Override
        protected void doStart() {
            initialized = true; // skip the real KafkaProducer; destroy closes the mock below
        }
    }

    /**
     * WI20/F-27: destroy must cancel subscriptions (consumer closed) BEFORE closing the
     * producer — locked with a cross-mock InOrder verification.
     */
    @Test
    void destroyCancelsSubscriptionsBeforeClosingProducer() throws Exception {
        DestroyOrderService service = new DestroyOrderService();
        service.config = new KafkaClientConfig();
        service.config.setBootstrapServers("localhost:9092");
        Consumer<String, String> mockConsumer = mock(Consumer.class);
        service.consumerForTest = mockConsumer;
        when(mockConsumer.poll(any(Duration.class))).thenAnswer(inv -> {
            Thread.sleep(50);
            return ConsumerRecords.EMPTY;
        });
        when(mockConsumer.assignment()).thenReturn(java.util.Collections.emptySet());

        org.apache.kafka.clients.producer.KafkaProducer<String, String> mockProducer =
                mock(org.apache.kafka.clients.producer.KafkaProducer.class);

        service.init(); // ACTIVE — destroy must run the real doStop
        service.producer = mockProducer;

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-destroy-order");
        io.nop.api.core.message.IMessageSubscription subscription =
                service.subscribe("t-order", (t, m, c) -> null, options);

        service.destroy();

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mockConsumer, mockProducer);
        inOrder.verify(mockConsumer).close();
        inOrder.verify(mockProducer).close(any(java.time.Duration.class));
        assertFalse(service.isActive());
        assertTrue(subscription.isCancelled());
    }

    /**
     * WI20/F-26: suspend/resume are applied by the consume thread at the loop head — the
     * desired state is re-asserted every round (pause while suspended, resume after).
     */
    @Test
    void suspendStateAppliedByConsumeThreadAndReversedOnResume() throws Exception {
        Consumer<String, String> mockConsumer = mock(Consumer.class);
        Set<TopicPartition> assignment = java.util.Collections.singleton(
                new TopicPartition("t-suspend", 0));
        when(mockConsumer.assignment()).thenReturn(assignment);
        when(mockConsumer.poll(any(Duration.class))).thenAnswer(inv -> {
            Thread.sleep(20);
            return ConsumerRecords.EMPTY;
        });

        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscriptionConfig config =
                new MessageSubscriptionConfig("t-suspend", msgConsumer, new MessageSubscribeOptions());
        KafkaConsumeTask task = new KafkaConsumeTask(new KafkaMessageService(), mockConsumer,
                config, KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MS);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "test-suspend-apply");
            t.setDaemon(true);
            return t;
        });
        task.bindExecutor(executor, 100);

        task.setSuspended(true);
        task.start();
        try {
            org.mockito.Mockito.verify(mockConsumer, org.mockito.Mockito.timeout(2000))
                    .pause(assignment);

            task.setSuspended(false);
            org.mockito.Mockito.verify(mockConsumer, org.mockito.Mockito.timeout(2000))
                    .resume(assignment);
        } finally {
            task.stop();
            executor.shutdown();
        }
    }

    /**
     * WI22: the backoff base is read from the consumer config in newTask, and the
     * cap/backoffMax accessors fall back to the module defaults (never 0) when no config
     * is set.
     */
    @Test
    void newTaskUsesConfiguredBackoffAndAccessorsFallBackToDefaults() {
        KafkaMessageService service = new KafkaMessageService();
        // No config: production defaults (not 0) so a directly constructed service keeps
        // production semantics.
        assertEquals(KafkaConsumeTask.DEFAULT_MAX_CONSUME_ATTEMPTS, service.maxConsumeAttempts());
        assertEquals(KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MAX_MS,
                service.consumeErrorBackoffMaxMs());

        KafkaConsumerConfig config = new KafkaConsumerConfig();
        config.setConsumeErrorBackoffMs(777L);
        config.setConsumeErrorBackoffMaxMs(888L);
        config.setMaxConsumeAttempts(3);
        service.setDefaultConsumerConfig(config);
        assertEquals(3, service.maxConsumeAttempts());
        assertEquals(888L, service.consumeErrorBackoffMaxMs());

        org.apache.kafka.clients.consumer.Consumer<String, String> mockConsumer =
                mock(Consumer.class);
        KafkaConsumeTask task = service.newTask(mockConsumer,
                new MessageSubscriptionConfig("t", (t, m, c) -> null, new MessageSubscribeOptions()));
        assertEquals(777L, task.consumeErrorBackoffMs(), "newTask must pass the config base");
    }

    /**
     * WI25 (F-17): per-subscribe batchReceiveCount overrides the global maxPollRecords;
     * null options keeps the global default.
     */
    @Test
    void batchReceiveCountOverridesMaxPollRecordsPerSubscribe() {
        KafkaMessageService service = new KafkaMessageService();
        KafkaClientConfig clientConfig = new KafkaClientConfig();
        clientConfig.setBootstrapServers("localhost:9092");
        service.setConfig(clientConfig);
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        config.setMaxPollRecords(500);
        service.setDefaultConsumerConfig(config);

        MessageSubscribeOptions withCount = new MessageSubscribeOptions();
        withCount.setBatchReceiveCount(25);
        java.util.Properties props = service.buildConsumerProps("g", withCount);
        assertEquals(25, props.get(org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG),
                "per-subscribe batchReceiveCount must override maxPollRecords");

        props = service.buildConsumerProps("g", null);
        assertEquals(500, props.get(org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG),
                "null options keeps the global default");
    }

    /**
     * WI20/F-26: a suspend issued before any partition was assigned (or before a
     * rebalance) holds for the freshly assigned partitions.
     */
    @Test
    void onPartitionsAssignedReappliesPauseWhenSuspended() {
        Consumer<String, String> mockConsumer = mock(Consumer.class);
        Set<TopicPartition> assignment = java.util.Collections.singleton(
                new TopicPartition("t-suspend", 0));
        when(mockConsumer.assignment()).thenReturn(assignment);

        IMessageConsumer msgConsumer = mock(IMessageConsumer.class);
        MessageSubscriptionConfig config =
                new MessageSubscriptionConfig("t-suspend", msgConsumer, new MessageSubscribeOptions());
        KafkaConsumeTask task = new KafkaConsumeTask(new KafkaMessageService(), mockConsumer,
                config, KafkaConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MS);

        task.setSuspended(true);
        task.onPartitionsAssigned(assignment);

        org.mockito.Mockito.verify(mockConsumer).pause(assignment);
    }
}
