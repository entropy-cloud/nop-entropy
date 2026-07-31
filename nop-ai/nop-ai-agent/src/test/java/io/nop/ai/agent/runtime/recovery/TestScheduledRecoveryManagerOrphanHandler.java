package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 226 orphan-recovery-handler integration: default handler wiring,
 * setter-injection wiring verification (#23) and the E2E ABORT daemon path
 * against a real H2 DB. Split from {@code TestScheduledRecoveryManager}
 * (MA4.2-06); fixtures in {@link AbstractScheduledRecoveryManagerTest}.
 */
public class TestScheduledRecoveryManagerOrphanHandler extends AbstractScheduledRecoveryManagerTest {

    // ========================================================================
    // Orphan recovery handler integration (plan 226 / L4-8-P4-RecoveryStrategy)
    // ========================================================================

    @Test
    void defaultOrphanHandlerIsNoOp() {
        ScheduledRecoveryManager mgr = newManager();
        // The shipped default handler must be NoOpOrphanRecoveryHandler
        // (SKIP mode, zero regression with plan 222).
        assertTrue(mgr.getOrphanRecoveryHandler() instanceof NoOpOrphanRecoveryHandler,
                "shipped default orphan handler must be NoOpOrphanRecoveryHandler");
    }

    @Test
    void setOrphanRecoveryHandlerRejectsNull() {
        ScheduledRecoveryManager mgr = newManager();
        assertThrows(NopAiAgentException.class,
                () -> mgr.setOrphanRecoveryHandler(null),
                "setOrphanRecoveryHandler(null) must fail-fast (no silent fallback to default)");
    }

    @Test
    void setOrphanRecoveryHandlerInjectsNonNoOpHandler() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        RecordingHandler handler = new RecordingHandler();
        mgr.setOrphanRecoveryHandler(handler);

        insertSession("orphan-inject", "running");
        RecoveryScanResult result = mgr.scanOnce();

        // Wiring Verification (#23): the injected handler must be called
        // at runtime by scanOnce (not just assigned as a field).
        assertEquals(1, handler.callCount.get(),
                "scanOnce must invoke the injected handler for the single orphan");
        assertEquals("orphan-inject", handler.lastSessionId.get(),
                "handler must receive the orphan session ID");
        assertFalse(mgr.getOrphanRecoveryHandler() instanceof NoOpOrphanRecoveryHandler,
                "after injection, the handler must be the non-NoOp instance");
        assertEquals(1, result.getRecoveryActions().size(),
                "recoveryActions must contain one outcome per orphan");
    }

    @Test
    void noOpDefaultScanRecordsSkipOutcomes() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        insertSession("orphan-noop-1", "running");
        insertSession("orphan-noop-2", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        // Shipped default (NoOp/SKIP): each orphan gets a SKIP outcome.
        assertEquals(2, result.getOrphanSessionsDetected());
        assertEquals(2, result.getRecoveryActions().size(),
                "recoveryActions must have one outcome per detected orphan");
        for (RecoveryOutcome outcome : result.getRecoveryActions()) {
            assertEquals(RecoveryMode.SKIP, outcome.getMode(),
                    "NoOp default must produce SKIP outcomes");
            assertTrue(outcome.isSucceeded(),
                    "SKIP is an observation-only success");
        }
    }

    @Test
    void noOpManagerEmptyResultHasEmptyRecoveryActions() {
        RecoveryScanResult empty = RecoveryScanResult.empty();
        assertNotNull(empty.getRecoveryActions(),
                "empty() recoveryActions must be non-null");
        assertTrue(empty.getRecoveryActions().isEmpty(),
                "empty() recoveryActions must be an empty list");
    }

    // ========================================================================
    // E2E: daemon + ABORT handler → orphan session aborted (plan 226)
    // ========================================================================

    @Test
    void endToEndAbortHandlerAbortsOrphanSession() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        // Inject a functional ABORT handler (no engine needed for ABORT).
        DefaultOrphanRecoveryHandler abortHandler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, dataSource);
        mgr.setOrphanRecoveryHandler(abortHandler);

        insertSession("orphan-e2e", "running");
        // A healthy locked session — must be untouched by ABORT.
        insertSession("healthy-e2e", "running");
        insertLockRow("healthy-e2e", "alive-owner", System.currentTimeMillis() + 60_000L);
        // A terminal session — excluded from orphan detection entirely.
        insertSession("done-e2e", "completed");

        RecoveryScanResult result = mgr.scanOnce();

        // End-to-end: exactly one orphan (running, no lock) was detected and aborted.
        assertEquals(1, result.getOrphanSessionsDetected(),
                "only the lock-free running session is an orphan");
        assertEquals("orphan-e2e", result.getOrphanSessionIds().get(0));
        assertEquals(1, result.getRecoveryActions().size());
        RecoveryOutcome outcome = result.getRecoveryActions().get(0);
        assertEquals(RecoveryMode.ABORT, outcome.getMode());
        assertTrue(outcome.isSucceeded(),
                "ABORT of the orphan must succeed");

        // The orphan session's DB status must now be 'failed'.
        assertEquals("failed", getSessionStatus("orphan-e2e"),
                "E2E: orphan session status must be 'failed' after ABORT scan");
        // The healthy session must be untouched (still running, lock preserved).
        assertEquals("running", getSessionStatus("healthy-e2e"),
                "E2E: healthy locked session must be untouched");
        // The terminal session must be untouched.
        assertEquals("completed", getSessionStatus("done-e2e"),
                "E2E: terminal session must be untouched");
    }

    @Test
    void daemonIntegrationAbortHandlerPopulatesRecoveryActions() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        DefaultOrphanRecoveryHandler abortHandler =
                new DefaultOrphanRecoveryHandler(RecoveryMode.ABORT, null, dataSource);
        mgr.setOrphanRecoveryHandler(abortHandler);

        insertSession("orphan-diag", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        // Daemon integration: scanOnce detected the orphan and the handler
        // recorded a succeeded=true ABORT outcome in recoveryActions.
        assertEquals(1, result.getRecoveryActions().size());
        assertTrue(result.getRecoveryActions().get(0).isSucceeded());
        assertEquals(RecoveryMode.ABORT, result.getRecoveryActions().get(0).getMode());
        assertEquals("failed", getSessionStatus("orphan-diag"),
                "daemon integration: pending orphan status must be 'failed' after scan");
    }
}
