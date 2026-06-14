package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestToolNameNormalizationStage {

    private static AgentExecutionContext ctxWithTools(Set<String> tools) {
        AgentModel model = new AgentModel();
        model.setTools(tools);
        return AgentExecutionContext.create(model, "test-session");
    }

    private static ChatToolCall callWithName(String name) {
        ChatToolCall call = new ChatToolCall();
        call.setId("call_1");
        call.setName(name);
        call.setArguments(Map.of("path", "/etc/hosts"));
        return call;
    }

    @Test
    void uniqueMatchWrongCase() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall input = callWithName("READ_FILE");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("read_file", result.getName());
        assertNotSame(input, result);
    }

    @Test
    void uniqueMatchMixedCase() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall input = callWithName("Read_File");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("read_file", result.getName());
    }

    @Test
    void uniqueMatchDashSeparator() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall input = callWithName("read-file");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("read_file", result.getName());
    }

    @Test
    void uniqueMatchDotSeparator() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall input = callWithName("read.file");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("read_file", result.getName());
    }

    @Test
    void noMatchPassThrough() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall input = callWithName("write_file");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("write_file", result.getName());
        assertSame(input, result);
    }

    @Test
    void ambiguousMatchPassThrough() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file", "read.file"));
        ChatToolCall input = callWithName("ReadFile");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("ReadFile", result.getName());
        assertSame(input, result);
    }

    @Test
    void emptyToolSetPassThrough() {
        AgentExecutionContext ctx = ctxWithTools(Set.of());
        ChatToolCall input = callWithName("ReadFile");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("ReadFile", result.getName());
        assertSame(input, result);
    }

    @Test
    void nullToolSetPassThrough() {
        AgentModel model = new AgentModel();
        model.setTools(null);
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ChatToolCall input = callWithName("ReadFile");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("ReadFile", result.getName());
        assertSame(input, result);
    }

    @Test
    void alreadyCanonicalPassThrough() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall input = callWithName("read_file");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("read_file", result.getName());
        assertSame(input, result);
    }

    @Test
    void preservesIdAndArguments() {
        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall input = callWithName("ReadFile");

        ChatToolCall result = new ToolNameNormalizationStage().repair(input, ctx);

        assertEquals("call_1", result.getId());
        assertEquals("/etc/hosts", result.getArguments().get("path"));
    }
}
