package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan 212 (L3-8) Phase 2 focused test for {@link SisypheanSustainer}
 * (Minimum Rules #25). Verifies every decision path of the stateless
 * Sisyphean sustainer: MAX_ITERATIONS → CONTINUE under the ceiling,
 * MAX_ITERATIONS → STOP at/above the ceiling (fail-safe), non-sustainable
 * stopReason → STOP, and the stateless ceiling enforcement via
 * {@code SustainContext.sustainCountSoFar}.
 */
public class TestSisypheanSustainer {

    @Test
    void defaultMaxSustainCountIsThree() {
        SisypheanSustainer s = new SisypheanSustainer();
        assertEquals(SisypheanSustainer.DEFAULT_MAX_SUSTAIN_COUNT, s.getMaxSustainCount(),
                "Default ceiling must be DEFAULT_MAX_SUSTAIN_COUNT (3)");
    }

    @Test
    void customMaxSustainCountViaConstructor() {
        SisypheanSustainer s = new SisypheanSustainer(5);
        assertEquals(5, s.getMaxSustainCount(),
                "Custom ceiling must be honoured");
    }

    @Test
    void negativeMaxSustainCountRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SisypheanSustainer(-1),
                "Negative maxSustainCount must be rejected");
    }

    @Test
    void zeroMaxSustainCountActsLikeNoOpForMaxIterations() {
        // A ceiling of 0 means the sustainer never forces a continuation —
        // equivalent to NoOpSustainer for the MAX_ITERATIONS exit point.
        SisypheanSustainer s = new SisypheanSustainer(0);
        SustainContext ctx = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 10, 0);
        assertEquals(SustainDecision.STOP, s.onStop(ctx),
                "maxSustainCount=0 must return STOP even at sustainCountSoFar=0");
    }

    // ========================================================================
    // MAX_ITERATIONS → CONTINUE under the ceiling
    // ========================================================================

    @Test
    void maxIterationsUnderCeilingReturnsContinue() {
        SisypheanSustainer s = new SisypheanSustainer(3);
        // sustainCountSoFar = 0, 1, 2 are all < 3 → CONTINUE
        for (int sustainSoFar = 0; sustainSoFar < 3; sustainSoFar++) {
            SustainContext ctx = new SustainContext(
                    "session-a", SustainStopReason.MAX_ITERATIONS, 10 + sustainSoFar * 10, sustainSoFar);
            assertEquals(SustainDecision.CONTINUE, s.onStop(ctx),
                    "MAX_ITERATIONS with sustainCountSoFar=" + sustainSoFar
                            + " < maxSustainCount=3 must return CONTINUE");
        }
    }

    // ========================================================================
    // MAX_ITERATIONS → STOP at/above the ceiling (fail-safe)
    // ========================================================================

    @Test
    void maxIterationsAtCeilingReturnsStop() {
        SisypheanSustainer s = new SisypheanSustainer(3);
        // sustainCountSoFar = 3 = maxSustainCount → STOP (ceiling reached)
        SustainContext ctx = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 40, 3);
        assertEquals(SustainDecision.STOP, s.onStop(ctx),
                "MAX_ITERATIONS with sustainCountSoFar=3 = maxSustainCount=3 must return STOP "
                        + "(ceiling reached, fail-safe)");
    }

    @Test
    void maxIterationsAboveCeilingReturnsStop() {
        SisypheanSustainer s = new SisypheanSustainer(3);
        // sustainCountSoFar = 5 > maxSustainCount=3 → STOP (defensive)
        SustainContext ctx = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 60, 5);
        assertEquals(SustainDecision.STOP, s.onStop(ctx),
                "MAX_ITERATIONS with sustainCountSoFar > maxSustainCount must return STOP");
    }

    // ========================================================================
    // Stateless: the same instance serves multiple sessions without
    // cross-contamination. The per-execution sustainCountSoFar (passed in via
    // SustainContext) is the only sustain counter — the sustainer holds none.
    // ========================================================================

    @Test
    void statelessAcrossSessions() {
        SisypheanSustainer s = new SisypheanSustainer(2);
        // Session A: sustainCountSoFar = 0 → CONTINUE
        SustainContext ctxA0 = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 10, 0);
        assertEquals(SustainDecision.CONTINUE, s.onStop(ctxA0),
                "Session A at sustainCountSoFar=0 must get CONTINUE");

        // Session B: sustainCountSoFar = 0 → CONTINUE (independent of session A)
        SustainContext ctxB0 = new SustainContext(
                "session-b", SustainStopReason.MAX_ITERATIONS, 10, 0);
        assertEquals(SustainDecision.CONTINUE, s.onStop(ctxB0),
                "Session B at sustainCountSoFar=0 must get CONTINUE independent of session A");

        // Session A: sustainCountSoFar = 1 → CONTINUE (still under ceiling 2)
        SustainContext ctxA1 = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 20, 1);
        assertEquals(SustainDecision.CONTINUE, s.onStop(ctxA1),
                "Session A at sustainCountSoFar=1 < ceiling=2 must get CONTINUE");

        // Session A: sustainCountSoFar = 2 → STOP (ceiling reached)
        SustainContext ctxA2 = new SustainContext(
                "session-a", SustainStopReason.MAX_ITERATIONS, 30, 2);
        assertEquals(SustainDecision.STOP, s.onStop(ctxA2),
                "Session A at sustainCountSoFar=2 = ceiling must get STOP");

        // Session B: sustainCountSoFar = 1 → CONTINUE (still under ceiling 2,
        // independent of session A reaching its ceiling)
        SustainContext ctxB1 = new SustainContext(
                "session-b", SustainStopReason.MAX_ITERATIONS, 20, 1);
        assertEquals(SustainDecision.CONTINUE, s.onStop(ctxB1),
                "Session B at sustainCountSoFar=1 < ceiling must get CONTINUE "
                        + "even after session A reached its ceiling (stateless isolation)");
    }

    // ========================================================================
    // at-least-once semantics: the agent gets exactly maxSustainCount
    // additional sustain rounds before the ceiling stops it.
    // ========================================================================

    @Test
    void atLeastOnceSemanticsMaxSustainCountContinues() {
        // With maxSustainCount=3, the agent must get exactly 3 CONTINUEs
        // (sustainCountSoFar = 0, 1, 2) and then STOP (sustainCountSoFar = 3).
        SisypheanSustainer s = new SisypheanSustainer(3);
        int continueCount = 0;
        for (int sustainSoFar = 0; sustainSoFar <= 5; sustainSoFar++) {
            SustainContext ctx = new SustainContext(
                    "session-a", SustainStopReason.MAX_ITERATIONS, 10 + sustainSoFar * 10, sustainSoFar);
            SustainDecision decision = s.onStop(ctx);
            if (decision == SustainDecision.CONTINUE) {
                continueCount++;
            } else {
                // Once STOP is returned, all subsequent calls must also be STOP
                assertEquals(SustainDecision.STOP, decision,
                        "STOP must be sticky once the ceiling is reached (sustainCountSoFar=" + sustainSoFar + ")");
                for (int later = sustainSoFar + 1; later <= 5; later++) {
                    SustainContext laterCtx = new SustainContext(
                            "session-a", SustainStopReason.MAX_ITERATIONS, 10 + later * 10, later);
                    assertEquals(SustainDecision.STOP, s.onStop(laterCtx),
                            "STOP must persist for all sustainCountSoFar >= ceiling");
                }
                break;
            }
        }
        assertEquals(3, continueCount,
                "Exactly maxSustainCount=3 CONTINUEs must be granted (at-least-once: 3 additional sustain rounds)");
    }

    @Test
    void nullContextRejected() {
        SisypheanSustainer s = new SisypheanSustainer();
        assertThrows(IllegalArgumentException.class, () -> s.onStop(null),
                "onStop(null) must be rejected");
    }
}
