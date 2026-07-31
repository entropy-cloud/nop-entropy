package io.nop.ai.agent.runtime.recovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 222 scanOnce semantics against a real H2 DB: stale-lock cleanup,
 * orphan session detection and full-scan result fields. Split from
 * {@code TestScheduledRecoveryManager} (MA4.2-06); fixtures in
 * {@link AbstractScheduledRecoveryManagerTest}.
 */
public class TestScheduledRecoveryManagerScanOnce extends AbstractScheduledRecoveryManagerTest {

    // ========================================================================
    // scanOnce — stale lock cleanup (idempotent DELETE)
    // ========================================================================

    @Test
    void staleLocksAreCleanedUp() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        // Two stale (expired) lock rows + one active lock row.
        insertLockRow("stale-1", "owner-A", now - 1000L);
        insertLockRow("stale-2", "owner-B", now - 1L);
        insertLockRow("active-1", "owner-C", now + 60_000L);
        assertEquals(3, countAllLockRows(), "precondition: 3 lock rows inserted");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(2, result.getStaleLocksCleaned(),
                "staleLocksCleaned must count the 2 expired rows deleted");
        assertEquals(1, countAllLockRows(),
                "only the active lock row must remain after cleanup");
    }

    @Test
    void activeLocksArePreserved() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        insertLockRow("active-future", "owner-A", now + 60_000L);

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(0, result.getStaleLocksCleaned(),
                "no expired locks → nothing cleaned");
        assertEquals(1, countAllLockRows(),
                "active (non-expired) lock row must NOT be deleted");
    }

    // ========================================================================
    // scanOnce — orphan session detection
    // ========================================================================

    @Test
    void orphanSessionsAreDetected() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        // running session with NO active lock → orphan.
        insertSession("orphan-running", "running");
        // pending session with NO active lock → orphan.
        insertSession("orphan-pending", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(2, result.getOrphanSessionsDetected(),
                "both running and pending sessions without a lock are orphans");
        assertTrue(result.getOrphanSessionIds().contains("orphan-running"),
                "orphanSessionIds must contain the running orphan");
        assertTrue(result.getOrphanSessionIds().contains("orphan-pending"),
                "orphanSessionIds must contain the pending orphan");
    }

    @Test
    void terminalSessionsAreExcluded() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        insertSession("completed-session", "completed");
        insertSession("failed-session", "failed");
        insertSession("cancelled-session", "cancelled");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(0, result.getOrphanSessionsDetected(),
                "terminal sessions are never orphans");
        assertTrue(result.getOrphanSessionIds().isEmpty(),
                "no orphan ids for terminal-only sessions");
    }

    @Test
    void sessionWithActiveLockIsNotOrphan() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        insertSession("running-locked", "running");
        // An active (non-expired) lock protects the session → not an orphan.
        insertLockRow("running-locked", "owner-A", now + 60_000L);

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(0, result.getOrphanSessionsDetected(),
                "a running session with an active lock is not an orphan");
    }

    @Test
    void sessionWithExpiredLockIsOrphan() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        insertSession("running-expired-lock", "running");
        // Only an expired lock exists → after cleanup it has no active lock.
        insertLockRow("running-expired-lock", "owner-A", now - 1000L);

        RecoveryScanResult result = mgr.scanOnce();

        // The expired lock is cleaned up first, then orphan detection runs
        // against the (now lock-free) session.
        assertEquals(1, result.getStaleLocksCleaned(),
                "expired lock is cleaned up");
        assertEquals(1, result.getOrphanSessionsDetected(),
                "after cleanup, the session has no active lock → orphan");
        assertEquals("running-expired-lock", result.getOrphanSessionIds().get(0));
    }

    // ========================================================================
    // scanOnce — result fields / E2E
    // ========================================================================

    @Test
    void scanResultFieldsPopulated() {
        ScheduledRecoveryManager mgr = newManager();
        long before = System.currentTimeMillis();
        RecoveryScanResult result = mgr.scanOnce();
        long after = System.currentTimeMillis();

        assertNotNull(result, "scanOnce must return a non-null result");
        assertTrue(result.getScannedAt() >= before && result.getScannedAt() <= after,
                "scannedAt must be a current epoch ms timestamp");
        assertTrue(result.getScanDurationMs() >= 0,
                "scanDurationMs must be non-negative");
        // Empty DB → all-zero counts but a real timestamp.
        assertEquals(0, result.getStaleLocksCleaned());
        assertEquals(0, result.getOrphanSessionsDetected());
        assertTrue(result.getOrphanSessionIds().isEmpty());
    }

    @Test
    void endToEndStaleLockPlusOrphan() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        long now = System.currentTimeMillis();
        // Stale lock (orphaned by a crashed holder).
        insertLockRow("crashed-holder", "dead-owner", now - 5_000L);
        // Orphan session: running, no lock.
        insertSession("orphan-1", "running");
        // Active, healthy session+lock — must be untouched.
        insertSession("healthy", "running");
        insertLockRow("healthy", "alive-owner", now + 60_000L);
        // Terminal session — excluded.
        insertSession("done", "completed");

        RecoveryScanResult result = mgr.scanOnce();

        // End-to-end: stale lock deleted, orphan detected, active lock preserved,
        // terminal session excluded.
        assertEquals(1, result.getStaleLocksCleaned(),
                "the single stale lock row is cleaned");
        assertEquals(1, result.getOrphanSessionsDetected(),
                "only the lock-free running session is an orphan (healthy is locked, done is terminal)");
        assertEquals("orphan-1", result.getOrphanSessionIds().get(0));
        assertEquals(1, countAllLockRows(),
                "only the healthy active lock row remains");
        // Observable duration + timestamp.
        assertTrue(result.getScanDurationMs() >= 0);
        assertTrue(result.getScannedAt() > 0);
    }
}
