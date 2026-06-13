package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpToolCallRepairer {

    @Test
    void testReturnsInputUnchanged() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("test_tool");
        toolCall.setArguments(Map.of("key", "value"));

        ChatToolCall result = NoOpToolCallRepairer.INSTANCE.repair(toolCall, ctx);
        assertSame(toolCall, result);
    }

    @Test
    void testReturnsInputUnchangedWithNullArguments() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_2");
        toolCall.setName("test_tool");

        ChatToolCall result = NoOpToolCallRepairer.INSTANCE.repair(toolCall, ctx);
        assertSame(toolCall, result);
    }

    @Test
    void testSingletonIdentity() {
        NoOpToolCallRepairer a = NoOpToolCallRepairer.INSTANCE;
        NoOpToolCallRepairer b = NoOpToolCallRepairer.INSTANCE;
        assertSame(a, b);
    }

    @Test
    void testImplementsInterface() {
        assertTrue(IToolCallRepairer.class.isAssignableFrom(NoOpToolCallRepairer.class));
    }
}
