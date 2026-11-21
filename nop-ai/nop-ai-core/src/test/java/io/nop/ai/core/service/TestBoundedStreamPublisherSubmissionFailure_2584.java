package io.nop.ai.core.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2584 Phase 2 (audit Minor): a failing executor submission in
 * {@link BoundedStreamPublisher} is no longer silently swallowed — the first
 * rejection is WARNed (buffer retained for retry), and a SUSTAINED rejection
 * failure fails the stream loudly (terminal error state via
 * closeExceptionally) instead of hanging the buffer forever.
 */
public class TestBoundedStreamPublisherSubmissionFailure_2584 {

    /** Executor with a toggle that makes execute() throw (simulated rejection). */
    private static final class ToggleableRejectingExecutor implements Executor {
        final AtomicBoolean rejecting = new AtomicBoolean(false);
        final java.util.concurrent.Executor worker = java.util.concurrent.Executors.newSingleThreadExecutor();

        @Override
        public void execute(Runnable command) {
            if (rejecting.get()) {
                throw new java.util.concurrent.RejectedExecutionException("injected rejection");
            }
            worker.execute(command);
        }
    }

    private static final class CollectingSubscriber<T> implements Flow.Subscriber<T> {
        final List<T> items = new CopyOnWriteArrayList<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch terminal = new CountDownLatch(1);
        Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription s) {
            this.subscription = s;
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable t) {
            error.set(t);
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }
    }

    @Test
    void sustainedSubmissionFailureFailsStreamLoudly() {
        ToggleableRejectingExecutor executor = new ToggleableRejectingExecutor();
        BoundedStreamPublisher<String> publisher = new BoundedStreamPublisher<>(executor, 10);
        CollectingSubscriber<String> sub = new CollectingSubscriber<>();
        publisher.subscribe(sub);

        // first rejection: WARN, buffer retained, stream NOT terminal
        executor.rejecting.set(true);
        publisher.offer("a", null);
        assertFalse(publisher.isTerminated(),
                "the FIRST submission failure must only warn and retain the buffer");

        // sustained rejection: fail-loud terminal error
        publisher.offer("b", null);
        assertTrue(publisher.isTerminated(),
                "sustained submission failure must terminalize the stream");
        assertNotNull(publisher.terminalError());
        assertTrue(publisher.terminalError() instanceof IllegalStateException);
        assertTrue(String.valueOf(publisher.terminalError().getMessage())
                        .contains("repeatedly rejected"),
                "the fail-loud error must name the rejection cause");
    }

    @Test
    void singleTransientRejectionRecoversAndDelivers() throws Exception {
        ToggleableRejectingExecutor executor = new ToggleableRejectingExecutor();
        BoundedStreamPublisher<String> publisher = new BoundedStreamPublisher<>(executor, 10);
        CollectingSubscriber<String> sub = new CollectingSubscriber<>();
        publisher.subscribe(sub);

        // one transient rejection: recoverable
        executor.rejecting.set(true);
        publisher.offer("a", null);
        assertFalse(publisher.isTerminated());

        executor.rejecting.set(false);
        publisher.offer("b", null);
        publisher.close();

        assertTrue(sub.terminal.await(10, TimeUnit.SECONDS),
                "the stream must recover and terminate normally after the transient rejection");
        assertEquals(java.util.List.of("a", "b"), sub.items,
                "no buffered element may be lost across the transient rejection");
    }
}
