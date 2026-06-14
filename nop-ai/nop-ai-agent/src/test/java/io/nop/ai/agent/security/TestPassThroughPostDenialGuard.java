package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the {@link PassThroughPostDenialGuard} contract (design §6.3
 * default): consultation always returns {@code null} (no blind-retry
 * detection), recording and reset are no-ops that do not throw, and the
 * singleton factory returns a shared instance — following the same pattern as
 * {@link TestNoOpDenialLedger} / {@link TestAutoApproveGate}.
 */
public class TestPassThroughPostDenialGuard {

    @Test
    void checkBeforeDispatchAlwaysReturnsNull() {
        IPostDenialGuard guard = PassThroughPostDenialGuard.passThrough();
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), "/work"),
                "PassThroughPostDenialGuard.checkBeforeDispatch must always return null (no blocking)");
        assertNull(guard.checkBeforeDispatch(null, null, null, null),
                "PassThroughPostDenialGuard.checkBeforeDispatch must return null even for null inputs");
    }

    @Test
    void checkBeforeDispatchReturnsNullEvenAfterRepeatedRecording() {
        IPostDenialGuard guard = PassThroughPostDenialGuard.passThrough();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), "/work");
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), "/work");
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), "/work"),
                "recording must have no effect on the pass-through default (no per-session state)");
    }

    @Test
    void recordDeniedActionDoesNotThrow() {
        IPostDenialGuard guard = PassThroughPostDenialGuard.passThrough();
        guard.recordDeniedAction("s1", "shell.exec", Map.of("path", "/x"), "/work");
        guard.recordDeniedAction(null, null, null, null);
        // no exception expected — recording is a semantically-correct no-op
    }

    @Test
    void resetDoesNotThrow() {
        IPostDenialGuard guard = PassThroughPostDenialGuard.passThrough();
        guard.reset("s1");
        guard.reset(null);
        // still consistent after reset
        assertNull(guard.checkBeforeDispatch("s1", "shell.exec", Map.of("path", "/x"), "/work"));
    }

    @Test
    void singletonInstanceShared() {
        IPostDenialGuard a = PassThroughPostDenialGuard.passThrough();
        IPostDenialGuard b = PassThroughPostDenialGuard.passThrough();
        assertSame(a, b, "PassThroughPostDenialGuard.passThrough() must return a shared singleton");
    }
}
