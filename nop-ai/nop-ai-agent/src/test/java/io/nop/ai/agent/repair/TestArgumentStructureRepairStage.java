package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.api.core.json.JSON;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestArgumentStructureRepairStage {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static AgentExecutionContext ctx() {
        AgentModel model = new AgentModel();
        model.setTools(java.util.Collections.emptySet());
        return AgentExecutionContext.create(model, "test-session");
    }

    @Test
    void nullArgumentsNormalizedToEmptyMap() {
        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("test_tool");
        input.setArguments(null);

        ChatToolCall result = new ArgumentStructureRepairStage().repair(input, ctx());

        assertNotNull(result.getArguments());
        assertTrue(result.getArguments().isEmpty());
        assertEquals("call_1", result.getId());
        assertEquals("test_tool", result.getName());
    }

    @Test
    void jsonStringParsedToMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", "/etc/hosts");
        data.put("line", 42);
        String json = JSON.stringify(data);

        Map<String, Object> result = ArgumentStructureRepairStage.normalizeArguments(json);

        assertEquals("/etc/hosts", result.get("path"));
        assertEquals(42, result.get("line"));
    }

    @Test
    void oneElementArrayUnwrapped() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("path", "/etc/hosts");

        Map<String, Object> result = ArgumentStructureRepairStage.normalizeArguments(List.of(inner));

        assertEquals("/etc/hosts", result.get("path"));
    }

    @Test
    void alreadyMapPassThrough() {
        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("test_tool");
        input.setArguments(Map.of("path", "/etc/hosts"));

        ChatToolCall result = new ArgumentStructureRepairStage().repair(input, ctx());

        assertEquals("/etc/hosts", result.getArguments().get("path"));
        assertSame(input, result);
    }

    @Test
    void singleWrapperKeyNotUnwrapped() {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("config", Map.of("path", "/etc/hosts"));

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("test_tool");
        input.setArguments(wrapper);

        ChatToolCall result = new ArgumentStructureRepairStage().repair(input, ctx());

        assertEquals(wrapper, result.getArguments());
    }

    @Test
    void malformedJsonStringProducesEmptyMap() {
        Map<String, Object> result = ArgumentStructureRepairStage.normalizeArguments("not valid json {{{");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void multiElementArrayProducesEmptyMap() {
        Map<String, Object> result = ArgumentStructureRepairStage.normalizeArguments(
                List.of(Map.of("a", 1), Map.of("b", 2)));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void nullProducesEmptyMap() {
        Map<String, Object> result = ArgumentStructureRepairStage.normalizeArguments(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
