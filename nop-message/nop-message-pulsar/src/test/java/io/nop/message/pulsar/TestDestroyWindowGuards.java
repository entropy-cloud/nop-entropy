package io.nop.message.pulsar;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.MessageSubscribeOptions;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 4.1-MA-06（plan 2558 裁定 D）carrier（Pulsar 三组压力，全部内存 stub 无 broker）：
 *
 * <ul>
 * <li><strong>subscribe×destroy</strong>：checkIsActive 前置交换 —— destroy 后 subscribe
 * 在触碰 client/builder 之前 typed 拒绝（不再可能 NPE）。</li>
 * <li><strong>sendAsync×destroy</strong>：并发 send 与 destroy 竞争的每一个结果要么成功要么
 * NopException 信封（C3-004 envelope；NOT_ACTIVE 残差分叉已登记 owner doc）。</li>
 * <li><strong>blocked connect×destroy</strong>：挂在 createAsync 上的 getProducer 等待者，
 * destroy 必须解除其阻塞（doStop 取消占位）—— 排空语义，无永久悬挂。</li>
 * </ul>
 */
class TestDestroyWindowGuards {

    /** In-memory stub mirroring TestPulsarServiceLifecycle.LifecycleTestService. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static class GuardTestService extends PulsarMessageService {
        final PulsarClient clientForTest = mock(PulsarClient.class);
        final org.apache.pulsar.client.api.ProducerBuilder producerBuilder =
                mock(org.apache.pulsar.client.api.ProducerBuilder.class);
        final org.apache.pulsar.client.api.ConsumerBuilder consumerBuilder =
                mock(org.apache.pulsar.client.api.ConsumerBuilder.class);
        final org.apache.pulsar.client.api.Consumer<Object> consumer =
                mock(org.apache.pulsar.client.api.Consumer.class);

        volatile CompletableFuture<org.apache.pulsar.client.api.Producer<?>> createAsyncFuture =
                new CompletableFuture<>();

        GuardTestService() {
            try {
                when(consumer.receive(org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
                            Thread.sleep(30);
                            return null;
                        });
                when(producerBuilder.topic(org.mockito.ArgumentMatchers.anyString()))
                        .thenReturn(producerBuilder);
                when(consumerBuilder.topic(org.mockito.ArgumentMatchers.anyString()))
                        .thenReturn(consumerBuilder);
                when(consumerBuilder.subscriptionName(org.mockito.ArgumentMatchers.anyString()))
                        .thenReturn(consumerBuilder);
                when(consumerBuilder.subscribe()).thenReturn(consumer);
            } catch (org.apache.pulsar.client.api.PulsarClientException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected PulsarClient createClient() {
            return clientForTest;
        }

        @Override
        protected org.apache.pulsar.client.api.ConsumerBuilder newConsumerBuilder(
                org.apache.pulsar.client.api.Schema<?> schema) {
            return consumerBuilder;
        }
    }

    private GuardTestService configuredService() {
        GuardTestService service = new GuardTestService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl("pulsar://localhost:6650");
        service.setConfig(config);
        return service;
    }

    // ---------------------------------------------------------------
    // Group 1: subscribe × destroy (checkIsActive 前置交换)
    // ---------------------------------------------------------------

    @Test
    void subscribeAfterDestroyIsTypedRejectedBeforeBuilderTouch() {
        GuardTestService service = configuredService();
        service.init();
        service.destroy();

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-after-destroy");
        // checkIsActive runs BEFORE newConsumerBuilder: a typed rejection, never an NPE
        // and never a builder interaction on a dead client.
        assertThrows(NopException.class,
                () -> service.subscribe("t-after-destroy", (t, m, c) -> null, options));
    }

    // ---------------------------------------------------------------
    // Group 2: sendAsync × destroy (envelope face under pressure)
    // ---------------------------------------------------------------

    @Test
    void concurrentSendAndDestroyNeverProducesRawNonNopFailures() throws Exception {
        GuardTestService service = configuredService();
        service.init();
        // Pre-install a ready producer whose message face works; destroy drains it
        // mid-flight. Every failure must surface as the C3-004 typed envelope.
        when(service.clientForTest.newProducer(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Schema<?>>any())).thenReturn(service.producerBuilder);
        when(service.producerBuilder.createAsync()).thenAnswer(
                inv -> service.createAsyncFuture);
        service.createAsyncFuture.complete(readyProducer());

        int threads = 6;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        AtomicBoolean stop = new AtomicBoolean();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    barrier.await();
                    while (!stop.get()) {
                        try {
                            service.sendAsync("t-race", "m", null)
                                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            failures.add(t);
                        }
                    }
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        service.destroy();
        Thread.sleep(100);
        stop.set(true);
        assertTrue(done.await(10, TimeUnit.SECONDS));

        for (Throwable t : failures) {
            Throwable typed = typedCause(t);
            assertInstanceOf(NopException.class, typed,
                    "concurrent send failure must be the typed envelope, got: " + t);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private org.apache.pulsar.client.api.Producer<?> readyProducer() {
        org.apache.pulsar.client.api.Producer producer =
                mock(org.apache.pulsar.client.api.Producer.class);
        org.apache.pulsar.client.api.TypedMessageBuilder typedBuilder =
                mock(org.apache.pulsar.client.api.TypedMessageBuilder.class);
        when(producer.newMessage()).thenReturn(typedBuilder);
        when(typedBuilder.sendAsync()).thenReturn(CompletableFuture.completedFuture(
                mock(org.apache.pulsar.client.api.MessageId.class)));
        return producer;
    }

    /** First NopException in the cause chain (the typed envelope, not its root cause). */
    private static Throwable typedCause(Throwable t) {
        while (t != null) {
            if (t instanceof NopException) {
                return t;
            }
            t = t.getCause();
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Group 3: blocked connect × destroy (waiters must be released)
    // ---------------------------------------------------------------

    @Test
    void destroyReleasesWaitersBlockedOnProducerCreation() throws Exception {
        GuardTestService service = configuredService();
        service.init();
        when(service.clientForTest.newProducer(org.mockito.ArgumentMatchers
                .<org.apache.pulsar.client.api.Schema<?>>any())).thenReturn(service.producerBuilder);
        // The connect never completes: the waiter parks in getProducer's bounded wait
        // (sendAsync runs on its own thread — getProducer blocks synchronously).
        CompletableFuture<org.apache.pulsar.client.api.Producer<?>> blocked =
                new CompletableFuture<>();
        service.createAsyncFuture = blocked;
        when(service.producerBuilder.createAsync()).thenReturn(blocked);

        java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>> sendRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread waiter = new Thread(() -> sendRef.set(
                service.sendAsync("t-blocked", "m", null).toCompletableFuture()));
        waiter.start();
        Thread.sleep(300); // give the waiter time to park inside getProducer
        assertFalse(sendRef.get() != null,
                "the waiter must still be parked on the blocked connect");

        // destroy() cancels the placeholder: the waiter wakes exceptionally (bounded).
        service.destroy();
        waiter.join(10000);
        assertFalse(waiter.isAlive(), "destroy must release the parked waiter");
        assertTrue(sendRef.get() != null, "the parked sendAsync must have returned");
        assertTrue(sendRef.get().isCompletedExceptionally(),
                "destroy must release waiters blocked on a hung producer connect");
    }
}
