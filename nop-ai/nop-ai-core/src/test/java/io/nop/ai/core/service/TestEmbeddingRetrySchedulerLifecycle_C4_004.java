package io.nop.ai.core.service;

import io.nop.commons.lang.IDestroyable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.1-C4-004 regression (plan 2498 Phase 4): the internally-created
 * embedding retry scheduler ({@code nop-ai-embedding-retry}) must have a
 * bounded close path. Pre-fix the lazy pool had no shutdown anywhere —
 * {@code setRetryScheduler} swaps orphaned the old pool's core thread forever
 * and the bean definition registered no destroy hook.
 *
 * <p>Both tests compile against the pre-fix API surface so the red baseline is
 * compilable: the swap test fails behaviourally (orphan thread never
 * terminates), the destroy-hook test fails on the missing {@link IDestroyable}
 * implementation.
 */
public class TestEmbeddingRetrySchedulerLifecycle_C4_004 {

    private static final String INTERNAL_THREAD_NAME = "nop-ai-embedding-retry";

    private static boolean threadAlive(String name) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> name.equals(t.getName()) && t.isAlive());
    }

    private static void awaitUntil(String message, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    /** Forces the lazy creation of the internal retry scheduler. */
    private static EmbeddingServiceImpl serviceWithInternalSchedulerCreated() {
        EmbeddingServiceImpl svc = new EmbeddingServiceImpl();
        svc.scheduleRetry(0, () -> {
        });
        awaitUntil("precondition: internal retry scheduler thread must be created",
                () -> threadAlive(INTERNAL_THREAD_NAME));
        return svc;
    }

    @Test
    void swappedOutInternalSchedulerTerminatesWithinBound_C4_004() {
        EmbeddingServiceImpl svc = serviceWithInternalSchedulerCreated();

        // swap-to-default: the internal pool this service created must be
        // bounded-terminated, not orphaned
        svc.setRetryScheduler(null);

        awaitUntil("internal retry scheduler thread must terminate after the swap "
                + "(no orphan nop-ai-embedding-retry thread)",
                () -> !threadAlive(INTERNAL_THREAD_NAME));
    }

    @Test
    void destroyHookTerminatesInternalScheduler_C4_004() {
        EmbeddingServiceImpl svc = serviceWithInternalSchedulerCreated();

        // the bean lifecycle close hook (destroy-method="stop" / IDestroyable)
        IDestroyable destroyable = (IDestroyable) svc;
        destroyable.destroy();

        awaitUntil("internal retry scheduler thread must terminate after destroy",
                () -> !threadAlive(INTERNAL_THREAD_NAME));
    }

    @Test
    void stopDoesNotTerminateInjectedScheduler_C4_004() {
        EmbeddingServiceImpl svc = new EmbeddingServiceImpl();
        ScheduledExecutorService injected = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "c4-004-injected-scheduler"));
        try {
            // spawn the injected pool's worker (pool threads are lazy)
            injected.schedule(() -> {
            }, 0, TimeUnit.MILLISECONDS);
            awaitUntil("precondition: injected scheduler thread must be created",
                    () -> threadAlive("c4-004-injected-scheduler"));

            svc.setRetryScheduler(injected);
            ((IDestroyable) svc).destroy();

            // an injected scheduler is not owned by this service — it must
            // survive the service's own close path
            assertTrue(threadAlive("c4-004-injected-scheduler"),
                    "stop/destroy must not terminate a scheduler the service does not own");
        } finally {
            injected.shutdownNow();
        }
    }

    @Test
    void serviceRemainsUsableAfterCloseViaLazyRecreate_C4_004() {
        EmbeddingServiceImpl svc = serviceWithInternalSchedulerCreated();

        ((IDestroyable) svc).destroy();
        awaitUntil("internal scheduler terminated after destroy",
                () -> !threadAlive(INTERNAL_THREAD_NAME));

        // a subsequent call lazily recreates a fresh internal pool — no
        // RejectedExecutionException from the closed pool
        svc.scheduleRetry(0, () -> {
        });
        awaitUntil("a fresh internal retry scheduler must be created after close",
                () -> threadAlive(INTERNAL_THREAD_NAME));

        svc.setRetryScheduler(null);
    }
}
