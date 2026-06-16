package io.nop.ai.agent.runtime;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link InMemoryActorRegistry} dual-index registration,
 * unregistration, and query operations.
 */
public class TestInMemoryActorRegistry {

    private AgentActor actor(String actorId, String sessionId) {
        return new AgentActor(actorId, sessionId, "test-agent", System.currentTimeMillis(), null);
    }

    @Test
    void registerAndGetByActorId() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        AgentActor a = actor("a1", "s1");
        reg.register(a);

        Optional<AgentActor> found = reg.get("a1");
        assertTrue(found.isPresent());
        assertEquals(a, found.get());
    }

    @Test
    void registerAndGetBySessionId() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        AgentActor a = actor("a1", "s1");
        reg.register(a);

        Optional<AgentActor> found = reg.getBySession("s1");
        assertTrue(found.isPresent());
        assertEquals(a, found.get());
    }

    @Test
    void registerReplacesExistingActorId() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        AgentActor original = actor("a1", "s1");
        reg.register(original);

        // Re-register with same actorId but different session → replaces
        AgentActor replacement = actor("a1", "s2");
        reg.register(replacement);

        assertEquals(replacement, reg.get("a1").get());
        // Old session index should be gone (replacement has different sessionId)
        assertFalse(reg.getBySession("s1").isPresent());
        assertTrue(reg.getBySession("s2").isPresent());
    }

    @Test
    void registerNewActorForSameSessionUpdatesSessionIndex() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        AgentActor first = actor("a1", "s1");
        reg.register(first);

        // New actor for same session (e.g. after terminal status replacement)
        AgentActor second = actor("a2", "s1");
        reg.register(second);

        // Session index points to the new actor
        assertEquals("a2", reg.getBySession("s1").get().getActorId());
        // Old actor still accessible by its own actorId until explicitly unregistered
        assertTrue(reg.get("a1").isPresent());
    }

    @Test
    void unregisterRemovesFromBothIndices() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        AgentActor a = actor("a1", "s1");
        reg.register(a);

        reg.unregister("a1");

        assertFalse(reg.get("a1").isPresent());
        assertFalse(reg.getBySession("s1").isPresent());
    }

    @Test
    void unregisterOnlyRemovesMatchingSessionIndex() {
        // When a new actor has replaced the session index, unregistering
        // the old actor must not clobber the new one's session index entry.
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        AgentActor first = actor("a1", "s1");
        reg.register(first);
        AgentActor second = actor("a2", "s1");
        reg.register(second); // session index now points to a2

        reg.unregister("a1"); // remove old actor

        // Session index still points to a2
        assertEquals("a2", reg.getBySession("s1").get().getActorId());
        assertFalse(reg.get("a1").isPresent());
        assertTrue(reg.get("a2").isPresent());
    }

    @Test
    void unregisterUnknownIsNoOp() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        reg.unregister("nonexistent"); // no exception
        reg.unregister(null); // no exception
    }

    @Test
    void getAllReturnsAllRegistered() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        reg.register(actor("a1", "s1"));
        reg.register(actor("a2", "s2"));
        reg.register(actor("a3", "s3"));

        Collection<AgentActor> all = reg.getAll();
        assertEquals(3, all.size());
    }

    @Test
    void getAllReturnsEmptyWhenNothingRegistered() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        assertTrue(reg.getAll().isEmpty());
    }

    @Test
    void getNullActorIdReturnsEmpty() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        assertTrue(reg.get(null).isEmpty());
    }

    @Test
    void getBySessionNullReturnsEmpty() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        assertTrue(reg.getBySession(null).isEmpty());
    }

    @Test
    void registerNullThrows() {
        InMemoryActorRegistry reg = new InMemoryActorRegistry();
        assertThrows(IllegalArgumentException.class, () -> reg.register(null));
    }
}
