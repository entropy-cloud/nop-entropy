package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link FingerprintPostDenialGuard} functional behavior (design
 * §6.3 / L3-7): first consultation allows (returns null), recording makes a
 * subsequent identical consultation deny ({@code REPEATED_SAME_INTENT}),
 * changed parameters (legitimate follow-up) produce a different fingerprint
 * and are therefore allowed, reset clears the session set, per-session sets
 * are independent, and anonymous (null) sessions are not tracked.
 */
public class TestFingerprintPostDenialGuard {

    // ========================================================================
    // First consultation allows
    // ========================================================================

    @Test
    void firstConsultationReturnsNull() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), "/work"),
                "first consultation (no prior recording) must allow (return null)");
    }

    // ========================================================================
    // Recording + blind-retry detection
    // ========================================================================

    @Test
    void recordingThenIdenticalConsultationDenies() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), "/work");

        DenialResult result = guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), "/work");
        assertTrue(result != null, "after recording, an identical action must be denied (blind retry)");
        assertEquals(DenialReason.REPEATED_SAME_INTENT, result.getReason());
        assertFalse(result.isRetryable(), "REPEATED_SAME_INTENT must not be retryable");
        assertEquals(DenialSuggestedStep.REPLAN, result.getSuggestedNextStep());
        assertTrue(result.getMessage() != null && result.getMessage().contains("shell.exec"),
                "message should identify the denied tool");
    }

    @Test
    void deniedFingerprintCarriesFingerprintValue() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), "/work");
        DenialResult result = guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), "/work");
        String expectedFp = guard.fingerprintOf("shell.exec", Map.of("path", "/x"), "/work");
        assertEquals(expectedFp, result.getActionFingerprint(),
                "DenialResult.actionFingerprint must equal the guard's computed fingerprint");
    }

    // ========================================================================
    // Changed parameters (legitimate follow-up) -> different fingerprint -> allowed
    // ========================================================================

    @Test
    void differentArgumentsProduceDifferentFingerprintAndAreAllowed() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("s1", "write-file", Map.of("path", "/a/b.txt"), null);

        // Same toolName, different path -> legitimate narrower follow-up.
        DenialResult result = guard.checkBeforeDispatch("s1", "write-file",
                Map.of("path", "/a/c.txt"), null);
        assertNull(result, "different arguments must produce a different fingerprint and be allowed");
    }

    @Test
    void differentWorkDirProduceDifferentFingerprintAndAreAllowed() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("cmd", "ls"), "/work-a");

        DenialResult result = guard.checkBeforeDispatch("s1", "shell.exec",
                Map.of("cmd", "ls"), "/work-b");
        assertNull(result, "different workDir must produce a different fingerprint and be allowed");
    }

    // ========================================================================
    // Reset clears the session set
    // ========================================================================

    @Test
    void resetClearsSessionDeniedSet() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), null);
        // before reset: denied
        assertTrue(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), null) != null);

        guard.reset("s1");

        // after reset: allowed
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), null),
                "reset must clear the session denied set so the action is allowed again");
    }

    // ========================================================================
    // Per-session independence
    // ========================================================================

    @Test
    void perSessionSetsAreIndependent() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("sessionA", "shell.exec", Map.of("path", "/x"), null);

        // sessionA: denied (blind retry)
        assertTrue(guard.checkBeforeDispatch("sessionA", "shell.exec", Map.of("path", "/x"), null) != null,
                "sessionA recorded the denial, so it must be blocked");

        // sessionB: allowed (no prior recording in sessionB)
        assertNull(guard.checkBeforeDispatch("sessionB", "shell.exec", Map.of("path", "/x"), null),
                "sessionB must not be affected by sessionA's recording (per-session independence)");
    }

    @Test
    void resetOneSessionDoesNotAffectOthers() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("sessionA", "shell.exec", Map.of("path", "/x"), null);
        guard.recordDeniedAction("sessionB", "shell.exec", Map.of("path", "/x"), null);

        guard.reset("sessionA");

        // sessionA cleared
        assertNull(guard.checkBeforeDispatch("sessionA", "shell.exec", Map.of("path", "/x"), null));
        // sessionB still blocked
        assertTrue(guard.checkBeforeDispatch("sessionB", "shell.exec", Map.of("path", "/x"), null) != null,
                "resetting sessionA must not clear sessionB's denied set");
    }

    // ========================================================================
    // Anonymous (null) sessions are not tracked
    // ========================================================================

    @Test
    void nullSessionConsultationReturnsNull() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction(null, "shell.exec", Map.of("path", "/x"), null);
        assertNull(guard.checkBeforeDispatch(null, "shell.exec", Map.of("path", "/x"), null),
                "anonymous (null) session must not be tracked — consultation returns null");
    }

    @Test
    void nullSessionRecordingIsNoOp() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction(null, "shell.exec", Map.of("path", "/x"), null);
        // recording under null session must not create any per-session state
        // that would later affect a real session.
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), null));
    }

    // ========================================================================
    // isDenied diagnostic accessor
    // ========================================================================

    @Test
    void isDeniedReflectsRecordedState() {
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        String fp = guard.fingerprintOf("shell.exec", Map.of("path", "/x"), null);
        assertFalse(guard.isDenied("s1", fp));
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), null);
        assertTrue(guard.isDenied("s1", fp));
        guard.reset("s1");
        assertFalse(guard.isDenied("s1", fp));
        assertFalse(guard.isDenied(null, fp), "anonymous session must never report denied");
    }

    // ========================================================================
    // Closed loop: guard's own deny is recorded back to the guard
    // (mirrors the dispatch-path recording-after-guard-deny integration)
    // ========================================================================

    @Test
    void guardDenyRecordingFormsClosedLoop() {
        // Simulate the dispatch-path integration: record a Layer-1 deny,
        // then on the next iteration the guard consultation hits, and the
        // dispatch path records the guard-deny back to the guard. The
        // fingerprint set must remain stable (idempotent add).
        FingerprintPostDenialGuard guard = new FingerprintPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), null);

        DenialResult r1 = guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), null);
        assertTrue(r1 != null, "first guard consultation after Layer-1 deny must hit");

        // Dispatch path records the guard-deny back to the guard (closed loop).
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), null);

        // Second consultation must still hit (idempotent — set semantics).
        DenialResult r2 = guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), null);
        assertTrue(r2 != null, "closed-loop recording must keep the fingerprint in the set");
        assertEquals(r1.getActionFingerprint(), r2.getActionFingerprint());
    }
}
