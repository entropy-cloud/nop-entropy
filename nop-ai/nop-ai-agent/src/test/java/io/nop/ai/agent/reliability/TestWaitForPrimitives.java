package io.nop.ai.agent.reliability;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the WAIT_FOR condition model + coordinator primitives
 * (design §13.1 Decisions D, H): WaitCondition JSON round-trip, WaitDecision
 * semantics, and NoOpWaitCoordinator zero-regression behavior.
 */
public class TestWaitForPrimitives {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // WaitCondition JSON serialization (Decision D)
    // ========================================================================

    @Test
    void timeoutConditionRoundTrip() {
        WaitCondition original = WaitCondition.timeout(1234567890L);
        String json = original.toJsonString();
        assertTrue(json.contains("\"type\":\"TIMEOUT\""));
        assertTrue(json.contains("\"deadlineMs\":1234567890"));

        WaitCondition parsed = WaitCondition.fromJson(json);
        assertNotNull(parsed);
        assertEquals(WaitCondition.Type.TIMEOUT, parsed.getType());
        assertEquals(1234567890L, parsed.getDeadlineMs());
        assertEquals(original, parsed);
    }

    @Test
    void eventConditionRoundTrip() {
        WaitCondition original = WaitCondition.event("user-approval");
        String json = original.toJsonString();
        assertTrue(json.contains("\"type\":\"EVENT\""));
        assertTrue(json.contains("\"key\":\"user-approval\""));

        WaitCondition parsed = WaitCondition.fromJson(json);
        assertNotNull(parsed);
        assertEquals(WaitCondition.Type.EVENT, parsed.getType());
        assertEquals("user-approval", parsed.getKey());
        assertEquals(original, parsed);
    }

    @Test
    void userInputConditionRoundTrip() {
        WaitCondition original = WaitCondition.userInput("form-response");
        String json = original.toJsonString();
        assertTrue(json.contains("\"type\":\"USER_INPUT\""));

        WaitCondition parsed = WaitCondition.fromJson(json);
        assertEquals(WaitCondition.Type.USER_INPUT, parsed.getType());
        assertEquals("form-response", parsed.getKey());
    }

    @Test
    void fromJsonReturnsNullForNullOrBlank() {
        assertNull(WaitCondition.fromJson(null));
        assertNull(WaitCondition.fromJson(""));
    }

    @Test
    void fromJsonThrowsOnUnknownType() {
        assertThrows(io.nop.ai.agent.engine.NopAiAgentException.class,
                () -> WaitCondition.fromJson("{\"type\":\"UNKNOWN\"}"));
    }

    // ========================================================================
    // WaitDecision semantics (Decision H)
    // ========================================================================

    @Test
    void noneDecisionHasNoneActionAndNullCondition() {
        WaitDecision d = WaitDecision.none();
        assertEquals(WaitDecision.Action.NONE, d.getAction());
        assertNull(d.getCondition());
    }

    @Test
    void suspendDecisionCarriesCondition() {
        WaitCondition wc = WaitCondition.event("test");
        WaitDecision d = WaitDecision.suspend(wc);
        assertEquals(WaitDecision.Action.SUSPEND, d.getAction());
        assertEquals(wc, d.getCondition());
    }

    @Test
    void proceedDecisionHasNoCondition() {
        WaitDecision d = WaitDecision.proceed();
        assertEquals(WaitDecision.Action.PROCEED, d.getAction());
        assertNull(d.getCondition());
    }

    @Test
    void suspendRejectsNullCondition() {
        assertThrows(IllegalArgumentException.class,
                () -> WaitDecision.suspend(null));
    }

    // ========================================================================
    // NoOpWaitCoordinator zero-regression (Decision G)
    // ========================================================================

    @Test
    void noOpCoordinatorAlwaysReturnsNone() {
        NoOpWaitCoordinator coord = NoOpWaitCoordinator.noOp();
        assertEquals(WaitDecision.none(), coord.checkWait("any-session"));
    }

    @Test
    void noOpCoordinatorRequestWaitIsNoOp() {
        NoOpWaitCoordinator coord = NoOpWaitCoordinator.noOp();
        coord.requestWait("sess", WaitCondition.event("key"));
        assertEquals(WaitDecision.none(), coord.checkWait("sess"),
                "NoOp coordinator must ignore requestWait — zero regression");
    }

    @Test
    void noOpCoordinatorIsAlwaysNotWaiting() {
        NoOpWaitCoordinator coord = NoOpWaitCoordinator.noOp();
        coord.requestWait("sess", WaitCondition.event("key"));
        assertTrue(!coord.isWaiting("sess"),
                "NoOp coordinator must never report waiting");
    }

    @Test
    void noOpCoordinatorDeliverWakeIsNoOp() {
        NoOpWaitCoordinator coord = NoOpWaitCoordinator.noOp();
        coord.deliverWake("sess", "payload");
        assertEquals(WaitDecision.none(), coord.checkWait("sess"));
    }

    @Test
    void noOpSingleton() {
        assertSame(NoOpWaitCoordinator.noOp(), NoOpWaitCoordinator.noOp());
    }

    private static void assertSame(Object expected, Object actual) {
        assertTrue(expected == actual, "Expected same singleton instance");
    }
}
