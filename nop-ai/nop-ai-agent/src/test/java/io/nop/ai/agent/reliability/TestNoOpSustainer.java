package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 212 (L3-8) Phase 1 focused test for {@link NoOpSustainer} (Minimum
 * Rules #25). Verifies the shipped default unconditionally returns STOP for
 * every sustainable exit point — preserving the engine's pre-plan-212
 * zero-sustain behaviour (no forced continuation ever fires).
 */
public class TestNoOpSustainer {

    @Test
    void noOpReturnsSingletonViaFactory() {
        ISustainer a = NoOpSustainer.noOp();
        ISustainer b = NoOpSustainer.noOp();
        assertSame(a, b, "noOp() must return the same singleton instance");
    }

    @Test
    void onStopAlwaysReturnsStopForMaxIterationsExit() {
        ISustainer sustainer = NoOpSustainer.noOp();
        // A MAX_ITERATIONS exit at any iteration / sustain count must always
        // return STOP — the pass-through default never forces a continuation.
        SustainContext ctx0 = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 10, 0);
        assertEquals(SustainDecision.STOP, sustainer.onStop(ctx0),
                "NoOpSustainer must return STOP for a MAX_ITERATIONS exit (zero sustain rounds)");

        SustainContext ctx1 = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 20, 1);
        assertEquals(SustainDecision.STOP, sustainer.onStop(ctx1),
                "NoOpSustainer must return STOP for a MAX_ITERATIONS exit (one sustain round)");
    }

    @Test
    void onStopAlwaysReturnsStopForAnySession() {
        ISustainer sustainer = NoOpSustainer.noOp();
        assertEquals(SustainDecision.STOP,
                sustainer.onStop(new SustainContext("session-a", SustainStopReason.MAX_ITERATIONS, 10, 0)),
                "NoOpSustainer must return STOP for any session");
        assertEquals(SustainDecision.STOP,
                sustainer.onStop(new SustainContext("session-b", SustainStopReason.MAX_ITERATIONS, 10, 0)),
                "NoOpSustainer must return STOP for any session");
        assertEquals(SustainDecision.STOP,
                sustainer.onStop(new SustainContext(null, SustainStopReason.MAX_ITERATIONS, 10, 0)),
                "NoOpSustainer must return STOP for anonymous (null) sessions");
    }

    @Test
    void onStopIsExplicitNoOpDecisionAndNeverContinues() {
        ISustainer sustainer = NoOpSustainer.noOp();
        // Calling onStop many times must always return STOP — the pass-through
        // default maintains no per-execution sustain state and never forces a
        // continuation. This proves onStop is a real pass-through decision
        // (not an empty placeholder) that correctly discards the sustain
        // opportunity by design.
        for (int i = 0; i < 100; i++) {
            SustainContext ctx = new SustainContext(
                    "session-a", SustainStopReason.MAX_ITERATIONS, 10 + i * 10, i);
            assertEquals(SustainDecision.STOP, sustainer.onStop(ctx),
                    "NoOpSustainer must never return CONTINUE after any number of onStop calls "
                            + "(call " + i + ")");
        }
    }
}
