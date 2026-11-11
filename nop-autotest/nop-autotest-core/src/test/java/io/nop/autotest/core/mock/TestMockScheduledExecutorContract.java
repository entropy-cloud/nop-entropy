package io.nop.autotest.core.mock;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for {@link MockScheduledExecutor} (plan 2563 MA-04, C10-003). Five symmetric legs
 * asserted against the semantics of {@code DefaultScheduledExecutor} (compared by hand in the
 * fixture per design R1-M5 - no real executor is started, avoiding real-thread + sleep flakiness):
 *
 * <ol>
 * <li>leg 1 - delayed expiry: a delay>0 task fires only after the delay (TimeUnit-faithful) has
 * been advanced through the internal virtual clock;</li>
 * <li>leg 2 - periodic single round per manual drive: fixed-rate / fixed-delay tasks are not
 * re-armed (boundary declaration case, design R1-B2);</li>
 * <li>leg 3 - reject after destroy;</li>
 * <li>leg 4 - cancel removes the task (source promise, cancel propagates);</li>
 * <li>leg 5 - destroy drains pending tasks with completeExceptionally.</li>
 * </ol>
 */
public class TestMockScheduledExecutorContract {

    private final MockScheduledExecutor executor = new MockScheduledExecutor();

    // leg 1: delayed expiry + TimeUnit faithful conversion (mutation chain 1 target)
    @Test
    public void delayedTaskFiresOnlyAfterUnitFaithfulAdvance() throws Exception {
        CompletableFuture<String> promise = executor.schedule(() -> "v", 500, TimeUnit.MILLISECONDS);
        assertNull(executor.triggerNext());

        executor.expireTasks(499);
        assertNull(executor.triggerNext());

        executor.expireTasks(1);
        assertEquals("v", executor.triggerNext());
        assertTrue(promise.isDone());
        assertFalse(promise.isCompletedExceptionally());

        // SECONDS conversion must not be read as milliseconds
        CompletableFuture<String> secondsPromise = executor.schedule(() -> "sec", 1, TimeUnit.SECONDS);
        executor.expireTasks(999);
        assertNull(executor.triggerNext());
        executor.expireTasks(1);
        assertEquals("sec", executor.triggerNext());
        assertTrue(secondsPromise.isDone());
    }

    // R2-M3 semantic pin: expireTasks advances the virtual clock anchor for subsequent enqueues
    @Test
    public void virtualClockAnchorsSubsequentEnqueues() {
        executor.expireTasks(1000);
        CompletableFuture<String> promise = executor.schedule(() -> "later", 500, TimeUnit.MILLISECONDS);
        executor.expireTasks(400);
        assertNull(executor.triggerNext());
        executor.expireTasks(100);
        assertEquals("later", executor.triggerNext());
        assertTrue(promise.isDone());
    }

    // leg 2: periodic kinds - single round per manual drive, no re-arm (chain target of boundary case)
    @Test
    public void fixedRateRunsSingleRoundPerManualDrive() {
        AtomicInteger count = new AtomicInteger();
        executor.scheduleAtFixedRate(count::incrementAndGet, 0, 1000, TimeUnit.MILLISECONDS);
        executor.triggerNext();
        assertEquals(1, count.get());
        // boundary declaration: no re-arm - the same task is not delivered twice
        assertNull(executor.triggerNext());
        assertEquals(1, count.get());
    }

    @Test
    public void fixedDelayRunsSingleRoundPerManualDrive() {
        AtomicInteger count = new AtomicInteger();
        executor.scheduleWithFixedDelay(count::incrementAndGet, 0, 1000, TimeUnit.MILLISECONDS);
        executor.triggerNext();
        assertEquals(1, count.get());
        assertNull(executor.triggerNext());
        assertEquals(1, count.get());
    }

    // leg 3: reject after destroy (mutation chain 4 target)
    @Test
    public void rejectSubmitAfterDestroy() {
        executor.destroy();
        assertThrows(NopException.class, () -> executor.schedule(() -> "x", 1, TimeUnit.MILLISECONDS));
        assertThrows(NopException.class, () -> executor.scheduleAtFixedRate(() -> {
        }, 0, 1, TimeUnit.MILLISECONDS));
        assertThrows(NopException.class, () -> executor.scheduleWithFixedDelay(() -> {
        }, 0, 1, TimeUnit.MILLISECONDS));
        assertThrows(NopException.class, () -> executor.submit(() -> "y"));
    }

    // leg 4: cancel on the returned (source) promise removes the task (mutation chain 3 target).
    // The virtual clock is advanced past the deadline: a cancelled task whose cleanup callback
    // was dropped would still be delivered.
    @Test
    public void cancelRemovesTaskFromQueue() throws Exception {
        CompletableFuture<String> promise = executor.schedule(() -> "never", 10, TimeUnit.SECONDS);
        promise.cancel(true);
        assertTrue(promise.isCancelled());
        executor.expireTasks(100_000);
        assertNull(executor.triggerNext());

        Future<?> periodic = executor.scheduleAtFixedRate(() -> {
        }, 10, 10, TimeUnit.SECONDS);
        periodic.cancel(false);
        assertTrue(periodic.isCancelled());
        executor.expireTasks(100_000);
        assertNull(executor.triggerNext());
    }

    // leg 5: destroy drains pending tasks with completeExceptionally (mutation chain 2 target)
    @Test
    public void destroyDrainsPendingTasksCompleteExceptionally() throws Exception {
        CompletableFuture<String> p1 = executor.schedule(() -> "a", 10, TimeUnit.SECONDS);
        CompletableFuture<String> p2 = executor.schedule((Callable<String>) () -> "b", 20, TimeUnit.SECONDS);
        executor.destroy();
        assertTrue(p1.isCompletedExceptionally());
        assertTrue(p2.isCompletedExceptionally());
        // get() surfaces the NopException wrapped in an ExecutionException
        ExecutionException ex = assertThrows(ExecutionException.class, () -> p1.get());
        assertTrue(ex.getCause() instanceof NopException, "cause was: " + ex.getCause());
        assertNull(executor.triggerNext());
    }

    // cleanup callback fires on normal completion too (whenComplete, not exceptionally-only)
    @Test
    public void cleanupFiresOnNormalCompletion() throws Exception {
        CompletableFuture<String> promise = executor.schedule(() -> "once", 0, TimeUnit.MILLISECONDS);
        assertEquals("once", executor.triggerNext());
        assertTrue(promise.isDone());
        // task was removed by the whenComplete cleanup callback - not delivered twice
        assertNull(executor.triggerNext());
    }
}
