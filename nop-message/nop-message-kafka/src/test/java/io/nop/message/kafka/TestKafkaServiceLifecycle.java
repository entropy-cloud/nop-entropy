/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.kafka;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.MessageSubscribeOptions;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
