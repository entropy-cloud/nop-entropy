package io.nop.ai.agent.runtime;

import io.nop.ai.agent.message.DeferredAckMailbox;
import io.nop.ai.agent.message.IMailbox;
import io.nop.ai.agent.message.MailboxDeliveryState;
import io.nop.ai.agent.message.MailboxEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link AgentActor} identity, state transitions, and
 * observation-only received-messages list.
 */
public class TestAgentActor {

    @Test
    void constructionSetsImmutableIdentityAndCreatedStatus() {
        long before = System.currentTimeMillis();
        IMailbox mailbox = new DeferredAckMailbox();
        AgentActor actor = new AgentActor("actor-1", "sess-1", "test-agent", before, mailbox);

        assertEquals("actor-1", actor.getActorId());
        assertEquals("sess-1", actor.getSessionId());
        assertEquals("test-agent", actor.getAgentName());
        assertEquals(before, actor.getCreatedAt());
        assertEquals(AgentActorStatus.CREATED, actor.getStatus());
        assertEquals(before, actor.getLastActiveAt());
        assertEquals(mailbox, actor.getMailbox());
    }

    @Test
    void constructionWithNullMailboxAllowed() {
        AgentActor actor = new AgentActor("actor-2", "sess-2", "agent", System.currentTimeMillis(), null);
        assertNull(actor.getMailbox());
    }

    @Test
    void updateStatusTransitions() {
        AgentActor actor = new AgentActor("a", "s", "g", System.currentTimeMillis(), null);

        actor.updateStatus(AgentActorStatus.READY);
        assertEquals(AgentActorStatus.READY, actor.getStatus());

        actor.updateStatus(AgentActorStatus.RUNNING);
        assertEquals(AgentActorStatus.RUNNING, actor.getStatus());

        actor.updateStatus(AgentActorStatus.IDLE);
        assertEquals(AgentActorStatus.IDLE, actor.getStatus());

        actor.updateStatus(AgentActorStatus.STOPPED);
        assertEquals(AgentActorStatus.STOPPED, actor.getStatus());
    }

    @Test
    void updateStatusNullThrows() {
        AgentActor actor = new AgentActor("a", "s", "g", 0L, null);
        assertThrows(NullPointerException.class, () -> actor.updateStatus(null));
    }

    @Test
    void touchUpdatesLastActiveAt() throws InterruptedException {
        AgentActor actor = new AgentActor("a", "s", "g", 1000L, null);
        assertEquals(1000L, actor.getLastActiveAt());

        Thread.sleep(5);
        actor.touch();
        assertTrue(actor.getLastActiveAt() > 1000L,
                "touch() must update lastActiveAt to current time");
    }

    @Test
    void receivedMessagesRecordsAndReturnsSnapshot() {
        AgentActor actor = new AgentActor("a", "s", "g", 0L, null);
        assertTrue(actor.getReceivedMessages().isEmpty());

        MailboxEntry entry = new MailboxEntry(1, 1, MailboxDeliveryState.IN_FLIGHT,
                null, System.currentTimeMillis(), System.currentTimeMillis());
        actor.addReceivedMessage(entry);

        List<MailboxEntry> snapshot = actor.getReceivedMessages();
        assertEquals(1, snapshot.size());
        assertEquals(entry, snapshot.get(0));

        // Mutating the snapshot must not affect the actor's internal list.
        snapshot.clear();
        assertEquals(1, actor.getReceivedMessages().size(),
                "getReceivedMessages must return a defensive copy");
    }

    @Test
    void addReceivedMessageNullThrows() {
        AgentActor actor = new AgentActor("a", "s", "g", 0L, null);
        assertThrows(NullPointerException.class, () -> actor.addReceivedMessage(null));
    }

    @Test
    void constructorNullIdentityFieldsThrow() {
        assertThrows(NullPointerException.class,
                () -> new AgentActor(null, "s", "g", 0L, null));
        assertThrows(NullPointerException.class,
                () -> new AgentActor("a", null, "g", 0L, null));
        assertThrows(NullPointerException.class,
                () -> new AgentActor("a", "s", null, 0L, null));
    }

    @Test
    void toStringContainsKeyFields() {
        AgentActor actor = new AgentActor("actor-x", "sess-x", "my-agent", 42L, null);
        String s = actor.toString();
        assertTrue(s.contains("actor-x"));
        assertTrue(s.contains("sess-x"));
        assertTrue(s.contains("my-agent"));
        assertTrue(s.contains("CREATED"));
    }
}
