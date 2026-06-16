package io.nop.ai.agent.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 focused tests for {@link MailboxMessageHandler} — the
 * {@link IAgentMessageHandler} adapter that offers envelopes to an
 * {@link IMailbox}.
 */
public class TestMailboxMessageHandler {

    @Test
    void onMessageOffersToMailboxAndReturnsNull() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        MailboxMessageHandler handler = new MailboxMessageHandler(mb);

        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "s", "agent.t.inbox", null, AgentMessageKind.ASYNC, "payload-x");

        Object result = handler.onMessage(env);
        assertNull(result, "ASYNC message handler must return null (no response)");

        assertEquals(1, mb.pendingCount(), "envelope must have been offered to the mailbox");
        MailboxEntry entry = mb.poll();
        assertTrue(entry != null && "payload-x".equals(entry.getEnvelope().getPayload()));
    }

    @Test
    void onMessageWithFullMailboxLogsWarnAndReturnsNullNoThrow() {
        // bounded mailbox with capacity 1, pre-filled
        DeferredAckMailbox mb = new DeferredAckMailbox(1, 5);
        assertTrue(mb.offer(new AgentMessageEnvelope(
                "s", "agent.t.inbox", null, AgentMessageKind.ASYNC, "first")));

        MailboxMessageHandler handler = new MailboxMessageHandler(mb);
        AgentMessageEnvelope rejected = new AgentMessageEnvelope(
                "s", "agent.t.inbox", null, AgentMessageKind.ASYNC, "second");

        // offer-rejected path must not throw; must return null
        Object result = assertThrowsNothing(() -> handler.onMessage(rejected));
        assertNull(result);
        assertEquals(1, mb.pendingCount(), "rejected envelope must not be in the mailbox");
    }

    @Test
    void onMessageWithNoOpMailboxLogsWarnAndReturnsNullNoThrow() {
        MailboxMessageHandler handler = new MailboxMessageHandler(NoOpMailbox.noOp());
        AgentMessageEnvelope env = new AgentMessageEnvelope(
                "s", "agent.t.inbox", null, AgentMessageKind.ASYNC, "p");
        Object result = handler.onMessage(env);
        assertNull(result);
    }

    @Test
    void constructorRejectsNullMailbox() {
        assertThrows(NullPointerException.class, () -> new MailboxMessageHandler(null));
    }

    @Test
    void getMailboxReturnsWiredInstance() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        MailboxMessageHandler handler = new MailboxMessageHandler(mb);
        assertSame(mb, handler.getMailbox());
    }

    /** helper: run a runnable and return null (for fluent null-result assertions). */
    private static Object assertThrowsNothing(Runnable r) {
        r.run();
        return null;
    }
}
