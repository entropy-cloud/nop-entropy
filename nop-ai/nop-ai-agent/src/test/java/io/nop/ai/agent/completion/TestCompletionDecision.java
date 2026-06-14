package io.nop.ai.agent.completion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCompletionDecision {

    @Test
    void completeIsSingleton() {
        CompletionDecision.Complete a = CompletionDecision.Complete.instance();
        CompletionDecision.Complete b = CompletionDecision.Complete.instance();
        assertSame(a, b);
    }

    @Test
    void completeIsComplete() {
        CompletionDecision result = CompletionDecision.Complete.instance();
        assertTrue(result.isComplete());
        assertEquals(false, result.isContinue());
        assertEquals(false, result.isEscalate());
    }

    @Test
    void continueCarriesMessage() {
        CompletionDecision.Continue result = new CompletionDecision.Continue("You have not finished yet");
        assertEquals("You have not finished yet", result.getMessage());
        assertTrue(result.isContinue());
    }

    @Test
    void continueNullMessage() {
        CompletionDecision.Continue result = new CompletionDecision.Continue(null);
        assertNull(result.getMessage());
        assertTrue(result.isContinue());
    }

    @Test
    void escalateCarriesReason() {
        CompletionDecision.Escalate result = new CompletionDecision.Escalate("requires human approval");
        assertEquals("requires human approval", result.getReason());
        assertTrue(result.isEscalate());
    }

    @Test
    void escalateNullReason() {
        CompletionDecision.Escalate result = new CompletionDecision.Escalate(null);
        assertNull(result.getReason());
        assertTrue(result.isEscalate());
    }

    @Test
    void concreteTypesExtendCompletionDecision() {
        assertNotNull((CompletionDecision) CompletionDecision.Complete.instance());
        assertNotNull((CompletionDecision) new CompletionDecision.Continue("m"));
        assertNotNull((CompletionDecision) new CompletionDecision.Escalate("r"));
    }

    @Test
    void outcomesAreDistinguishable() {
        CompletionDecision complete = CompletionDecision.Complete.instance();
        CompletionDecision cont = new CompletionDecision.Continue("keep going");
        CompletionDecision escalate = new CompletionDecision.Escalate("stuck");

        assertTrue(complete.isComplete() && !complete.isContinue() && !complete.isEscalate());
        assertTrue(cont.isContinue() && !cont.isComplete() && !cont.isEscalate());
        assertTrue(escalate.isEscalate() && !escalate.isComplete() && !escalate.isContinue());
    }
}
