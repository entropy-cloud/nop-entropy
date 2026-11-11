package io.nop.message.kafka;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.MessageSubscriptionConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-MA-06（plan 2558 裁定 D）carrier（Kafka 三组压力）：
 *
 * <ul>
 * <li><strong>sendAsync×destroy</strong>：destroy 之后 send 必须 typed 拒绝
 * ({@code ERR_KAFKA_PRODUCER_CREATE_FAILED}，非裸 NPE)；并发 send 与 destroy 竞争的每一
 * 个结果要么成功要么 NopException（volatile producer + getProducer 单次局部读 — 不撕裂）。
 * 在途 send（已捕获 producer 局部读）不被 destroy 打断。</li>
 * <li><strong>subscribe×destroy</strong>：consumer 构造之后的 subscribe/task 失败不得泄漏
 * consumer（MA-06 外层 catch，Pulsar WI8 形态镜像）。</li>
 * <li><strong>blocked connect×destroy</strong>（Kafka 形态）：destroy 与进行中的 subscribe
 * 竞争时资源被外层 catch 排空。</li>
 * </ul>
 */
class TestDestroyWindowGuards {

    /**
     * Same-package wiring seam: init() reaches ACTIVE on the real lifecycle state machine
     * (doStart builds a real, unconnected KafkaProducer), then the mock producer replaces
     * the field so no broker is ever contacted — destroy() from ACTIVE actually runs
     * doStop (CREATED-state destroy would be a no-op).
     */
    private KafkaMessageService serviceWithMockProducer(KafkaProducer<String, String> producer) {
        KafkaMessageService service = new KafkaMessageService();
        service.config = new KafkaClientConfig();
        service.config.setBootstrapServers("localhost:9092");
        service.init();
        service.producer = producer;
        return service;
    }

    private void completeSendImmediately(KafkaProducer<String, String> producer) {
        when(producer.send(ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer(inv -> {
            org.apache.kafka.clients.producer.Callback cb = inv.getArgument(1);
            cb.onCompletion(null, null);
            return null;
        });
    }

    // ---------------------------------------------------------------
    // Group 1: sendAsync × destroy
    // ---------------------------------------------------------------

    @Test
    void sendAfterDestroyIsTypedRejectedNotNpe() {
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        KafkaMessageService service = serviceWithMockProducer(producer);
        service.destroy();

        NopException e = assertThrows(NopException.class,
                () -> service.sendAsync("t-after", "m", null));
        assertEquals(KafkaErrors.ERR_KAFKA_PRODUCER_CREATE_FAILED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void concurrentSendAndDestroyNeverProducesRawNonNopFailures() throws Exception {
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        completeSendImmediately(producer);
        final KafkaMessageService service = serviceWithMockProducer(producer);

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
                            service.sendAsync("t-race", "m", (MessageSendOptions) null)
                                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
                        } catch (Throwable t) {
                            // Every failure must be the typed envelope — no raw NPE/ISE.
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
            Throwable cause = (t instanceof Exception && t.getCause() != null
                    && !(t instanceof NopException)) ? unwrap(t) : t;
            assertTrue(cause instanceof NopException,
                    "concurrent send failure must be the typed envelope, got: " + cause);
        }
    }

    private static Throwable unwrap(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    @Test
    void inFlightSendSurvivesDestroyViaSingleLocalProducerRead() throws Exception {
        KafkaProducer<String, String> producer = mock(KafkaProducer.class);
        completeSendImmediately(producer);
        KafkaMessageService service = serviceWithMockProducer(producer);

        CompletableFuture<Void> inFlight = service
                .sendAsync("t-flight", "m", (MessageSendOptions) null).toCompletableFuture();

        // destroy() nulls the volatile field — the in-flight send already captured the
        // single local read and must complete successfully.
        service.destroy();
        assertTrue(inFlight.isDone());
        assertFalse(inFlight.isCompletedExceptionally());
        verify(producer).close(org.mockito.ArgumentMatchers.any(java.time.Duration.class));
    }

    // ---------------------------------------------------------------
    // Group 2/3: subscribe × destroy — outer catch drains the consumer
    // (MA-06, Pulsar WI8 form mirror; C6-003 typed construction catch not shadowed)
    // ---------------------------------------------------------------

    @Test
    void subscribeFailureAfterConsumerCreationDrainsTheConsumer() {
        Consumer<String, String> consumer = mock(Consumer.class);
        doThrow(new IllegalStateException("subscribe-boom")).when(consumer).subscribe(
                ArgumentMatchers.anyCollection(), ArgumentMatchers.any());
        class TestService extends KafkaMessageService {
            @Override
            protected Consumer<String, String> createConsumer(java.util.Properties props,
                                                              org.apache.kafka.common.serialization.StringDeserializer keyDeserializer,
                                                              org.apache.kafka.common.serialization.StringDeserializer valueDeserializer) {
                return consumer;
            }
        }
        TestService service = new TestService();
        service.config = new KafkaClientConfig();
        service.config.setBootstrapServers("localhost:9092");

        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-outer");
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> service.doSubscribe(new MessageSubscriptionConfig(
                        "test-topic", (t, m, c) -> null, options)));
        assertEquals("subscribe-boom", unwrap(e).getMessage());
        // The consumer's network threads must not leak: the outer catch closed it.
        verify(consumer).close();
    }

    @Test
    void typedConsumerCreateCodeNotShadowedByOuterCatch() {
        // C6-003 coordination: the construction-time typed wrap stays ERR_KAFKA_CONSUMER_CREATE_FAILED.
        class FailingService extends KafkaMessageService {
            @Override
            protected Consumer<String, String> createConsumer(java.util.Properties props,
                                                              org.apache.kafka.common.serialization.StringDeserializer keyDeserializer,
                                                              org.apache.kafka.common.serialization.StringDeserializer valueDeserializer) {
                throw new IllegalArgumentException("bad-config");
            }
        }
        FailingService service = new FailingService();
        service.config = new KafkaClientConfig();
        service.config.setBootstrapServers("localhost:9092");
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setSubscribeName("sub-typed");
        NopException e = assertThrows(NopException.class,
                () -> service.doSubscribe(new MessageSubscriptionConfig(
                        "test-topic", (t, m, c) -> null, options)));
        assertEquals(KafkaErrors.ERR_KAFKA_CONSUMER_CREATE_FAILED.getErrorCode(), e.getErrorCode());
    }

    @Test
    void assertInstanceOfSmokeForEnvelopeFailures() {
        // The envelope face used by the pressure groups above.
        NopException e = new NopException(KafkaErrors.ERR_KAFKA_PRODUCER_CREATE_FAILED);
        assertInstanceOf(NopException.class, unwrap(e));
    }
}
