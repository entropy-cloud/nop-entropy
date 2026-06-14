package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInMemorySessionStoreFork {

    @Test
    void forkSessionReturnsNewSessionId() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("parent-1", "agent-a");

        String childId = store.forkSession("parent-1", true, null);

        assertNotNull(childId);
        assertNotEquals("parent-1", childId);
        assertNotNull(store.get(childId));
    }

    @Test
    void forkSessionInheritsMessagesWhenInheritContextTrue() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-2", "agent-a");
        parent.appendMessages(List.of(
                new ChatUserMessage("hello"),
                new ChatUserMessage("world")));

        String childId = store.forkSession("parent-2", true, null);

        AgentSession child = store.get(childId);
        assertEquals(2, child.getMessageCount());
        assertEquals(parent.getMessageCount(), child.getMessageCount());
    }

    @Test
    void forkSessionHasNoMessagesWhenInheritContextFalse() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-3", "agent-a");
        parent.appendMessages(List.of(new ChatUserMessage("hello")));

        String childId = store.forkSession("parent-3", false, null);

        AgentSession child = store.get(childId);
        assertEquals(0, child.getMessageCount());
    }

    @Test
    void childParentSessionIdPointsToParent() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("parent-4", "agent-a");

        String childId = store.forkSession("parent-4", true, null);

        AgentSession child = store.get(childId);
        assertEquals("parent-4", child.getParentSessionId());
    }

    @Test
    void forkSessionProducesIndependentMessageLists() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-5", "agent-a");
        parent.appendMessages(List.of(new ChatUserMessage("original")));

        String childId = store.forkSession("parent-5", true, null);
        AgentSession child = store.get(childId);
        child.appendMessages(List.of(new ChatUserMessage("appended-to-child")));

        assertEquals(1, parent.getMessageCount(),
                "Appending to child must not affect parent message count");
        assertEquals(2, child.getMessageCount());
    }

    @Test
    void forkSessionInheritsPlanIdWhenInheritContextTrue() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-6", "agent-a");
        parent.setPlanId("plan-xyz");

        String childId = store.forkSession("parent-6", true, null);

        AgentSession child = store.get(childId);
        assertEquals("plan-xyz", child.getPlanId());
    }

    @Test
    void forkSessionPlanIdIsNullWhenInheritContextFalse() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-7", "agent-a");
        parent.setPlanId("plan-xyz");

        String childId = store.forkSession("parent-7", false, null);

        AgentSession child = store.get(childId);
        assertNull(child.getPlanId());
    }

    @Test
    void forkSessionThrowsForNonExistentParent() {
        InMemorySessionStore store = new InMemorySessionStore();

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.forkSession("ghost-parent", true, null));
        assertTrue(ex.getMessage().contains("session not found"),
                "forkSession should fail-fast for a non-existent parent session");
    }

    @Test
    void forkSessionMergesPropsIntoChildMetadata() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("parent-8", "agent-a");

        Map<String, Object> props = new HashMap<>();
        props.put("env", "staging");
        props.put("priority", 5);

        String childId = store.forkSession("parent-8", true, props);

        AgentSession child = store.get(childId);
        assertEquals("staging", child.getMetadata().get("env"));
        assertEquals(5, child.getMetadata().get("priority"));
    }

    @Test
    void forkSessionAgentNameOverrideFromProps() {
        InMemorySessionStore store = new InMemorySessionStore();
        store.getOrCreate("parent-9", "agent-a");

        Map<String, Object> props = new HashMap<>();
        props.put("agentName", "agent-b");

        String childId = store.forkSession("parent-9", true, props);

        AgentSession child = store.get(childId);
        assertEquals("agent-b", child.getAgentName());
        assertNull(child.getMetadata().get("agentName"),
                "The agentName key should not leak into metadata");
    }

    @Test
    void forkSessionInheritsParentMetadataWhenInheritContextTrue() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-10", "agent-a");
        parent.setMetadata(Map.of("source", "parent"));

        String childId = store.forkSession("parent-10", true, null);

        AgentSession child = store.get(childId);
        assertEquals("parent", child.getMetadata().get("source"));
    }

    @Test
    void forkSessionParentMetadataIsIndependentCopy() {
        InMemorySessionStore store = new InMemorySessionStore();
        AgentSession parent = store.getOrCreate("parent-11", "agent-a");
        parent.setMetadata(new HashMap<>(Map.of("source", "parent")));

        String childId = store.forkSession("parent-11", true, null);
        AgentSession child = store.get(childId);
        child.getMetadata().put("child-only", "yes");

        assertNull(parent.getMetadata().get("child-only"),
                "Modifying child metadata must not affect parent metadata");
    }
}
