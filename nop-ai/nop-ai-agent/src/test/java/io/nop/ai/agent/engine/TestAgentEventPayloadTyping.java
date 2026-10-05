package io.nop.ai.agent.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 2290 (WI9): typed payload view — key-constant access with null-safe
 * missing-key behaviour, and the schemaVersion ignorable contract.
 */
public class TestAgentEventPayloadTyping {

    @Test
    void typedViewReadsKnownKeysWithCorrectTypes() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(AgentEventPayloads.ITERATION, 3);
        payload.put(AgentEventPayloads.TOOL_NAME, "bash");
        payload.put(AgentEventPayloads.PENDING_COUNT, 7L);
        payload.put(AgentEventPayloads.GUARDRAIL_BLOCKED, true);

        AgentEvent event = AgentEvent.create(AgentEventType.LLM_RESPONSE_RECEIVED,
                "s1", "agent", payload);
        AgentEvent.TypedPayload typed = event.typedPayload();
        assertEquals(Integer.valueOf(3), typed.getInteger(AgentEventPayloads.ITERATION));
        assertEquals("bash", typed.getString(AgentEventPayloads.TOOL_NAME));
        assertEquals(Long.valueOf(7), typed.getLong(AgentEventPayloads.PENDING_COUNT));
        assertEquals(Boolean.TRUE, typed.getBoolean(AgentEventPayloads.GUARDRAIL_BLOCKED));
    }

    @Test
    void missingKeysReturnNullNeverThrow() {
        AgentEvent event = AgentEvent.create(AgentEventType.LLM_RESPONSE_RECEIVED,
                "s1", "agent", new HashMap<>());
        AgentEvent.TypedPayload typed = event.typedPayload();
        assertNull(typed.getString(AgentEventPayloads.REASON));
        assertNull(typed.getInteger(AgentEventPayloads.ITERATION));
        assertNull(typed.getLong(AgentEventPayloads.PENDING_COUNT));
        assertNull(typed.getBoolean(AgentEventPayloads.GUARDRAIL_BLOCKED));
    }

    @Test
    void typeMismatchReturnsNullNotThrow() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(AgentEventPayloads.ITERATION, "not-a-number");
        AgentEvent event = AgentEvent.create(AgentEventType.LLM_RESPONSE_RECEIVED,
                "s1", "agent", payload);
        assertNull(event.typedPayload().getInteger(AgentEventPayloads.ITERATION),
                "type mismatch → null (ignorable, no throw)");
    }

    @Test
    void schemaVersionDefaultsToCurrentAndSurvives() {
        AgentEvent event = AgentEvent.create(AgentEventType.EXECUTION_STARTED,
                "s1", "agent", null);
        assertEquals(AgentEvent.CURRENT_SCHEMA_VERSION, event.getSchemaVersion());

        AgentEvent explicit = new AgentEvent(AgentEventType.EXECUTION_STARTED,
                "s1", "agent", null, null, 2);
        assertEquals(2, explicit.getSchemaVersion(),
                "explicit schema version is preserved for ignorable-contract consumers");
    }
}
