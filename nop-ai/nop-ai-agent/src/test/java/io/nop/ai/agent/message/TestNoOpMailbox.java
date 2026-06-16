package io.nop.ai.agent.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 focused tests for {@link NoOpMailbox} — the explicit no-op default.
 * Every method must return a result that unambiguously signals "no operation
 * occurred" (Minimum Rules #24), never a silent success.
 */
public class TestNoOpMailbox {

    @Test
    void noOpReturnsSameSingletonInstance() {
        IMailbox a = NoOpMailbox.noOp();
        IMailbox b = NoOpMailbox.noOp();
        assertSame(a, b);
    }

    @Test
    void offerReturnsFalseExplicitRefusal() {
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "s", "agent.t.inbox", null, AgentMessageKind.ASYNC, "p");
        assertFalse(NoOpMailbox.noOp().offer(env),
                "NoOpMailbox.offer must return false (explicit refusal, not silent success)");
    }

    @Test
    void pollReturnsNull() {
        assertNull(NoOpMailbox.noOp().poll(),
                "NoOpMailbox.poll must return null (empty)");
    }

    @Test
    void ackReturnsFalse() {
        assertFalse(NoOpMailbox.noOp().ack(1L),
                "NoOpMailbox.ack must return false (nothing to ack)");
    }

    @Test
    void nackReturnsFalse() {
        assertFalse(NoOpMailbox.noOp().nack(1L, true),
                "NoOpMailbox.nack must return false (nothing to nack)");
        assertFalse(NoOpMailbox.noOp().nack(1L, false));
    }

    @Test
    void countsReturnZero() {
        IMailbox mb = NoOpMailbox.noOp();
        assertEquals(0, mb.pendingCount());
        assertEquals(0, mb.inFlightCount());
    }

    @Test
    void noOpIsDistinctFromFunctionalMailboxBehaviour() {
        // The whole point: a caller can tell NoOp apart from a real mailbox by
        // checking offer's return value, rather than mistaking silent success.
        IMailbox noOp = NoOpMailbox.noOp();
        IMailbox functional = new DeferredAckMailbox();
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "s", "agent.t.inbox", null, AgentMessageKind.ASYNC, "p");
        assertFalse(noOp.offer(env));
        assertTrue(functional.offer(env),
                "functional mailbox must accept; this contrast proves NoOp is an explicit refusal");
    }
}
