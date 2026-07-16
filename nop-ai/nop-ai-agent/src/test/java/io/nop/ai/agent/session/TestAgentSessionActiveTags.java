package io.nop.ai.agent.session;

import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 296 (WS2): unit tests for {@link AgentSession#resolveActiveTags}.
 * Verifies the three-level precedence:
 * session override → model declared → empty (full visibility).
 */
public class TestAgentSessionActiveTags {

    @Test
    void nullSessionAndModelReturnsEmpty() {
        AgentSession session = AgentSession.create("s1", "agent");
        assertEquals(Set.of(), session.resolveActiveTags(null));
    }

    @Test
    void nullSessionOverrideFallsBackToModel() {
        AgentSession session = AgentSession.create("s1", "agent");
        AgentModel model = new AgentModel();
        model.setActiveTags(Set.of("readonly", "channel:webui"));

        assertEquals(Set.of("readonly", "channel:webui"), session.resolveActiveTags(model));
    }

    @Test
    void nullSessionOverrideAndNullModelReturnsEmpty() {
        AgentSession session = AgentSession.create("s1", "agent");
        AgentModel model = new AgentModel();

        assertEquals(Set.of(), session.resolveActiveTags(model));
    }

    @Test
    void sessionOverrideTakesPrecedenceOverModel() {
        AgentSession session = AgentSession.create("s1", "agent");
        session.setActiveTags(Set.of("admin"));

        AgentModel model = new AgentModel();
        model.setActiveTags(Set.of("readonly"));

        assertEquals(Set.of("admin"), session.resolveActiveTags(model));
    }

    @Test
    void clearingSessionOverrideFallsBackToModel() {
        AgentSession session = AgentSession.create("s1", "agent");
        session.setActiveTags(Set.of("admin"));

        AgentModel model = new AgentModel();
        model.setActiveTags(Set.of("readonly"));

        // Verify override works
        assertEquals(Set.of("admin"), session.resolveActiveTags(model));

        // Clear override (set to null) → falls back to model
        session.setActiveTags(null);
        assertEquals(Set.of("readonly"), session.resolveActiveTags(model));
    }

    @Test
    void emptySessionOverrideMeansNoTagFilter() {
        AgentSession session = AgentSession.create("s1", "agent");
        session.setActiveTags(Set.of());

        AgentModel model = new AgentModel();
        model.setActiveTags(Set.of("readonly"));

        // Empty session override takes precedence → empty = no tag filter
        assertEquals(Set.of(), session.resolveActiveTags(model));
    }

    @Test
    void activeTagsInitiallyNull() {
        AgentSession session = AgentSession.create("s1", "agent");
        assertNull(session.getActiveTags());
        assertTrue(session.resolveActiveTags(null).isEmpty());
    }
}
