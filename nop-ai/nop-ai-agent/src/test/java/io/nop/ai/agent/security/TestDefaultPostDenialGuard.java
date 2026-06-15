package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 200 focused tests for {@link DefaultPostDenialGuard}:
 * verifies fingerprint-based blind-retry blocking.
 */
public class TestDefaultPostDenialGuard {

    @Test
    void firstCallNotBlocked() {
        DefaultPostDenialGuard guard = new DefaultPostDenialGuard();
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("cmd", "ls"), "/tmp"),
                "first call must not be blocked");
    }

    @Test
    void blindRetryBlockedAfterRecording() {
        DefaultPostDenialGuard guard = new DefaultPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("cmd", "ls"), "/tmp");

        DenialResult result = guard.checkBeforeDispatch("s1", "shell.exec", Map.of("cmd", "ls"), "/tmp");
        assertNotNull(result, "identical call after denial must be blocked");
        assertTrue(result.getReason() == DenialReason.REPEATED_SAME_INTENT,
                "blocked retry must carry REPEATED_SAME_INTENT reason");
    }

    @Test
    void differentParametersNotBlocked() {
        DefaultPostDenialGuard guard = new DefaultPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("cmd", "ls"), "/tmp");

        // Different arguments → different fingerprint → not a blind retry
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("cmd", "pwd"), "/tmp"),
                "call with different arguments must not be blocked (legitimate follow-up)");
    }

    @Test
    void resetClearsDeniedSet() {
        DefaultPostDenialGuard guard = new DefaultPostDenialGuard();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("cmd", "ls"), "/tmp");
        assertNotNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("cmd", "ls"), "/tmp"));

        guard.reset("s1");
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("cmd", "ls"), "/tmp"),
                "after reset, same call must not be blocked");
    }

    @Test
    void anonymousSessionNotTracked() {
        DefaultPostDenialGuard guard = new DefaultPostDenialGuard();
        guard.recordDeniedAction(null, "shell.exec", Map.of("cmd", "ls"), "/tmp");
        assertNull(guard.checkBeforeDispatch(null, "shell.exec", Map.of("cmd", "ls"), "/tmp"),
                "anonymous session must not be tracked");
    }

    @Test
    void perSessionDeniedSetsAreIndependent() {
        DefaultPostDenialGuard guard = new DefaultPostDenialGuard();
        guard.recordDeniedAction("sA", "shell.exec", Map.of("cmd", "ls"), "/tmp");
        assertNotNull(guard.checkBeforeDispatch("sA", "shell.exec", Map.of("cmd", "ls"), "/tmp"));
        assertNull(guard.checkBeforeDispatch("sB", "shell.exec", Map.of("cmd", "ls"), "/tmp"),
                "session B must not be affected by session A's denial");
    }
}
