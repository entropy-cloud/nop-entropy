package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestArgumentCleanupStage {

    private static AgentExecutionContext ctx() {
        AgentModel model = new AgentModel();
        model.setTools(java.util.Collections.emptySet());
        return AgentExecutionContext.create(model, "test-session");
    }

    private static XNode parseSchema(String xml) {
        return XNodeParser.instance().parseFromText(SourceLocation.fromPath("[test]"), xml);
    }

    private static AiToolModel toolModelWithSchema(String name, String schemaXml) {
        AiToolModel model = new AiToolModel();
        model.setName(name);
        model.setSchema(parseSchema(schemaXml));
        return model;
    }

    private static IToolManager managerFor(AiToolModel... tools) {
        Map<String, AiToolModel> map = new LinkedHashMap<>();
        for (AiToolModel t : tools) {
            map.put(t.getName(), t);
        }
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                return map.get(toolName);
            }
        };
    }

    private static ChatToolCall callWithArgs(String toolName, Map<String, Object> args) {
        ChatToolCall call = new ChatToolCall();
        call.setId("call_1");
        call.setName(toolName);
        call.setArguments(args);
        return call;
    }

    @Test
    void nullValuedArgRemoved() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file path=\"!string\" fromLine=\"int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "/etc/hosts");
        args.put("fromLine", null);

        ChatToolCall result = new ArgumentCleanupStage(mgr).repair(callWithArgs("read_file", args), ctx());

        assertEquals("/etc/hosts", result.getArguments().get("path"));
        assertFalse(result.getArguments().containsKey("fromLine"));
    }

    @Test
    void schemaAbsentArgRemoved() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file path=\"!string\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "/etc/hosts");
        args.put("noise_arg", "garbage");

        ChatToolCall result = new ArgumentCleanupStage(mgr).repair(callWithArgs("read_file", args), ctx());

        assertEquals("/etc/hosts", result.getArguments().get("path"));
        assertFalse(result.getArguments().containsKey("noise_arg"));
    }

    @Test
    void missingRequiredArgLeftInPlace() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file path=\"!string\" fromLine=\"int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        // Only fromLine provided, missing required 'path'. Must pass through — not fabricated.
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("fromLine", 10);

        ChatToolCall result = new ArgumentCleanupStage(mgr).repair(callWithArgs("read_file", args), ctx());

        assertEquals(10, result.getArguments().get("fromLine"));
        assertFalse(result.getArguments().containsKey("path"));
    }

    @Test
    void schemaUnavailableOnlyNullRemoval() {
        IToolManager mgr = managerFor();

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "/etc/hosts");
        args.put("noise_arg", "garbage");
        args.put("null_arg", null);

        ChatToolCall result = new ArgumentCleanupStage(mgr).repair(callWithArgs("unknown_tool", args), ctx());

        // null_arg removed, but noise_arg stays (no schema to check against)
        assertEquals("/etc/hosts", result.getArguments().get("path"));
        assertEquals("garbage", result.getArguments().get("noise_arg"));
        assertFalse(result.getArguments().containsKey("null_arg"));
    }

    @Test
    void nullToolManagerOnlyNullRemoval() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "/etc/hosts");
        args.put("noise_arg", "garbage");
        args.put("null_arg", null);

        ChatToolCall result = new ArgumentCleanupStage(null).repair(callWithArgs("read_file", args), ctx());

        assertEquals("/etc/hosts", result.getArguments().get("path"));
        assertEquals("garbage", result.getArguments().get("noise_arg"));
        assertFalse(result.getArguments().containsKey("null_arg"));
    }

    @Test
    void nullArgumentsNormalizedToEmptyMap() {
        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("read_file");
        input.setArguments(null);

        ChatToolCall result = new ArgumentCleanupStage(null).repair(input, ctx());

        assertNotNull(result.getArguments());
        assertTrue(result.getArguments().isEmpty());
    }

    @Test
    void alreadyCleanPassThrough() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file path=\"!string\" fromLine=\"int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "/etc/hosts");
        args.put("fromLine", 10);

        ChatToolCall input = callWithArgs("read_file", args);
        ChatToolCall result = new ArgumentCleanupStage(mgr).repair(input, ctx());

        assertSame(input, result);
    }
}
