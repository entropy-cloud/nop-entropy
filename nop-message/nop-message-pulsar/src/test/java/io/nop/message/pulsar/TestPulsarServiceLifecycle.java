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
    void pulsarCreationFailuresCarryTypedCodes() throws Exception {
        // Client-creation failure → typed ERR_PULSAR_CLIENT_CREATE_FAILED (WI26/F-55).
        // The wrap lives at the doStart call site, so a createClient override that throws
        // IS wrapped (call-site wrap cannot be bypassed by overrides).
        LifecycleTestService failing = new LifecycleTestService() {
            @Override
            protected PulsarClient createClient() {
                throw new IllegalStateException("client-down");
            }
        };
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl("pulsar://localhost:6650");
        failing.setConfig(config);
        NopException initError = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                failing::init);
        org.junit.jupiter.api.Assertions.assertEquals(
                PulsarErrors.ERR_PULSAR_CLIENT_CREATE_FAILED.getErrorCode(),
                initError.getErrorCode());
        org.junit.jupiter.api.Assertions.assertNotNull(initError.getCause(),
                "the underlying client-creation failure must be preserved as cause");

        // Producer-creation failure → typed ERR_PULSAR_PRODUCER_CREATE_FAILED.
        LifecycleTestService producerService = new LifecycleTestService();
        PulsarClientConfig producerConfig = new PulsarClientConfig();
        producerConfig.setServiceUrl("pulsar://localhost:6650");
        producerService.setConfig(producerConfig);
        org.apache.pulsar.client.api.PulsarClient producerClient =
                mock(org.apache.pulsar.client.api.PulsarClient.class);
        producerService.clientForTest = producerClient;
        // init() first: the not-active guard (F-30) fires before newProducer otherwise.
        producerService.init();
        when(producerClient.newProducer(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Schema<?>>any()))
                .thenThrow(new IllegalStateException("producer-down"));
        NopException producerError = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> producerService.buildProducer("t-typed",
                        org.apache.pulsar.client.api.Schema.STRING));
        org.junit.jupiter.api.Assertions.assertEquals(
                PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED.getErrorCode(),
                producerError.getErrorCode());
        org.junit.jupiter.api.Assertions.assertTrue(
                producerError.getCause() instanceof IllegalStateException,
                "cause chain must be preserved");
        producerService.destroy();
    }

    @Test
    void subscribeFailureCarriesTypedConsumerCreateCode() throws Exception {
        LifecycleTestService service = configuredService();
        service.init();
        // WI26 (F-55): subscribe() failure → typed consumer-create code (narrow wrap).
        when(service.builder.subscribe()).thenThrow(
                new org.apache.pulsar.client.api.PulsarClientException("subscribe-down"));
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-typed");
        NopException e = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> service.subscribe("t-typed", (t, m, c) -> null, options));
        org.junit.jupiter.api.Assertions.assertEquals(
                PulsarErrors.ERR_PULSAR_CONSUMER_CREATE_FAILED.getErrorCode(), e.getErrorCode());
        service.destroy();
    }

    // ------------------------------------------------------------------
    // WI27 (D4/F-32): neutral mode mapping and concurrency determinism
    // ------------------------------------------------------------------

    private static LifecycleTestService configuredTestService() {
        LifecycleTestService service = new LifecycleTestService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl("pulsar://localhost:6650");
        service.setConfig(config);
        return service;
    }

    @Test
    void subscriptionModeMapsToPulsarTypes() {
        MessageSubscribeOptions single = new MessageSubscribeOptions();
        single.setSubscriptionMode(io.nop.api.core.message.SubscriptionMode.SINGLE_ACTIVE);
        MessageSubscribeOptions balanced = new MessageSubscribeOptions();
        balanced.setSubscriptionMode(io.nop.api.core.message.SubscriptionMode.LOAD_BALANCED);
        balanced.setConcurrency(2);
        MessageSubscribeOptions keyed = new MessageSubscribeOptions();
        keyed.setSubscriptionMode(io.nop.api.core.message.SubscriptionMode.ORDERED_BY_KEY);
        keyed.setConcurrency(2);

        org.junit.jupiter.api.Assertions.assertEquals(
                org.apache.pulsar.client.api.SubscriptionType.Exclusive,
                PulsarMessageService.resolveEffectivePulsarType(single));
        org.junit.jupiter.api.Assertions.assertEquals(
                org.apache.pulsar.client.api.SubscriptionType.Shared,
                PulsarMessageService.resolveEffectivePulsarType(balanced));
        org.junit.jupiter.api.Assertions.assertEquals(
                org.apache.pulsar.client.api.SubscriptionType.Key_Shared,
                PulsarMessageService.resolveEffectivePulsarType(keyed));
    }

    @Test
    void singleActiveClampsConcurrencyToOne() throws Exception {
        LifecycleTestService service = configuredTestService();
        service.init();
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-clamp");
        options.setSubscriptionMode(io.nop.api.core.message.SubscriptionMode.SINGLE_ACTIVE);
        options.setConcurrency(3);
        io.nop.api.core.message.IMessageSubscription sub =
                service.subscribe("t-clamp", (t, m, c) -> null, options);
        sub.cancel();
        // F-32: exactly ONE consumer — the previous behavior failed the whole subscribe.
        verify(service.builder, times(1)).subscribe();
    }

    @Test
    void unconfiguredTypeWithConcurrencyRunsShared() throws Exception {
        LifecycleTestService service = configuredTestService();
        service.init();
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-defshared");
        options.setConcurrency(2);
        io.nop.api.core.message.IMessageSubscription sub =
                service.subscribe("t-defshared", (t, m, c) -> null, options);
        try {
            org.mockito.ArgumentCaptor<org.apache.pulsar.client.api.SubscriptionType> captor =
                    org.mockito.ArgumentCaptor.forClass(org.apache.pulsar.client.api.SubscriptionType.class);
            // concurrency=2 → doSubscribe runs twice; every consumer gets Shared.
            verify(service.builder, times(2)).subscriptionType(captor.capture());
            org.junit.jupiter.api.Assertions.assertEquals(
                    org.apache.pulsar.client.api.SubscriptionType.Shared, captor.getValue(),
                    "unconfigured type + concurrency>1 must run Shared, not the exclusive default");
            // MultiMessageSubscription wraps both consumers — the times(2) wiring
            // assertion above is the consumer-count proof.
        } finally {
            sub.cancel();
        }
    }

    @Test
    void modeTypeConflictPrefersMode() throws Exception {
        LifecycleTestService service = configuredTestService();
        service.init();
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-conflict");
        options.setSubscriptionMode(io.nop.api.core.message.SubscriptionMode.LOAD_BALANCED);
        options.setSubscriptionType(io.nop.api.core.message.SubscriptionType.Exclusive);
        io.nop.api.core.message.IMessageSubscription sub =
                service.subscribe("t-conflict", (t, m, c) -> null, options);
        try {
            org.mockito.ArgumentCaptor<org.apache.pulsar.client.api.SubscriptionType> captor =
                    org.mockito.ArgumentCaptor.forClass(org.apache.pulsar.client.api.SubscriptionType.class);
            verify(service.builder).subscriptionType(captor.capture());
            org.junit.jupiter.api.Assertions.assertEquals(
                    org.apache.pulsar.client.api.SubscriptionType.Shared, captor.getValue(),
                    "mode must take precedence over the deprecated legacy type");
        } finally {
            sub.cancel();
        }
    }

    @Test
    void loadBalancedModeWithDlqGetsSharedTypeNotFalseWarning() throws Exception {
        LifecycleTestService service = configuredTestService();
        PulsarConsumerConfig consumerConfig = new PulsarConsumerConfig();
        consumerConfig.setDeadLetterMaxRedeliverCount(5);
        service.setDefaultConsumerConfig(consumerConfig);
        service.init();

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-dlq-mode");
        options.setSubscriptionMode(io.nop.api.core.message.SubscriptionMode.LOAD_BALANCED);
        io.nop.api.core.message.IMessageSubscription subscription =
                service.subscribe("t-dlq-mode", (t, m, c) -> null, options);
        try {
            // M1 regression nail: the resolver single-source feeds the DLQ check — the
            // effective type IS Shared, so the "will not trigger" warn-once must NOT have
            // fired for this subscription.
            verify(service.builder).subscriptionType(
                    org.apache.pulsar.client.api.SubscriptionType.Shared);
        } finally {
            subscription.cancel();
        }
    }

    @Test
    void batchReceivePolicyWiredWithClientDefaultFloor() throws Exception {
        LifecycleTestService service = configuredService();
        service.init();

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveCount(25);
        io.nop.api.core.message.IMessageSubscription subscription =
                service.subscribe("t-batch", (t, m, c) -> null, options);
        try {
            org.mockito.ArgumentCaptor<org.apache.pulsar.client.api.BatchReceivePolicy> captor =
                    org.mockito.ArgumentCaptor.forClass(org.apache.pulsar.client.api.BatchReceivePolicy.class);
            verify(service.builder).batchReceivePolicy(captor.capture());
            org.apache.pulsar.client.api.BatchReceivePolicy policy = captor.getValue();
            assertEquals(25, policy.getMaxNumMessages());
            // WI25 (audit B-1): the client-default latency floor is preserved — a
            // maxNumMessages-only gate would make low-traffic topics wait unboundedly.
            assertEquals(org.apache.pulsar.client.api.BatchReceivePolicy.DEFAULT_POLICY.getMaxNumBytes(),
                    policy.getMaxNumBytes());
            assertEquals(100L, policy.getTimeoutMs());
        } finally {
            subscription.cancel();
        }
    }

    @Test
    void batchReceivePolicyNotWiredWhenUnconfigured() throws Exception {
        LifecycleTestService service = configuredService();
        service.init();

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        io.nop.api.core.message.IMessageSubscription subscription =
                service.subscribe("t-nobatch", (t, m, c) -> null, options);
        try {
            verify(service.builder, never()).batchReceivePolicy(
                    org.mockito.ArgumentMatchers.any());
        } finally {
            subscription.cancel();
        }
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
