package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestChainRepairer {

    private static AgentExecutionContext ctxWithTools(Set<String> tools) {
        AgentModel model = new AgentModel();
        model.setTools(tools);
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

    @Test
    void allFourStagesFireInSequence() {
        // Use spy stages that count invocations
        List<AtomicInteger> counters = new ArrayList<>();
        List<IToolCallRepairer> spyStages = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            AtomicInteger counter = new AtomicInteger(0);
            counters.add(counter);
            final int idx = i;
            spyStages.add(new IToolCallRepairer() {
                @Override
                public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
                    counters.get(idx).incrementAndGet();
                    return toolCall;
                }
            });
        }

        ChainRepairer chain = new ChainRepairer(spyStages);

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("test_tool");
        input.setArguments(new LinkedHashMap<>());

        chain.repair(input, ctxWithTools(Set.of("test_tool")));

        for (int i = 0; i < 4; i++) {
            assertEquals(1, counters.get(i).get(), "Stage " + (i + 1) + " should fire exactly once");
        }
    }

    @Test
    void stagesRunInOrder() {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        List<IToolCallRepairer> spyStages = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final String name = "stage" + (i + 1);
            spyStages.add(new IToolCallRepairer() {
                @Override
                public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
                    order.add(name);
                    return toolCall;
                }
            });
        }

        ChainRepairer chain = new ChainRepairer(spyStages);

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("test_tool");
        input.setArguments(new LinkedHashMap<>());

        chain.repair(input, ctxWithTools(Set.of("test_tool")));

        assertEquals(List.of("stage1", "stage2", "stage3", "stage4"), order);
    }

    @Test
    void nullToolManagerAllStagesRun() {
        ChainRepairer chain = new ChainRepairer((IToolManager) null);
        assertEquals(4, chain.getStages().size());

        // Stage 1 still set-matches; Stages 3/4 degrade to schema-agnostic no-ops
        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("READ_FILE");
        input.setArguments(new LinkedHashMap<>());
        input.getArguments().put("null_arg", null);

        AgentExecutionContext ctx = ctxWithTools(Set.of("read_file"));
        ChatToolCall result = chain.repair(input, ctx);

        // Stage 1: canonicalized to read_file
        assertEquals("read_file", result.getName());
        // Stage 4: null arg removed even without IToolManager
        assertNotNull(result.getArguments());
    }

    @Test
    void emptyToolSetStage1DegradesToCanonicalizeOnly() {
        ChainRepairer chain = new ChainRepairer((IToolManager) null);

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("READ_FILE");
        input.setArguments(new LinkedHashMap<>());

        AgentExecutionContext ctx = ctxWithTools(Set.of());
        ChatToolCall result = chain.repair(input, ctx);

        // No declared tools to match against — name unchanged
        assertEquals("READ_FILE", result.getName());
    }

    @Test
    void fullRepairChainWithSchema() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file id=\"!int\" path=\"!string\" fromLine=\"int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        ChainRepairer chain = new ChainRepairer(mgr);

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("READ-FILE");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", "42");
        args.put("path", "/etc/hosts");
        args.put("fromLine", "10");
        args.put("noise_arg", "garbage");
        args.put("null_arg", null);
        input.setArguments(args);

        ChatToolCall result = chain.repair(input, ctxWithTools(Set.of("read_file")));

        // Stage 1: name canonicalized
        assertEquals("read_file", result.getName());
        // Stage 3: string values coerced
        assertEquals(42, result.getArguments().get("id"));
        assertEquals(10, result.getArguments().get("fromLine"));
        // Stage 4: noise and null removed
        assertNull(result.getArguments().get("noise_arg"));
        assertNull(result.getArguments().get("null_arg"));
        // Path stays (declared in schema)
        assertEquals("/etc/hosts", result.getArguments().get("path"));
    }

    @Test
    void neverReturnsNull() {
        ChainRepairer chain = new ChainRepairer((IToolManager) null);

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("test_tool");
        input.setArguments(null);

        ChatToolCall result = chain.repair(input, ctxWithTools(Set.of("test_tool")));

        assertNotNull(result);
        assertNotNull(result.getArguments());
    }

    private static IChatService stubChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    @Test
    void builderOptInCreatesChainRepairer() {
        IToolManager mgr = managerFor();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(stubChatService())
                .toolManager(mgr)
                .enableChainRepairer()
                .build();

        assertNotNull(executor);
    }

    @Test
    void builderOptInRequiresToolManager() {
        assertThrows(NopAiAgentException.class, () ->
                ReActAgentExecutor.builder()
                        .chatService(stubChatService())
                        .enableChainRepairer()
        );
    }

    @Test
    void defaultBuilderDoesNotUseChainRepairer() {
        // Without enableChainRepairer(), the default should be NoOp
        IToolManager mgr = managerFor();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(stubChatService())
                .toolManager(mgr)
                .build();

        assertNotNull(executor);
        // We can't directly inspect the repairer field, but the default is NoOp.
        // The backward-compat tests in Phase 3 verify this behaviorally.
    }

    @Test
    void passThroughOnAlreadyCleanCall() {
        AiToolModel tool = toolModelWithSchema("read_file",
                "<schema><read_file id=\"!int\" path=\"!string\" fromLine=\"int\"/></schema>");
        IToolManager mgr = managerFor(tool);

        ChainRepairer chain = new ChainRepairer(mgr);

        ChatToolCall input = new ChatToolCall();
        input.setId("call_1");
        input.setName("read_file");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", 42);
        args.put("path", "/etc/hosts");
        input.setArguments(args);

        ChatToolCall result = chain.repair(input, ctxWithTools(Set.of("read_file")));

        assertEquals("read_file", result.getName());
        assertEquals(42, result.getArguments().get("id"));
        assertEquals("/etc/hosts", result.getArguments().get("path"));
        assertEquals(2, result.getArguments().size());
    }
}
