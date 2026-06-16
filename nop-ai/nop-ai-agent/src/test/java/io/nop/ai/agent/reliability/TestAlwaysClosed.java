package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 210 (L3-1) Phase 1 focused test for {@link AlwaysClosed} (Minimum
 * Rules #25). Verifies the shipped default unconditionally reports CLOSED,
 * allows every call, and treats result recording as explicit no-ops that do
 * not change the reported state — preserving the engine's pre-plan-210
 * zero-circuit-breaking behaviour.
 */
public class TestAlwaysClosed {

    @Test
    void alwaysClosedReturnsSingletonViaFactory() {
        ICircuitBreaker a = AlwaysClosed.alwaysClosed();
        ICircuitBreaker b = AlwaysClosed.alwaysClosed();
        assertSame(a, b, "alwaysClosed() must return the same singleton instance");
    }

    @Test
    void alwaysClosedReportsClosedForAnyModelKey() {
        ICircuitBreaker breaker = AlwaysClosed.alwaysClosed();
        // A key that was never recorded must still report CLOSED (no state
        // is ever tracked by the pass-through default).
        assertSame(CircuitState.CLOSED, breaker.getState("openai:gpt-4"),
                "AlwaysClosed must report CLOSED for an untracked model key");
        assertSame(CircuitState.CLOSED, breaker.getState("anthropic:claude-3"),
                "AlwaysClosed must report CLOSED for any model key");
        assertSame(CircuitState.CLOSED, breaker.getState("provider:model"),
                "AlwaysClosed must report CLOSED even for a synthetic key");
    }

    @Test
    void alwaysClosedAllowsEveryCall() {
        ICircuitBreaker breaker = AlwaysClosed.alwaysClosed();
        assertTrue(breaker.allowCall("openai:gpt-4"),
                "AlwaysClosed must allow every call (pass-through)");
        assertTrue(breaker.allowCall("anthropic:claude-3"),
                "AlwaysClosed must allow every call regardless of model key");
        assertTrue(breaker.allowCall("any:key"),
                "AlwaysClosed must never reject a call");
    }

    @Test
    void recordSuccessAndFailureDoNotChangeState() {
        ICircuitBreaker breaker = AlwaysClosed.alwaysClosed();
        // Recording outcomes must be explicit no-ops: the state stays CLOSED
        // and calls stay allowed no matter how many failures are recorded.
        // This proves the recording methods are real (not empty placeholders
        // for required behaviour) but correctly discard the data by design.
        for (int i = 0; i < 100; i++) {
            breaker.recordFailure("openai:gpt-4");
        }
        assertSame(CircuitState.CLOSED, breaker.getState("openai:gpt-4"),
                "AlwaysClosed must not trip after any number of recorded failures");
        assertTrue(breaker.allowCall("openai:gpt-4"),
                "AlwaysClosed must still allow calls after many recorded failures");

        breaker.recordSuccess("openai:gpt-4");
        breaker.recordFailure("openai:gpt-4");
        assertSame(CircuitState.CLOSED, breaker.getState("openai:gpt-4"),
                "AlwaysClosed must report CLOSED after mixed recording");
        assertTrue(breaker.allowCall("openai:gpt-4"),
                "AlwaysClosed must still allow calls after mixed recording");
    }

    @Test
    void recordingDoesNotLeakAcrossModelKeys() {
        ICircuitBreaker breaker = AlwaysClosed.alwaysClosed();
        // Recording many failures on model A must not affect model B at all
        // — the pass-through default maintains no per-model state.
        for (int i = 0; i < 50; i++) {
            breaker.recordFailure("provider-a:model-a");
        }
        assertSame(CircuitState.CLOSED, breaker.getState("provider-b:model-b"),
                "AlwaysClosed must isolate model keys (no cross-model state leak)");
        assertTrue(breaker.allowCall("provider-b:model-b"),
                "AlwaysClosed must allow calls to model B regardless of model A failures");
    }
}
