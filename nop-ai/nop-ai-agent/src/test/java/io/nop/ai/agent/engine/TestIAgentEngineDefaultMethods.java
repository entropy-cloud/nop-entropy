package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.session.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIAgentEngineDefaultMethods {

    private final IAgentEngine engine = new DefaultAgentEngine(null, null);

    @Test
    void forkSessionThrowsUOE() {
        AgentMessageRequest request = new AgentMessageRequest("test-agent", "hello");
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> engine.forkSession(request, true));
        assertEquals("forkSession requires Phase 2 ISessionStore", ex.getMessage());
    }

    @Test
    void getSessionStatusThrowsForUnknownSession() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.getSessionStatus("non-existent-session"));
        assertTrue(ex.getMessage().contains("session not found"),
                "getSessionStatus should fail-fast for a session that does not exist");
    }

    @Test
    void getSessionStatusReturnsStatusForExistingSession() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engineWithStore = new DefaultAgentEngine(null, null, store);
        store.getOrCreate("s1", "test-agent");

        assertEquals(AgentExecStatus.pending, engineWithStore.getSessionStatus("s1"),
                "A freshly created session should have status pending");
    }

    @Test
    void cancelSessionNoLongerThrowsUOE() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engineWithStore = new DefaultAgentEngine(null, null, store);
        store.getOrCreate("s1", "test-agent");

        CompletableFuture<Void> future = assertDoesNotThrow(
                () -> engineWithStore.cancelSession("s1", "user-requested", false));
        assertNotNull(future, "cancelSession should return a non-null future");
    }

    @Test
    void cancelSessionThrowsForUnknownSession() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> engine.cancelSession("no-such-session", "test", false));
        assertTrue(ex.getMessage().contains("session not found"),
                "cancelSession should fail-fast for a session that does not exist");
    }

    @Test
    void cancelSessionOnIdleSessionSetsCancelled() {
        InMemorySessionStore store = new InMemorySessionStore();
        DefaultAgentEngine engineWithStore = new DefaultAgentEngine(null, null, store);
        store.getOrCreate("idle-session", "test-agent");

        engineWithStore.cancelSession("idle-session", "user-cancel", false);

        assertEquals(AgentExecStatus.cancelled, engineWithStore.getSessionStatus("idle-session"),
                "Cancelling an existing-but-idle session should set its status to cancelled");
    }
}
