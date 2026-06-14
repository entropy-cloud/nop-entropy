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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestArgumentValueCoercionStage {

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
    void stringToIntCoercion() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file id=\"!int\" path=\"!string\" fromLine=\"int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", "42");
        args.put("fromLine", "10");

        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(callWithArgs("read_file", args), ctx());

        assertEquals(42, result.getArguments().get("id"));
        assertEquals(10, result.getArguments().get("fromLine"));
    }

    @Test
    void stringToBooleanCoercion() {
        AiToolModel tool = toolModelWithSchema("grep",
                "<schema><grep recursive=\"boolean\" ignoreCase=\"boolean\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("recursive", "true");
        args.put("ignoreCase", "false");

        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(callWithArgs("grep", args), ctx());

        assertEquals(Boolean.TRUE, result.getArguments().get("recursive"));
        assertEquals(Boolean.FALSE, result.getArguments().get("ignoreCase"));
    }

    @Test
    void stringToNumberCoercion() {
        AiToolModel tool = toolModelWithSchema("calc",
                "<schema><calc ratio=\"number\" threshold=\"double\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("ratio", "3.14");
        args.put("threshold", "0.5");

        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(callWithArgs("calc", args), ctx());

        assertEquals(3.14, result.getArguments().get("ratio"));
        assertEquals(0.5, result.getArguments().get("threshold"));
    }

    @Test
    void nonPrimitiveTypeNotCoerced() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file path=\"!full-path\" status=\"!enum:pending|done\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "/etc/hosts");
        args.put("status", "pending");

        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(callWithArgs("read_file", args), ctx());

        // full-path and enum:* are non-primitive — left as-is
        assertEquals("/etc/hosts", result.getArguments().get("path"));
        assertEquals("pending", result.getArguments().get("status"));
    }

    @Test
    void ambiguousValueNotCoerced() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file id=\"!int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", "abc");

        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(callWithArgs("read_file", args), ctx());

        assertEquals("abc", result.getArguments().get("id"));
    }

    @Test
    void schemaUnavailableNoCoercion() {
        IToolManager mgr = managerFor();

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", "42");

        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(callWithArgs("unknown_tool", args), ctx());

        assertEquals("42", result.getArguments().get("id"));
    }

    @Test
    void nullToolManagerNoCoercion() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", "42");

        ChatToolCall input = callWithArgs("read_file", args);
        ChatToolCall result = new ArgumentValueCoercionStage(null).repair(input, ctx());

        assertSame(input, result);
    }

    @Test
    void alreadyTypedValueNotChanged() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file id=\"!int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", 42);

        ChatToolCall input = callWithArgs("read_file", args);
        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(input, ctx());

        assertSame(input, result);
        assertEquals(42, result.getArguments().get("id"));
    }

    @Test
    void nullArgumentsNotChanged() {
        IToolManager mgr = managerFor();

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("read_file");
        input.setArguments(null);

        ChatToolCall result = new ArgumentValueCoercionStage(mgr).repair(input, ctx());

        assertNull(result.getArguments());
    }
}
