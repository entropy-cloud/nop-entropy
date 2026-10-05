/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.MessageSubscribeOptions;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WI19: {@code PulsarMessageService} runs on the platform {@link io.nop.commons.service.LifeCycleSupport}
 * state machine — {@code init}/{@code destroy} delegate to {@code start}/{@code stop},
 * state transitions are observable via {@code isActive()}, restart after destroy is
 * allowed, and destroy-before-init is a no-op. The {@code createClient()} seam keeps these
 * tests broker-free (mirrors the Kafka createConsumer factory).
 *
 * <p>WI20 additions: destroy order (F-27: subscriptions before producers), concurrent
 * cancel running the cleanup body exactly once (F-25), and destroy-then-send rejected
 * with a typed error instead of an NPE (F-30).
 */
class TestPulsarServiceLifecycle {

    /** Overrides client + consumer-builder creation so no broker connection is attempted. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static class LifecycleTestService extends PulsarMessageService {
        PulsarClient clientForTest = mock(PulsarClient.class);
        org.apache.pulsar.client.api.ConsumerBuilder builder =
                mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
        org.apache.pulsar.client.api.Consumer<Object> consumer =
                mock(org.apache.pulsar.client.api.Consumer.class);

        LifecycleTestService() {
            try {
                stubBrokerFaces();
            } catch (org.apache.pulsar.client.api.PulsarClientException e) {
                // Stubbing never actually invokes the broker client; impossible path.
                throw new IllegalStateException(e);
            }
        }

        private void stubBrokerFaces() throws org.apache.pulsar.client.api.PulsarClientException {
            // Bounded idle receive (WI19 form): each round sleeps briefly and returns null,
            // so subscription.cancel() does not wait out the full 5s await budget.
            when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
                        Thread.sleep(30);
                        return null;
                    });
            when(builder.topic(org.mockito.ArgumentMatchers.anyString())).thenReturn(builder);
            when(builder.subscriptionName(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(builder);
            when(builder.subscribe()).thenReturn(consumer);
        }

        @Override
        protected PulsarClient createClient() {
            return clientForTest;
        }

        @Override
        protected org.apache.pulsar.client.api.ConsumerBuilder newConsumerBuilder(
                org.apache.pulsar.client.api.Schema<?> schema) {
            return builder;
        }
    }

    private LifecycleTestService configuredService() {
        LifecycleTestService service = new LifecycleTestService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl("pulsar://localhost:6650");
        service.setConfig(config);
        return service;
    }

    private io.nop.api.core.message.IMessageSubscription subscribeOne(LifecycleTestService service,
                                                                      String topic, String subName) {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName(subName);
        return service.subscribe(topic, (t, m, c) -> null, options);
    }

    @Test
    void initDestroyRunOnLifecycleStateMachineAndSupportRestart() throws Exception {
        LifecycleTestService service = configuredService();
        assertFalse(service.isActive(), "a fresh service must not be active");

        service.init();
        assertTrue(service.isActive(), "init must go through LifeCycleSupport.start()");

        service.destroy();
        assertFalse(service.isActive(), "destroy must go through LifeCycleSupport.stop()");
        verify(service.clientForTest).close();

        // allowRestart defaults to true: re-init after destroy is supported.
        service.init();
        assertTrue(service.isActive());
        service.destroy();
        assertFalse(service.isActive());
    }

    @Test
    void initFailureLeavesServiceInactive() {
        PulsarMessageService service = new PulsarMessageService();
        service.setConfig(new PulsarClientConfig()); // serviceUrl missing
        assertThrows(NopException.class, service::init);
        assertFalse(service.isActive(), "a failed start must not leave the service active");
    }

    @Test
    void destroyWithoutInitIsNoOp() {
        LifecycleTestService service = new LifecycleTestService();
        service.destroy(); // ServiceStatus.CREATED disallows stop — must not throw
        assertFalse(service.isActive());
        verifyNoInteractions(service.clientForTest);
    }

    @Test
    void concurrentCancelsCloseConsumerExactlyOnce() throws Exception {
        LifecycleTestService service = configuredService();
        service.init();
        io.nop.api.core.message.IMessageSubscription subscription =
                subscribeOne(service, "t-concurrent", "sub-concurrent");

        int threads = 4;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    subscription.cancel();
                } catch (Exception e) {
                    // barrier awaits cannot realistically fail here; assertion below is the arbiter
                } finally {
                    done.countDown();
                }
            }).start();
        }
        done.await();

        verify(service.consumer, times(1)).close();
        assertTrue(subscription.isCancelled());
        service.destroy(); // no remaining subscriptions; must not hang or double-close
    }

    @Test
    void destroyCancelsSubscriptionsBeforeClosingProducerAndKeepsInFlightSendAlive()
            throws Exception {
        LifecycleTestService service = configuredService();
        service.init();

        // The in-flight send: a producer whose sendAsync future stays pending until WE
        // complete it — destroy must not break it (F-27: subscriptions close first).
        org.apache.pulsar.client.api.Producer<Object> producer =
                mock(org.apache.pulsar.client.api.Producer.class);
        org.apache.pulsar.client.api.TypedMessageBuilder<Object> typedBuilder =
                mock(org.apache.pulsar.client.api.TypedMessageBuilder.class);
        CompletableFuture<org.apache.pulsar.client.api.MessageId> pending = new CompletableFuture<>();
        when(producer.newMessage()).thenReturn(typedBuilder);
        when(typedBuilder.sendAsync()).thenReturn(pending);
        service.producersForTest().put("t-order", producer);

        io.nop.api.core.message.IMessageSubscription subscription =
                subscribeOne(service, "t-order", "sub-order");
        CompletableFuture<Void> inFlight = service
                .sendAsync("t-order", "payload", null).toCompletableFuture();
        assertFalse(inFlight.isDone(), "send must be in flight");

        service.destroy();

        // F-27 final order: the subscription's consumer closed BEFORE the producer.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(service.consumer, producer);
        inOrder.verify(service.consumer).close();
        inOrder.verify(producer).close();
        assertFalse(service.isActive());

        // The in-flight send completes successfully after destroy — it never hit a closed
        // producer, because the producer was still open while the send was accepted.
        pending.complete(mock(org.apache.pulsar.client.api.MessageId.class));
        assertTrue(inFlight.isDone());
        assertFalse(inFlight.isCompletedExceptionally());
    }

    @Test
    void wi22AccessorsFallBackToDefaultsAndReadConfig() {
        PulsarMessageService service = new PulsarMessageService();
        assertEquals(PulsarConsumeTask.DEFAULT_MAX_CONSUME_ATTEMPTS, service.maxConsumeAttempts());
        assertEquals(PulsarConsumeTask.DEFAULT_CONSUME_ERROR_BACKOFF_MAX_MS,
                service.consumeErrorBackoffMaxMs());

        PulsarConsumerConfig config = new PulsarConsumerConfig();
        config.setMaxConsumeAttempts(4);
        config.setConsumeErrorBackoffMaxMs(999L);
        service.setDefaultConsumerConfig(config);
        assertEquals(4, service.maxConsumeAttempts());
        assertEquals(999L, service.consumeErrorBackoffMaxMs());
    }

    @Test
    void sendAfterDestroyIsTypedRejectedNotNpe() throws Exception {
        LifecycleTestService service = configuredService();
        service.init();
        service.destroy();

        NopException e = assertThrows(NopException.class,
                () -> service.sendAsync("t-after", "m", null));
        assertEquals(PulsarErrors.ERR_PULSAR_SERVICE_NOT_ACTIVE.getErrorCode(), e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // WI21 (D6 layer two): native dead-letter policy wiring
    // ------------------------------------------------------------------

    @Test
    void deadLetterPolicyWiredWhenConfigured() throws Exception {
        LifecycleTestService service = configuredService();
        PulsarConsumerConfig consumerConfig = new PulsarConsumerConfig();
        consumerConfig.setDeadLetterMaxRedeliverCount(5);
        consumerConfig.setDeadLetterTopic("persistent://public/default/t-dlq-DLQ");
        service.setDefaultConsumerConfig(consumerConfig);
        service.init();

        subscribeOne(service, "t-dlq", "sub-dlq");

        org.mockito.ArgumentCaptor<org.apache.pulsar.client.api.DeadLetterPolicy> captor =
                org.mockito.ArgumentCaptor.forClass(org.apache.pulsar.client.api.DeadLetterPolicy.class);
        verify(service.builder).deadLetterPolicy(captor.capture());
        assertEquals(5, captor.getValue().getMaxRedeliverCount());
        assertEquals("persistent://public/default/t-dlq-DLQ", captor.getValue().getDeadLetterTopic());
    }

    @Test
    void deadLetterPolicyNotWiredWhenUnconfigured() throws Exception {
        LifecycleTestService service = configuredService();
        service.init();

        subscribeOne(service, "t-nodlq", "sub-nodlq");

        verify(service.builder, never()).deadLetterPolicy(
                org.mockito.ArgumentMatchers.any());
    }
}
