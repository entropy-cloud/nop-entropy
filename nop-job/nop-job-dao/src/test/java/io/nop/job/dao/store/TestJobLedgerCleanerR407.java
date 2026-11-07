/* CRA 8.47 R4-07 (plan 2540) W-04 / 1.4-C7-004 — driver 红④: R01 三表保留族.
 * fail-fast legs + wiring assertion (clearing body real on lifecycle chain). */
package io.nop.job.dao.store;

import io.nop.job.dao.cleanup.JobLedgerCleaner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestJobLedgerCleanerR407 {
    // fail-fast legs (adjudication H.1 decision 1 — SSOT §3.1 constraints)
    @Test
    public void negativeRetentionDaysFailsFast() {
        JobLedgerCleaner cleaner = new JobLedgerCleaner(null);
        try { cleaner.setRetentionDays(-1); fail("must fail-fast"); } catch (IllegalArgumentException e) { /* expected */ }
    }

    @Test
    public void cleanupIntervalBelowMinimumFailsFast() {
        JobLedgerCleaner cleaner = new JobLedgerCleaner(null);
        try { cleaner.setCleanupIntervalMs(30000); fail("must fail-fast"); } catch (IllegalArgumentException e) { /* expected */ }
    }

    @Test
    public void deleteBatchSizeZeroFailsFast() {
        JobLedgerCleaner cleaner = new JobLedgerCleaner(null);
        try { cleaner.setDeleteBatchSize(0); fail("must fail-fast"); } catch (IllegalArgumentException e) { /* expected */ }
    }

    // Wiring leg: cleanupAll() is callable and completes without error when retention=0
    @Test
    public void zeroRetentionIsDisabled() {
        JobLedgerCleaner cleaner = new JobLedgerCleaner(null);
        cleaner.setRetentionDays(0);
        assertDoesNotThrow(() -> cleaner.cleanupAll()); // disabled = no-op, no error
    }

    // Terminal predicate: SUSPICIOUS(15) excluded from terminal set (adjudication H.1 decision 2)
    @Test
    public void suspiciousExcludedFromTerminalTaskSet() {
        assertTrue(JobLedgerCleaner.isTerminalTask(30)); // SUCCESS
        assertTrue(JobLedgerCleaner.isTerminalTask(40)); // FAILED
        assertTrue(JobLedgerCleaner.isTerminalTask(50)); // TIMEOUT
        assertTrue(JobLedgerCleaner.isTerminalTask(60)); // CANCELED
        assertFalse(JobLedgerCleaner.isTerminalTask(15), "SUSPICIOUS must be excluded (resolvable-pending)");
        assertFalse(JobLedgerCleaner.isTerminalTask(20), "RUNNING must be excluded");
        assertFalse(JobLedgerCleaner.isTerminalTask(10), "CLAIMED must be excluded");
        assertFalse(JobLedgerCleaner.isTerminalTask(0), "WAITING must be excluded");
    }

    private static void assertDoesNotThrow(Runnable r) {
        try { r.run(); } catch (Exception e) { fail("unexpected exception: " + e); }
    }
    private static void fail(String msg) { throw new AssertionError(msg); }
}
