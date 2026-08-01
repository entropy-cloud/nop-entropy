package io.nop.ai.agent.reliability;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 unit tests for {@link DefaultWaitCoordinator} (design §13.1
 * Decisions C/D/H): verifies condition re-evaluation, anti-re-suspend
 * (deliverWake → PROCEED), injectable clock for timeout conditions, and
 * the no-miss wake guarantee.
 */
public class TestDefaultWaitCoordinator {

    // ========================================================================
    // Anti-re-suspend (Decision H — Blocker)
    // ========================================================================

    @Test
    void checkWaitReturnsSuspendBeforeWake() {
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        coord.requestWait("sess", WaitCondition.event("approval"));

        WaitDecision d = coord.checkWait("sess");
        assertEquals(WaitDecision.Action.SUSPEND, d.getAction(),
                "Before deliverWake, checkWait must return SUSPEND");
        assertEquals(WaitCondition.Type.EVENT, d.getCondition().getType());
    }

    @Test
    void checkWaitReturnsProceedAfterWake() {
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        coord.requestWait("sess", WaitCondition.event("approval"));

        // Before wake: SUSPEND
        assertEquals(WaitDecision.Action.SUSPEND, coord.checkWait("sess").getAction());

        // Deliver wake
        coord.deliverWake("sess", "user-response");

        // After wake: PROCEED (anti-re-suspend — the registration point
        // will skip suspend on replay re-entry)
        WaitDecision d = coord.checkWait("sess");
        assertEquals(WaitDecision.Action.PROCEED, d.getAction(),
                "After deliverWake, checkWait must return PROCEED (anti-re-suspend)");
    }

    @Test
    void proceedConsumesWaitSoSubsequentCheckReturnsNone() {
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        coord.requestWait("sess", WaitCondition.event("approval"));
        coord.deliverWake("sess", null);

        // First check after wake: PROCEED (consumes the wait)
        assertEquals(WaitDecision.Action.PROCEED, coord.checkWait("sess").getAction());

        // Second check: NONE (wait was consumed)
        assertEquals(WaitDecision.Action.NONE, coord.checkWait("sess").getAction(),
                "After PROCEED consumed the wait, subsequent checkWait must return NONE");
    }

    @Test
    void noReSuspendAfterWakeEndToEnd() {
        // Simulate the full flow: register → suspend → wake → proceed → none
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        coord.requestWait("sess", WaitCondition.userInput("form"));

        // Iteration 1: suspend
        WaitDecision d1 = coord.checkWait("sess");
        assertEquals(WaitDecision.Action.SUSPEND, d1.getAction());

        // External wake
        coord.deliverWake("sess", "user input data");

        // Iteration 2 (wake re-entry): proceed (NOT re-suspend)
        WaitDecision d2 = coord.checkWait("sess");
        assertEquals(WaitDecision.Action.PROCEED, d2.getAction(),
                "Wake re-entry must not re-suspend (Decision H Blocker)");

        // Iteration 3: normal execution resumes
        WaitDecision d3 = coord.checkWait("sess");
        assertEquals(WaitDecision.Action.NONE, d3.getAction());
    }

    // ========================================================================
    // Condition not satisfied → stays resident (no premature wake)
    // ========================================================================

    @Test
    void eventConditionNotSatisfiedStaysWaiting() {
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        coord.requestWait("sess", WaitCondition.event("key"));

        assertTrue(coord.isWaiting("sess"),
                "Session with unsatisfied event condition must be waiting");

        // Multiple checks without wake → always SUSPEND (stays resident)
        for (int i = 0; i < 3; i++) {
            assertEquals(WaitDecision.Action.SUSPEND, coord.checkWait("sess").getAction(),
                    "Without wake, checkWait must keep returning SUSPEND");
        }
        assertTrue(coord.isWaiting("sess"));
    }

    // ========================================================================
    // Timeout condition with injectable clock (Decision D — testable time)
    // ========================================================================

    @Test
    void timeoutConditionSatisfiedWhenDeadlinePassed() {
        AtomicLong clock = new AtomicLong(1000L);
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator(clock::get, null);

        // Register a timeout at deadline=2000
        coord.requestWait("sess", WaitCondition.timeout(2000L));

        // Before deadline: SUSPEND
        assertEquals(WaitDecision.Action.SUSPEND, coord.checkWait("sess").getAction());

        // Advance clock past deadline
        clock.set(2001L);

        // After deadline: PROCEED (condition satisfied by time)
        assertEquals(WaitDecision.Action.PROCEED, coord.checkWait("sess").getAction(),
                "After deadline passed, timeout condition must be satisfied → PROCEED");
    }

    @Test
    void timeoutConditionNotSatisfiedBeforeDeadline() {
        AtomicLong clock = new AtomicLong(1000L);
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator(clock::get, null);

        coord.requestWait("sess", WaitCondition.timeout(5000L));

        clock.set(4999L);
        assertEquals(WaitDecision.Action.SUSPEND, coord.checkWait("sess").getAction(),
                "Before deadline, timeout condition must not be satisfied");
        assertTrue(coord.isWaiting("sess"));
    }

    // ========================================================================
    // isWaiting / no-op for unknown sessions
    // ========================================================================

    @Test
    void isWaitingFalseForUnknownSession() {
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        assertFalse(coord.isWaiting("unknown"));
    }

    @Test
    void checkWaitReturnsNoneForUnknownSession() {
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        assertEquals(WaitDecision.Action.NONE, coord.checkWait("unknown").getAction());
    }

    @Test
    void deliverWakeOnUnknownSessionIsNoOp() {
        DefaultWaitCoordinator coord = new DefaultWaitCoordinator();
        coord.deliverWake("unknown", null);
        assertEquals(WaitDecision.Action.NONE, coord.checkWait("unknown").getAction());
        assertFalse(coord.isWaiting("unknown"));
    }
}
