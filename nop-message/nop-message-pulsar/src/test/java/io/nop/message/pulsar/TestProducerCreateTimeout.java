package io.nop.message.pulsar;

import io.nop.api.core.exceptions.NopException;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.1-C8-007（plan 2558 裁定 L）carrier：producer creation is a placeholder + async
 * connect with a bounded timeout — a hung/blocked broker connect no longer holds the
 * map's bin lock (the old {@code computeIfAbsent(topic, k -> buildProducer(...))} shape
 * serially amplified one slow connect into every caller).
 *
 * <ul>
 * <li>Blocked createAsync → {@code ERR_PULSAR_PRODUCER_CREATE_FAILED} after the bounded
 * wait (floor 1s, from {@code PulsarProducerConfig.sendTimeout}).</li>
 * <li>Failure is never cached negatively: the placeholder self-removes and the next
 * caller retries (createAsync invoked again).</li>
 * <li>Two concurrent first-callers share ONE creation — no serial amplification.</li>
 * <li>Non-shadowing declaration（C1-004 同站点异机制）: this carrier pins the BLOCKED-
 * CONNECT face; C1-004's destroy-window typed rejection is pinned in
 * {@code TestPulsarServiceLifecycle} / {@code TestDestroyWindowGuards}. Neither masks
 * the other.</li>
 * </ul>
 */
class TestProducerCreateTimeout {

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class BlockedCreateService extends PulsarMessageService {
        final PulsarClient clientForTest = mock(PulsarClient.class);
        final org.apache.pulsar.client.api.ProducerBuilder producerBuilder =
                mock(org.apache.pulsar.client.api.ProducerBuilder.class);
        final AtomicInteger createInvocations = new AtomicInteger();
        // The async connect's completion is gated by this latch — createAsync returns a
        // LIVE future immediately (real client shape), the failure arrives later.
        volatile CountDownLatch releaseCreate;

        BlockedCreateService() {
            try {
                when(clientForTest.newProducer(org.mockito.ArgumentMatchers
                        .<org.apache.pulsar.client.api.Schema<?>>any()))
                        .thenReturn(producerBuilder);
                when(producerBuilder.topic(org.mockito.ArgumentMatchers.anyString()))
                        .thenReturn(producerBuilder);
                when(producerBuilder.createAsync()).thenAnswer(inv -> {
                    createInvocations.incrementAndGet();
                    CompletableFuture future = new CompletableFuture<>();
                    CountDownLatch latch = releaseCreate;
                    if (latch == null) {
                        future.completeExceptionally(new IllegalStateException("connect-blocked"));
                    } else {
                        Thread completer = new Thread(() -> {
                            try {
                                latch.await(15, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            future.completeExceptionally(new IllegalStateException("connect-blocked"));
                        });
                        completer.setDaemon(true);
                        completer.start();
                    }
                    return future;
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected PulsarClient createClient() {
            return clientForTest;
        }
    }

    private BlockedCreateService service() {
        BlockedCreateService service = new BlockedCreateService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl("pulsar://localhost:6650");
        service.setConfig(config);
        PulsarProducerConfig producerConfig = new PulsarProducerConfig();
        producerConfig.setSendTimeout(1100); // bounded wait = max(1100, 1000 floor)
        service.setDefaultProducerConfig(producerConfig);
        service.init();
        return service;
    }

    /** First NopException in the cause chain (the typed envelope, not its root cause). */
    private static NopException typedCause(Throwable t) {
        while (t != null) {
            if (t instanceof NopException) {
                return (NopException) t;
            }
            t = t.getCause();
        }
        return null;
    }

    @Test
    void blockedConnectTimesOutWithTypedProducerCreateFailed() {
        BlockedCreateService service = service();
        try {
            service.releaseCreate = new CountDownLatch(1); // the connect never completes

            long start = System.nanoTime();
            NopException e = assertThrows(NopException.class,
                    () -> service.getProducer("t-blocked"));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertEquals(PulsarErrors.ERR_PULSAR_PRODUCER_CREATE_FAILED.getErrorCode(),
                    e.getErrorCode());
            assertTrue(elapsedMs >= 1000,
                    "the bounded wait must hold at least the floor, took " + elapsedMs + "ms");
            assertTrue(elapsedMs < 10000, "the wait must be bounded, took " + elapsedMs + "ms");
        } finally {
            service.destroy();
        }
    }

    @Test
    void failedCreationIsNotCachedNegativelyNextCallerRetries() {
        BlockedCreateService service = service();
        try {
            service.releaseCreate = new CountDownLatch(1);
            assertThrows(NopException.class, () -> service.getProducer("t-retry"));

            // Release round 1's connect, then retry: a NEW createAsync invocation fires
            // (placeholder removed — the negative was never cached).
            service.releaseCreate.countDown();
            service.releaseCreate = new CountDownLatch(1);
            assertThrows(NopException.class, () -> service.getProducer("t-retry"));
            assertTrue(service.createInvocations.get() >= 2,
                    "the second caller must retry the connect, not read a cached failure");
        } finally {
            service.releaseCreate = null; // release the parked completer threads
            service.destroy();
        }
    }

    @Test
    void concurrentFirstCallersShareOneCreation() throws Exception {
        BlockedCreateService service = service();
        try {
            CountDownLatch release = new CountDownLatch(1);
            service.releaseCreate = release;

            int callers = 3;
            CyclicBarrier barrier = new CyclicBarrier(callers);
            CountDownLatch done = new CountDownLatch(callers);
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            for (int i = 0; i < callers; i++) {
                new Thread(() -> {
                    try {
                        barrier.await();
                        try {
                            service.getProducer("t-shared");
                        } catch (Throwable t) {
                            failures.add(t);
                        } finally {
                            done.countDown();
                        }
                    } catch (Exception barrierError) {
                        failures.add(barrierError);
                        done.countDown();
                    }
                }).start();
            }
            assertTrue(done.await(15, TimeUnit.SECONDS));
            // All callers failed together on the ONE creation (no serial amplification:
            // a bin-locked computeIfAbsent would have run createAsync 3 times serially).
            assertEquals(1, service.createInvocations.get(),
                    "concurrent first-callers must share a single creation attempt");
            assertEquals(callers, failures.size());
            for (Throwable t : failures) {
                NopException typed = typedCause(t);
                assertTrue(typed != null,
                        "failure must carry the typed envelope, got: " + t);
            }
            verify(service.producerBuilder, times(1)).createAsync();
        } finally {
            service.releaseCreate = null;
            service.destroy();
        }
    }
}
