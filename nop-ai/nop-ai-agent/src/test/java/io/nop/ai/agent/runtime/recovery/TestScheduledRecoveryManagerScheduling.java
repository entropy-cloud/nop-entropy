package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

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
 */
public class TestScheduledRecoveryManagerScheduling extends AbstractScheduledRecoveryManagerTest {

    // ========================================================================
    // start / stop — scheduling wiring (Wiring Verification #23)
    // ========================================================================

    @Test
    void startRegistersPeriodicTaskOnScheduler() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);

        mgr.start();

        assertNotNull(scheduler.lastCommand.get(),
                "start() must register a task via scheduleWithFixedDelay");
        assertEquals(ScheduledRecoveryManager.DEFAULT_SCAN_INTERVAL_SEC,
                scheduler.lastDelay.get().longValue(),
                "start() must use the default 60s delay");
        assertEquals(TimeUnit.SECONDS, scheduler.lastUnit.get(),
                "delay unit must be SECONDS");
        assertFalse(scheduler.cancelled.get(),
                "the registered handle must NOT be cancelled right after start");
    }

    @Test
    void startUsesConfiguredScanInterval() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler, 15L);

        mgr.start();

        assertEquals(15L, scheduler.lastDelay.get().longValue(),
                "start() must use the configured 15s delay");
    }

    @Test
    void stopCancelsRegisteredHandle() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);
        mgr.start();
        assertFalse(scheduler.cancelled.get(), "precondition: handle not yet cancelled");

        mgr.stop();

        assertTrue(scheduler.cancelled.get(),
                "stop() must cancel the registered Future handle");
        assertFalse(scheduler.mayInterruptIfRunning,
                "cancel must use mayInterruptIfRunning=false (best-effort)");
    }

    @Test
    void startAndStopAreIdempotent() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ScheduledRecoveryManager mgr = new ScheduledRecoveryManager(dataSource, scheduler);

        // Double start → exactly one scheduleWithFixedDelay call.
        mgr.start();
        mgr.start();
        assertEquals(1, scheduler.scheduleCount.get(),
                "idempotent start: repeated start must not register a second task");

        // Double stop → handle cancelled, no exception.
        mgr.stop();
        mgr.stop();
        assertTrue(scheduler.cancelled.get(),
                "idempotent stop: handle is cancelled");

        // Restart after stop → registers again.
        scheduler.scheduleCount.set(0);
        scheduler.cancelled.set(false);
        mgr.start();
        assertEquals(1, scheduler.scheduleCount.get(),
                "start after stop re-registers the task");
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
        // The wiring is verified by executing the registered Runnable and
        // checking it actually performed the cleanup (not just that a
        // command object was registered).
        Runnable registered = scheduler.lastCommand.get();
        assertNotNull(registered);
        registered.run();

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
