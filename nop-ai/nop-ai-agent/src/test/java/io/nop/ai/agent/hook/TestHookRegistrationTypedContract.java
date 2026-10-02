package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.NopAiAgentException;

import io.nop.ai.agent.model.AgentHookModel;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2294 (WI13): hook registration typed contract — unknown event names in
 * agent.xml fail loud at registration instead of being silently skipped (the
 * dead-configuration surface). The removed REASONING_CHUNK gets a dedicated
 * message. Middleware point resolution fails loud too.
 */
public class TestHookRegistrationTypedContract {

    private AgentHookModel hookModel(String event) {
        AgentHookModel hookModel = new AgentHookModel();
        // KeyedList unique-key: hooks need distinct ids
        hookModel.setId(event);
        hookModel.setEvent(event);
        return hookModel;
    }

    @Test
    void unknownHookEventFailsLoudWithEventName() {
        AgentModel model = new AgentModel();
        model.setHooks(List.of(hookModel("typo_event_name")));

        NopAiAgentException e = assertThrows(NopAiAgentException.class,
                () -> DefaultHookRegistry.fromAgentModel(model));
        assertTrue(String.valueOf(e.getMessage()).contains("typo_event_name"),
                "error carries the bad event name: " + e.getMessage());
        assertTrue(String.valueOf(e.getMessage()).contains("valid events"),
                "error lists valid events: " + e.getMessage());
    }

    @Test
    void removedReasoningChunkGetsDedicatedMessage() {
        AgentModel model = new AgentModel();
        model.setHooks(List.of(hookModel("reasoning_chunk")));

        NopAiAgentException e = assertThrows(NopAiAgentException.class,
                () -> DefaultHookRegistry.fromAgentModel(model));
        assertTrue(String.valueOf(e.getMessage()).contains("removed in plan 2026-09-15-1029-2"),
                "REASONING_CHUNK dedicated message: " + e.getMessage());
    }

    @Test
    void validEventNamesStillRegister() {
        AgentModel model = new AgentModel();
        model.setHooks(List.of(hookModel("pre_call"), hookModel("after_reasoning")));

        DefaultHookRegistry registry = DefaultHookRegistry.fromAgentModel(model);
        // no exception; hooks registered (2 points)
        assertEquals(2, countRegisteredHooks(registry));
    }

    @Test
    void fromNameDelegatesToRegistryMap() {
        assertEquals(AgentLifecyclePoint.PRE_CALL, AgentLifecyclePoint.fromName("pre_call"));
        assertEquals(AgentLifecyclePoint.PRE_CALL, AgentLifecyclePoint.fromName("PRE_CALL"));
        assertNull(AgentLifecyclePoint.fromName("no_such_point"));
    }

    private int countRegisteredHooks(DefaultHookRegistry registry) {
        int total = 0;
        for (AgentLifecyclePoint point : AgentLifecyclePoint.values()) {
            total += registry.getHooks(point, "test-agent").size();
        }
        return total;
    }
}
