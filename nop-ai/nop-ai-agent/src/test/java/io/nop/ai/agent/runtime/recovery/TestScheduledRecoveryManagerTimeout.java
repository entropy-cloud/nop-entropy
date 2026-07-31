package io.nop.ai.agent.runtime.recovery;

import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 229 session-timeout-handler integration: default wiring, setter
 * injection, NoOp defaults, timeout-vs-orphan ordering (design 裁定 3),
 * and the E2E daemon paths (FORCE_FAILED / LOCAL_CANCELLED) against a real
 * H2 DB. Split from {@code TestScheduledRecoveryManager} (MA4.2-06);
 * fixtures in {@link AbstractScheduledRecoveryManagerTest}.
 */
public class TestScheduledRecoveryManagerTimeout extends AbstractScheduledRecoveryManagerTest {

    // ========================================================================
    // Session timeout handler integration (plan 229 / L4-8-P4-TimeoutAbort)
    // ========================================================================

    @Test
    void defaultTimeoutHandlerIsNoOp() {
        ScheduledRecoveryManager mgr = newManager();
        // The shipped default timeout handler must be NoOpSessionTimeoutHandler
        // (SKIPPED action, zero regression with plan 226).
        assertTrue(mgr.getSessionTimeoutHandler() instanceof NoOpSessionTimeoutHandler,
                "shipped default timeout handler must be NoOpSessionTimeoutHandler");
    }

    @Test
    void defaultTimeoutSecondsIs30Minutes() {
        ScheduledRecoveryManager mgr = newManager();
        assertEquals(30L * 60L, mgr.getTimeoutSeconds(),
                "default timeoutSeconds must be 30 minutes (1800s, matching default lockLeaseMs)");
    }

    @Test
    void setTimeoutSecondsRejectsNonPositive() {
        ScheduledRecoveryManager mgr = newManager();
        assertThrows(NopAiAgentException.class,
                () -> mgr.setTimeoutSeconds(0L),
                "setTimeoutSeconds(0) must fail-fast");
        assertThrows(NopAiAgentException.class,
                () -> mgr.setTimeoutSeconds(-1L),
                "setTimeoutSeconds(-1) must fail-fast");
        // Positive value must succeed.
        mgr.setTimeoutSeconds(120L);
        assertEquals(120L, mgr.getTimeoutSeconds());
    }

    @Test
    void setSessionTimeoutHandlerRejectsNull() {
        ScheduledRecoveryManager mgr = newManager();
        assertThrows(NopAiAgentException.class,
                () -> mgr.setSessionTimeoutHandler(null),
                "setSessionTimeoutHandler(null) must fail-fast (no silent fallback to default)");
    }

    @Test
    void setSessionTimeoutHandlerInjectsNonNoOpHandler() throws Exception {
        ScheduledRecoveryManager mgr = newManager();
        RecordingTimeoutHandler handler = new RecordingTimeoutHandler();
        mgr.setSessionTimeoutHandler(handler);

        insertSession("timeout-inject", "running");
        RecoveryScanResult result = mgr.scanOnce();

        // Wiring Verification (#23): the injected handler must be called
        // at runtime by scanOnce (not just assigned as a field).
        assertEquals(1, handler.callCount.get(),
                "scanOnce must invoke the injected timeout handler for the single timed-out session");
        assertEquals("timeout-inject", handler.lastSessionId.get(),
                "handler must receive the timed-out session ID");
        assertFalse(mgr.getSessionTimeoutHandler() instanceof NoOpSessionTimeoutHandler,
                "after injection, the handler must be the non-NoOp instance");
        assertEquals(1, result.getTimeoutActions().size(),
                "timeoutActions must contain one outcome per timed-out session");
    }

    @Test
    void noOpTimeoutHandlerDefaultProducesSkippedOutcomes() throws Exception {
        // Shipped default (NoOp SKIPPED): timed-out sessions produce SKIPPED
        // outcomes, no DB mutation, zero behaviour regression with plan 226.
        ScheduledRecoveryManager mgr = newManager();
        // UPDATED_AT=0 → always timed-out (0 < now - threshold).
        insertSession("timeout-noop-1", "running");
        insertSession("timeout-noop-2", "pending");

        RecoveryScanResult result = mgr.scanOnce();

        assertEquals(2, result.getTimeoutActions().size(),
                "NoOp default must produce one SKIPPED outcome per timed-out session");
        for (TimeoutOutcome outcome : result.getTimeoutActions()) {
            assertEquals(TimeoutAction.SKIPPED, outcome.getAction(),
                    "NoOp default must produce SKIPPED outcomes");
            assertTrue(outcome.isSucceeded(),
                    "SKIPPED is an observation-only success");
        }
        // NoOp must NOT mutate DB status (zero regression).
        assertEquals("running", getSessionStatus("timeout-noop-1"),
                "NoOp timeout handler must NOT mutate DB status");
        assertEquals("pending", getSessionStatus("timeout-noop-2"),
                "NoOp timeout handler must NOT mutate DB status");
    }

    @Test
    void noOpManagerEmptyResultHasEmptyTimeoutActions() {
        RecoveryScanResult empty = RecoveryScanResult.empty();
        assertNotNull(empty.getTimeoutActions(),
                "empty() timeoutActions must be non-null");
        assertTrue(empty.getTimeoutActions().isEmpty(),
                "empty() timeoutActions must be an empty list");
    }

    @Test
    void nonTimedOutSessionIsNotTimeoutDetected() throws Exception {
        // A session with a RECENT UPDATED_AT (within the threshold) must
        // NOT be flagged as timed-out.
        ScheduledRecoveryManager mgr = newManager();
        mgr.setTimeoutSeconds(60L); // 1-minute threshold
        long now = System.currentTimeMillis();
        // UPDATED_AT = now → within the 60s threshold → NOT timed-out.
        insertSessionWithUpdatedAt("fresh-session", "running", now);

        RecoveryScanResult result = mgr.scanOnce();

        assertTrue(result.getTimeoutActions().isEmpty(),
                "a session with a recent UPDATED_AT must NOT be flagged as timed-out");
    }

    @Test
    void daemonTimeoutDetectionForceFailsOrphanedTimedOutSession() throws Exception {
        // Daemon integration: scanOnce detects a timed-out orphan session
        // and the DefaultSessionTimeoutHandler FORCE_FAILED branch marks
        // it 'failed' (terminal).
        ScheduledRecoveryManager mgr = newManager();
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, new StubEngine(), dataSource, "this-instance");
        mgr.setSessionTimeoutHandler(handler);

        // UPDATED_AT=0 → timed-out; no lock → orphaned → FORCE_FAILED.
        insertSession("timeout-orphan", "running");

        RecoveryScanResult result = mgr.scanOnce();

        // Daemon integration: scanOnce detected the timed-out session and
        // the handler recorded a succeeded=true FORCE_FAILED outcome.
        assertEquals(1, result.getTimeoutActions().size(),
                "one timed-out session → one timeout outcome");
        TimeoutOutcome outcome = result.getTimeoutActions().get(0);
        assertEquals(TimeoutAction.FORCE_FAILED, outcome.getAction());
        assertTrue(outcome.isSucceeded(),
                "FORCE_FAILED of a running orphan must succeed");
        assertEquals("failed", getSessionStatus("timeout-orphan"),
                "daemon integration: timed-out orphan status must be 'failed' after scan");
    }

    @Test
    void timeoutBeforeOrphanOrderingAvoidsConflict() throws Exception {
        // Design 裁定 3: timeout detection runs BEFORE orphan detection.
        // A timed-out orphan session force-marked 'failed' by the timeout
        // handler must NOT be subsequently detected as an orphan (it is
        // now terminal), avoiding double-handling.
        ScheduledRecoveryManager mgr = newManager();
        DefaultSessionTimeoutHandler timeoutHandler = new DefaultSessionTimeoutHandler(
                60L, new StubEngine(), dataSource, "this-instance");
        mgr.setSessionTimeoutHandler(timeoutHandler);
        // Inject a RESUME orphan handler to prove the timeout path took
        // precedence (if orphan detection ran on this session, the RESUME
        // handler would be called — but it must NOT be, because the
        // timeout handler already terminalised the session).
        RecordingHandler orphanHandler = new RecordingHandler();
        mgr.setOrphanRecoveryHandler(orphanHandler);

        // UPDATED_AT=0 → timed-out; no lock → would be orphan too.
        insertSession("conflict-1", "running");

        RecoveryScanResult result = mgr.scanOnce();

        // The timeout handler force-failed the session FIRST.
        assertEquals(1, result.getTimeoutActions().size());
        assertTrue(result.getTimeoutActions().get(0).isSucceeded());
        assertEquals(TimeoutAction.FORCE_FAILED, result.getTimeoutActions().get(0).getAction());
        assertEquals("failed", getSessionStatus("conflict-1"),
                "timeout handler must have force-failed the session");

        // Orphan detection subsequently excludes the session (now terminal).
        assertEquals(0, result.getOrphanSessionsDetected(),
                "after timeout force-fail, the session is terminal and must NOT be an orphan");
        assertTrue(result.getOrphanSessionIds().isEmpty());
        assertEquals(0, orphanHandler.callCount.get(),
                "the orphan handler must NOT be called (session was terminalised by timeout first)");
    }

    @Test
    void endToEndTimeoutForceFailedCompleteScanResult() throws Exception {
        // End-to-end (Minimum Rules #22): full scanOnce path with a
        // DefaultSessionTimeoutHandler. A timed-out orphan session is
        // force-failed; a healthy session with a fresh UPDATED_AT is NOT
        // timed-out; a terminal session is excluded.
        ScheduledRecoveryManager mgr = newManager();
        mgr.setTimeoutSeconds(60L);
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, new StubEngine(), dataSource, "this-instance");
        mgr.setSessionTimeoutHandler(handler);

        long now = System.currentTimeMillis();
        // Timed-out orphan: UPDATED_AT=0, no lock, running → FORCE_FAILED.
        insertSession("e2e-timeout", "running");
        // Healthy session: recent UPDATED_AT, active lock → NOT timed-out,
        // NOT orphan.
        insertSessionWithUpdatedAt("e2e-healthy", "running", now);
        insertLockRow("e2e-healthy", "alive-owner", now + 60_000L);
        // Terminal session: excluded from timeout and orphan detection.
        insertSession("e2e-done", "completed");

        RecoveryScanResult result = mgr.scanOnce();

        // Timeout path: only the timed-out orphan is detected and force-failed.
        assertEquals(1, result.getTimeoutActions().size(),
                "only the timed-out session (UPDATED_AT=0) is in timeoutActions");
        TimeoutOutcome timeoutOutcome = result.getTimeoutActions().get(0);
        assertEquals("e2e-timeout", timeoutOutcome.getSessionId());
        assertEquals(TimeoutAction.FORCE_FAILED, timeoutOutcome.getAction());
        assertTrue(timeoutOutcome.isSucceeded());

        // The timed-out session is now terminal → excluded from orphan detection.
        assertEquals(0, result.getOrphanSessionsDetected(),
                "the force-failed session is terminal → not detected as orphan");
        assertEquals("failed", getSessionStatus("e2e-timeout"),
                "E2E: timed-out session status must be 'failed'");
        // The healthy session is untouched.
        assertEquals("running", getSessionStatus("e2e-healthy"),
                "E2E: healthy session with fresh UPDATED_AT must be untouched");
        // The terminal session is untouched.
        assertEquals("completed", getSessionStatus("e2e-done"),
                "E2E: terminal session must be untouched");
        // Observable duration + timestamp.
        assertTrue(result.getScanDurationMs() >= 0);
        assertTrue(result.getScannedAt() > 0);
    }

    @Test
    void daemonTimeoutLocalCancelledWiringWithMockEngine() throws Exception {
        // Daemon integration for LOCAL_CANCELLED: a timed-out session with
        // an active local lock → the DefaultSessionTimeoutHandler must
        // delegate to engine.cancelSession(forced=true). The DB status is
        // NOT mutated by the handler (the cancelSession path owns the
        // terminal transition).
        ScheduledRecoveryManager mgr = newManager();
        StubEngine engine = new StubEngine();
        DefaultSessionTimeoutHandler handler = new DefaultSessionTimeoutHandler(
                60L, engine, dataSource, StubEngine.INSTANCE_ID);
        mgr.setSessionTimeoutHandler(handler);

        // Timed-out session (UPDATED_AT=0) with an active lock owned by
        // this instance → LOCAL_CANCELLED branch.
        insertSession("timeout-local", "running");
        insertLockRow("timeout-local", StubEngine.INSTANCE_ID,
                System.currentTimeMillis() + 60_000L);

        RecoveryScanResult result = mgr.scanOnce();

        // Wiring Verification (#23): engine.cancelSession(forced=true) was
        // actually invoked at runtime by the daemon scanOnce path.
        assertEquals(1, engine.cancelCount.get(),
                "LOCAL_CANCELLED via daemon must call engine.cancelSession");
        assertEquals("timeout-local", engine.lastCancelSessionId.get());
        assertTrue(engine.lastCancelForced.get(),
                "cancelSession must be forced=true");
        // The outcome records LOCAL_CANCELLED.
        assertEquals(1, result.getTimeoutActions().size());
        TimeoutOutcome outcome = result.getTimeoutActions().get(0);
        assertEquals(TimeoutAction.LOCAL_CANCELLED, outcome.getAction());
        assertTrue(outcome.isSucceeded());
        // The session is still running/pending for orphan detection
        // purposes (the handler did not mutate status). However, the
        // session has an ACTIVE lock → it is NOT an orphan either.
        assertEquals(0, result.getOrphanSessionsDetected(),
                "the local-locked session is not an orphan (active lock protects it)");
    }
}
