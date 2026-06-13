package io.nop.ai.agent.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void getSessionStatusThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> engine.getSessionStatus("s1"));
        assertEquals("getSessionStatus requires Phase 2", ex.getMessage());
    }

    @Test
    void cancelSessionThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> engine.cancelSession("s1", "user-requested", false));
        assertEquals("cancelSession requires Phase 2", ex.getMessage());
    }
}
