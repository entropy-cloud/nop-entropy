package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 222 start/stop scheduling wiring, the scheduled task actually
 * invoking scanOnce at runtime (Anti-Hollow #22), the NoOp default semantic
 * and constructor argument validation. Split from
 * {@code TestScheduledRecoveryManager} (MA4.2-06); fixtures in
 * {@link AbstractScheduledRecoveryManagerTest}.
 *
 * <p>CRA A4 (plan 2498 Phase 4): {@code start()} binds
 * {@code executeOn(globalWorker())} on the injected carrier, so the wiring is
 * registered through the one-shot {@code schedule} trigger path
 * (BindScheduledExecutor → PeriodicTaskHelper manual-rearm chain). These
 * tests assert the trigger registration, the live round dispatch on the real
 * {@code nop-global-worker} pool, and the stopped-chain suppression — same
 * intents as the pre-A4 wiring assertions, adapted to the bind form.
 */
public class TestScheduledRecoveryManagerScheduling extends AbstractScheduledRecoveryManagerTest {

    private static void awaitUntil(BooleanSupplier condition) {
        awaitUntilAtMost(10, condition, "bounded await expired");
    }

    private static void awaitUntilAtMost(long seconds, BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
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

    // ========================================================================
    // start / stop — scheduling wiring (Wiring Verification #23)
    // ========================================================================

    @Test
    void startRegistersPeriodicTaskOnScheduler() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);

        mgr.start();

        assertNotNull(scheduler.lastTrigger.get(),
                "start() must register the periodic round trigger via the one-shot schedule path");
        assertEquals(ScheduledRecoveryManager.DEFAULT_SCAN_INTERVAL_SEC * 1000L,
                scheduler.lastTriggerDelayMillis.get().longValue(),
                "start() must use the default 60s delay (converted to millis by the bind chain)");
        assertEquals(1, scheduler.oneShotScheduleCount.get(),
                "exactly one round trigger must be registered per start");
    }

    @Test
    void startUsesConfiguredScanInterval() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler, 15L);

        mgr.start();

        assertEquals(15_000L, scheduler.lastTriggerDelayMillis.get().longValue(),
                "start() must use the configured 15s delay");
    }

    @Test
    void stopCancelsRegisteredHandle() throws Exception {
        RecordingScheduler scheduler = new RecordingScheduler();
        AtomicInteger scanCount = new AtomicInteger(0);
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler) {
            @Override
            public RecoveryScanResult scanOnce() {
                scanCount.incrementAndGet();
                return RecoveryScanResult.empty();
            }
        };
        mgr.start();
        Runnable trigger = scheduler.lastTrigger.get();
        assertNotNull(trigger, "precondition: round trigger registered");
        trigger.run();
        awaitUntilAtMost(10, () -> scanCount.get() >= 1,
                "a live chain must dispatch the scan round on the worker pool");

        mgr.stop();

        // after stop the chain is terminal: running the trigger again must
        // not dispatch any further scan round (negative await: hold for 2s
        // and verify the scan count is unchanged)
        trigger.run();
        Thread.sleep(2000);
        assertEquals(1, scanCount.get(),
                "stop() must terminate the chain: a further scan round was dispatched after stop");
    }

    @Test
    void startAndStopAreIdempotent() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);

        // Double start → exactly one periodic trigger registration.
        mgr.start();
        mgr.start();
        assertEquals(1, scheduler.oneShotScheduleCount.get(),
                "idempotent start: repeated start must not register a second trigger");

        // Double stop → no exception (idempotent).
        mgr.stop();
        mgr.stop();

        // Restart after stop → registers again.
        mgr.start();
        assertEquals(2, scheduler.oneShotScheduleCount.get(),
                "start after stop re-registers the trigger");
        mgr.stop();
    }

    @Test
    void stopBeforeStartIsNoOp() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);
        // stop() without a prior start() must not throw and must not cancel anything.
        mgr.stop();
        assertFalse(scheduler.cancelled.get(),
                "stop before start is a no-op (no handle to cancel)");
    }

    // ========================================================================
    // scheduled task actually invokes scanOnce at runtime (Anti-Hollow #22)
    // ========================================================================

    @Test
    void scheduledTaskRunsScanOnce() throws Exception {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);
        insertLockRow("stale-x", "owner", System.currentTimeMillis() - 1000L);

        mgr.start();
        // The wiring is verified by firing the registered round trigger and
        // checking it actually performed the cleanup (not just that a
        // trigger object was registered). The trigger hops the scan body to
        // the real globalWorker pool, so the DB effect is awaited bounded.
        Runnable registered = scheduler.lastTrigger.get();
        assertNotNull(registered);
        registered.run();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && countAllLockRows() != 0) {
            Thread.sleep(50);
        }
        assertEquals(0, countAllLockRows(),
                "the registered periodic task must actually run scanOnce and clean stale locks");
        mgr.stop();
    }

    // ========================================================================
    // NoOp default semantic (Minimum Rules #24 — explicit, not silent)
    // ========================================================================

    @Test
    void noOpDefaultReturnsAllZeroResult() {
        NoOpRecoveryManager noop = NoOpRecoveryManager.noOp();
        // start/stop are no-ops (must not throw).
        noop.start();
        noop.stop();
        RecoveryScanResult result = noop.scanOnce();
        assertNotNull(result, "NoOp scanOnce must return a non-null result");
        assertEquals(0, result.getStaleLocksCleaned());
        assertEquals(0, result.getOrphanSessionsDetected());
        assertTrue(result.getOrphanSessionIds().isEmpty());
        assertEquals(0L, result.getScanDurationMs());
        assertEquals(0L, result.getScannedAt());
    }

    @Test
    void noOpIsSingleton() {
        assertSame(NoOpRecoveryManager.noOp(), NoOpRecoveryManager.noOp(),
                "NoOpRecoveryManager.noOp() is a singleton");
    }

    // ========================================================================
    // Constructor argument validation (fail-fast, no silent no-op)
    // ========================================================================

    @Test
    void constructorValidatesArguments() {
        RecordingScheduler scheduler = new RecordingScheduler();
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(null, scheduler),
                "null dataSource must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(dataSource, null),
                "null scheduledExecutor must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(dataSource, scheduler, 0L),
                "scanIntervalSec=0 must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> new ScheduledRecoveryManager(dataSource, scheduler, -1L),
                "negative scanIntervalSec must fail-fast");
    }
}
