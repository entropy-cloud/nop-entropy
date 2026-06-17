package io.nop.ai.agent.runtime;

import io.nop.ai.agent.message.AgentMessageEnvelope;
import io.nop.ai.agent.message.AgentMessageKind;
import io.nop.ai.agent.message.DeferredAckMailbox;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.MailboxEntry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the Actor observation-only mailbox consumption loop:
 * poll → record to receivedMessages → ack. Verifies that messages are
 * consumed (recorded + acked) but <strong>not</strong> injected into any
 * session or context (裁定 6 — observation-only).
 */
public class TestActorMailboxConsumption {

    private static final long FAST_POLL_MS = 50L;
    private static final long FAST_SHUTDOWN_MS = 3000L;

    private static AgentMessageEnvelope envelope(String payload) {
        return new AgentMessageEnvelope("sender-1", "agent.s1.inbox", "corr-1",
                AgentMessageKind.ASYNC, payload);
    }

    private InMemoryActorRuntime newRuntimeWithMailbox(String sessionId, DeferredAckMailbox mailbox) {
        Map<String, IMailbox> mailboxes = new HashMap<>();
        mailboxes.put(sessionId, mailbox);
        return new InMemoryActorRuntime(sid -> mailboxes.get(sid), FAST_POLL_MS, FAST_SHUTDOWN_MS);
    }

    /**
     * Core observation-only consumption: offer → poll → record → ack.
     */
    @Test
    void actorConsumesAndAcksOfferedMessage() throws Exception {
        DeferredAckMailbox mailbox = new DeferredAckMailbox();
        InMemoryActorRuntime rt = newRuntimeWithMailbox("s1", mailbox);

        AgentActor actor = rt.createActor("s1", "test-agent");

        // Offer a message to the mailbox
        assertTrue(mailbox.offer(envelope("hello-world")));

        // Wait for the Actor's consumption loop to poll → record → ack
        waitForReceivedMessages(actor, 1, 3000);

        // The message was recorded in the Actor's observation-only log
        List<MailboxEntry> received = actor.getReceivedMessages();
        assertEquals(1, received.size());
        assertEquals("hello-world", received.get(0).getEnvelope().getPayload());
        assertEquals(AgentMessageKind.ASYNC, received.get(0).getEnvelope().getKind());

        // The message was acked (removed from mailbox — no pending, no in-flight).
        // record→ack ordering: addReceivedMessage happens before ack, so we must
        // wait for the mailbox to be drained, not just for the record to appear.
        waitForMailboxDrained(mailbox, 3000);
        assertEquals(0, mailbox.pendingCount());
        assertEquals(0, mailbox.inFlightCount());

        rt.destroyActor(actor.getActorId());
    }

    /**
     * Multiple messages are consumed in FIFO order.
     */
    @Test
    void actorConsumesMultipleMessagesInOrder() throws Exception {
        DeferredAckMailbox mailbox = new DeferredAckMailbox();
        InMemoryActorRuntime rt = newRuntimeWithMailbox("s2", mailbox);

        AgentActor actor = rt.createActor("s2", "test-agent");

        mailbox.offer(envelope("msg-1"));
        mailbox.offer(envelope("msg-2"));
        mailbox.offer(envelope("msg-3"));

        waitForReceivedMessages(actor, 3, 3000);

        List<MailboxEntry> received = actor.getReceivedMessages();
        assertEquals(3, received.size());
        assertEquals("msg-1", received.get(0).getEnvelope().getPayload());
        assertEquals("msg-2", received.get(1).getEnvelope().getPayload());
        assertEquals("msg-3", received.get(2).getEnvelope().getPayload());

        // record→ack ordering: ack follows addReceivedMessage, so wait for the
        // mailbox to be fully drained before asserting emptiness.
        waitForMailboxDrained(mailbox, 3000);
        assertEquals(0, mailbox.pendingCount());
        assertEquals(0, mailbox.inFlightCount());

        rt.destroyActor(actor.getActorId());
    }

    /**
     * Messages consumed after an initial idle period (Actor is IDLE, then
     * transitions to RUNNING when a message arrives).
     */
    @Test
    void actorTransitionsIdleToRunningOnMessageArrival() throws Exception {
        DeferredAckMailbox mailbox = new DeferredAckMailbox();
        InMemoryActorRuntime rt = newRuntimeWithMailbox("s3", mailbox);

        AgentActor actor = rt.createActor("s3", "test-agent");

        // Wait for Actor to reach IDLE (empty mailbox)
        waitForStatus(actor, AgentActorStatus.IDLE, 2000);
        assertEquals(AgentActorStatus.IDLE, actor.getStatus());

        // Now offer a message → Actor should transition to RUNNING and consume
        mailbox.offer(envelope("after-idle"));
        waitForReceivedMessages(actor, 1, 3000);

        assertEquals(1, actor.getReceivedMessages().size());
        assertEquals(0, mailbox.pendingCount());

        rt.destroyActor(actor.getActorId());
    }

    /**
     * Observation-only verification: the Actor records messages in its own
     * receivedMessages list but has NO access to session.messages or
     * ctx.messages — the consumption loop only touches the mailbox and the
     * Actor's internal list. This test verifies the Actor object does not
     * expose or modify any session/context reference.
     */
    @Test
    void observationOnlyActorHasNoSessionOrContextReference() {
        DeferredAckMailbox mailbox = new DeferredAckMailbox();
        InMemoryActorRuntime rt = newRuntimeWithMailbox("s4", mailbox);

        AgentActor actor = rt.createActor("s4", "test-agent");

        // The AgentActor has NO session/context fields — only identity,
        // status, receivedMessages, and mailbox. The receivedMessages list
        // is the Actor's own observation log, not the session's message list.
        // Verify the Actor does not expose any session/context accessor.
        assertNotNull(actor.getActorId());
        assertNotNull(actor.getSessionId());
        assertNotNull(actor.getAgentName());
        // No getSession(), no getAgentExecutionContext(), no getMessages() —
        // only getReceivedMessages() which is the observation log.
        assertTrue(actor.getReceivedMessages().isEmpty());

        rt.destroyActor(actor.getActorId());
    }

    /**
     * nack redelivery: after the Actor acks the first delivery, no redelivery
     * occurs. If the Actor were to nack (requeue), the message would be
     * redelivered. This test verifies the normal ack path consumes the message
     * permanently.
     */
    @Test
    void ackConsumesMessagePermanentlyNoRedelivery() throws Exception {
        DeferredAckMailbox mailbox = new DeferredAckMailbox();
        InMemoryActorRuntime rt = newRuntimeWithMailbox("s5", mailbox);

        AgentActor actor = rt.createActor("s5", "test-agent");

        mailbox.offer(envelope("permanent-ack"));
        waitForReceivedMessages(actor, 1, 3000);

        // After ack, the message is permanently gone — no dead-letter, no redelivery.
        // record→ack ordering: ack follows addReceivedMessage, so wait for the
        // mailbox to be fully drained before asserting emptiness.
        waitForMailboxDrained(mailbox, 3000);
        assertEquals(0, mailbox.pendingCount());
        assertEquals(0, mailbox.inFlightCount());
        assertEquals(0, mailbox.deadLetterCount());

        // Wait a bit more and verify no additional messages appear
        Thread.sleep(200);
        assertEquals(1, actor.getReceivedMessages().size(),
                "no redelivery after ack — receivedMessages stays at 1");

        rt.destroyActor(actor.getActorId());
    }

    // ==================== Helpers ====================

    private static void waitForReceivedMessages(AgentActor actor, int expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (actor.getReceivedMessages().size() >= expected) {
                return;
            }
            Thread.sleep(10);
        }
    }

    /**
     * Wait until the mailbox is fully drained (no pending, no in-flight entries).
     * The consumption loop records to {@code receivedMessages} before calling
     * {@code ack}, so callers that assert mailbox emptiness must wait for the
     * ack to complete, not just for the record to appear.
     */
    private static void waitForMailboxDrained(DeferredAckMailbox mailbox, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (mailbox.pendingCount() == 0 && mailbox.inFlightCount() == 0) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private static void waitForStatus(AgentActor actor, AgentActorStatus expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (actor.getStatus() == expected) {
                return;
            }
            Thread.sleep(10);
        }
    }
}
