package io.nop.ai.gateway.channel;

import io.nop.integration.api.channel.IInboundMessageListener;
import io.nop.integration.api.channel.InboundChannelMessage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 2026-08-12-2050-2 / AR-4 behavior test: a mode-1 fan-out listener that
 * hangs must not permanently leak the fan-out pool thread. The
 * {@link ChannelMessageServiceImpl} timeout path must cancel the <b>raw</b>
 * {@code runAsync} future AND interrupt the worker thread (the interrupt is
 * delivered explicitly — the runtime JDK's {@code CompletableFuture.cancel}
 * does not reliably interrupt a running async task), so a cooperative
 * listener releases the pool thread and subsequent inbound dispatch keeps
 * working.
 *
 * <p>Asserts:
 * <ol>
 *   <li>{@code dispatchInbound} returns promptly (transport thread never
 *       blocked — I3 R-2-1 contract preserved);</li>
 *   <li>the hanging listener is interrupted on the {@code dispatchTimeoutMs}
 *       timeout path (interrupt flag observed on the listener thread);</li>
 *   <li>the single-thread fan-out pool is usable again afterwards (the
 *       worker was released — no thread leak).</li>
 * </ol>
 */
public class TestChannelFanOutTimeoutCancel {

    private static InboundChannelMessage inbound() {
        InboundChannelMessage m = new InboundChannelMessage();
        m.setChannelType("feishu");
        m.setText("hello");
        return m;
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition, String label) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while awaiting " + label);
            }
        }
        fail("timed out awaiting " + label);
    }

    /**
     * Listener that blocks on an interruptible latch until interrupted —
     * exactly the cooperative-listener contract of the in-repo interrupt
     * cancel pattern (SingleTurnExecutor / AgentToolDispatcher).
     */
    static final class HangingListener implements IInboundMessageListener {
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicBoolean interrupted = new AtomicBoolean();

        @Override
        public void onInbound(InboundChannelMessage message) {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void hangingListenerIsCancelledAndPoolThreadReleased() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ai-channel-fanout-timeout-test-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        try {
            ChannelMessageServiceImpl svc = new ChannelMessageServiceImpl();
            svc.setDispatchTimeoutMs(300L);
            svc.setFanOutExecutor(pool);
            HangingListener listener = new HangingListener();
            svc.subscribeInbound(listener);

            // dispatchInbound must return promptly — the transport thread is
            // never blocked by the hanging listener (I3 R-2-1 contract).
            long start = System.currentTimeMillis();
            svc.dispatchInbound(inbound());
            long dispatchElapsed = System.currentTimeMillis() - start;
            assertTrue(dispatchElapsed < 1000,
                    "dispatchInbound must not block the transport thread, took " + dispatchElapsed + "ms");
            assertTrue(listener.started.await(2, TimeUnit.SECONDS),
                    "the listener must actually run on the fan-out pool");

            // The timeout path (dispatchTimeoutMs) must cancel the raw future
            // and interrupt the worker — the listener observes the interrupt.
            awaitUntil(listener.interrupted::get,
                    "hanging listener to be interrupted on the dispatch timeout");

            // Pool-thread release: after the cancellation the single-thread
            // pool must accept and complete a follow-up task — the worker was
            // not leaked by the hanging listener.
            CompletableFuture<Boolean> probe = CompletableFuture.supplyAsync(() -> true, pool);
            assertTrue(probe.get(2, TimeUnit.SECONDS),
                    "the fan-out pool thread must be released after the timeout cancel");
            assertFalse(Thread.currentThread().isInterrupted(),
                    "the transport thread must never be interrupted by the fan-out cancel");
        } finally {
            pool.shutdownNow();
        }
    }
}
