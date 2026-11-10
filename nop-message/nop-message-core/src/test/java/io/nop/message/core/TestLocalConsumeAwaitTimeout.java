package io.nop.message.core;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.util.FutureHelper;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.1-MA-01（plan 2558 裁定 A·Local）carrier: the Local consume wait site is bounded by
 * {@code consumeAwaitTimeoutMs}. A hung handler (never-completing CompletionStage) must
 * hit the DEDICATED timeout branch — {@code dispatchConsumeFailure} 留证丢弃 + WARN,
 * attempt=0 semantics per merge-adjudication §五-6 — and NOT a poison/replay budget
 * path (exactly one failure callback, no redelivery loop). The consume pipeline liveness
 * flag must keep reporting active: the run-loop survives a timed-out handler.
 */
public class TestLocalConsumeAwaitTimeout {

    @Test
    void hungHandlerTimesOutDispatchesEvidenceAndKeepsPipelineAlive() throws Exception {
        LocalMessageService service = new LocalMessageService();
        service.setConsumeAwaitTimeoutMs(50);

        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        CompletableFuture<Object> never = new CompletableFuture<>();
        AtomicInteger deliveries = new AtomicInteger();
        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                deliveries.incrementAndGet();
                return never;
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                failures.add(failure);
            }
        };
        service.subscribe("t-await", consumer, null);
        Object message = "hung-payload";

        // send must return (bounded wait), never hang the sender thread
        assertReturnsQuickly(() -> service.send("t-await", message, null));

        assertEquals(1, deliveries.get(), "no retry/loop behind the timeout (attempt=0 semantics)");
        assertEquals(1, failures.size(),
                "the timeout takes the dedicated dispatchConsumeFailure branch: exactly one "
                        + "evidence callback, no poison-budget redelivery loop");
        ConsumeFailure failure = failures.get(0);
        assertSame(message, failure.getMessage());
        assertEquals(0, failure.getAttempt(), "attempt=0 semantics per §五-6");
        assertTrue(isTimeoutEvidence(failure.getThrowable()),
                "the dispatched throwable must be the consume-await timeout evidence, got: "
                        + failure.getThrowable());

        // run-loop liveness exposure: the pipeline is NOT shut down by the timeout —
        // the flag reflects shutdown state only, never handler progress.
        assertTrue(service.isConsumePipelineActive(),
                "the consume pipeline must stay alive after a timed-out handler");

        service.destroy();
        assertFalse(service.isConsumePipelineActive(), "destroy must flip the liveness flag");
    }

    @Test
    void boundedWaitCompletesWhenHandlerFinishesInTime() {
        LocalMessageService service = new LocalMessageService();
        service.setConsumeAwaitTimeoutMs(5000);
        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                CompletableFuture<Object> done = new CompletableFuture<>();
                done.complete("done");
                return done;
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                failures.add(failure);
            }
        };
        service.subscribe("t-fast", consumer, null);
        service.send("t-fast", "fast-payload", null);
        assertTrue(failures.isEmpty(),
                "a handler completing within the bound must not dispatch any failure");
        assertTrue(service.isConsumePipelineActive());
    }

    @Test
    void nonCompletableFutureStageIsTypedRejectedNotSilentlyAwaited() throws Exception {
        LocalMessageService service = new LocalMessageService();
        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        // A CompletionStage whose toCompletableFuture() is an illegal shape (裁定 A: typed
        // error, never a silent fallback to an unbounded wait).
        CompletableFuture<Object> illegal = new CompletableFuture<>() {
            @Override
            public CompletableFuture<Object> toCompletableFuture() {
                throw new UnsupportedOperationException("injected illegal stage shape");
            }
        };
        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return illegal;
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                failures.add(failure);
            }
        };
        service.subscribe("t-notfuture", consumer, null);

        assertReturnsQuickly(() -> service.send("t-notfuture", "m", null));

        assertEquals(1, failures.size(),
                "an illegal (non-CompletableFuture) stage shape is a typed rejection, not a "
                        + "silent unbounded await (裁定 A)");
        assertTrue(failures.get(0).getThrowable() instanceof NopException,
                "typed error expected, got: " + failures.get(0).getThrowable());
    }

    private static boolean isTimeoutEvidence(Throwable e) {
        return FutureHelper.isTimeoutException(e) || FutureHelper.isTimeoutException(e.getCause());
    }

    private static void assertReturnsQuickly(Runnable action) throws InterruptedException {
        Thread sender = new Thread(action, "test-sender");
        sender.setDaemon(true);
        sender.start();
        sender.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(sender.isAlive(),
                "the consume wait must be bounded — the sender thread must not hang");
    }
}
