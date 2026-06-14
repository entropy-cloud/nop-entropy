package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test proving a malformed LLM tool call is repaired by
 * the ChainRepairer and flows through the live ReAct loop to the tool manager.
 * Also includes backward-compat (NoOp default) and pass-through tests.
 */
public class TestChainRepairerInReActLoop {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String READ_FILE_SCHEMA = "<schema>"
            + "<read_file id=\"!int\" explanation=\"!string\" path=\"!full-path\" fromLine=\"int\"/>"
            + "</schema>";

    private abstract static class StubToolManager implements IToolManager {
        private final AiToolModel toolModel;

        StubToolManager(String toolName, String schemaXml) {
            this.toolModel = new AiToolModel();
            toolModel.setName(toolName);
            XNode schema = XNodeParser.instance().parseFromText(SourceLocation.fromPath("[test]"), schemaXml);
            toolModel.setSchema(schema);
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
            return toolModel;
        }
    }

    private static IChatService chatServiceEmittingToolCall(ChatToolCall toolCall) {
        AtomicInteger callCount = new AtomicInteger(0);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("");
                    msg.setToolCalls(List.of(toolCall));
                    resp = ChatResponse.success(msg);
                } else {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Done.");
                    resp = ChatResponse.success(msg);
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    /**
     * End-to-end test: LLM emits a malformed tool call (wrong-case name +
     * string-encoded int arg + null noise arg), ChainRepairer repairs it, and
     * the tool manager receives the clean call.
     */
    @Test
    void testMalformedCallRepairedEndToEnd() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        // Malformed tool call: wrong-case name, string-encoded args, null noise
        ChatToolCall malformed = new ChatToolCall();
        malformed.setId("call_1");
        malformed.setName("READ-FILE");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", "1");
        args.put("explanation", "read hosts");
        args.put("path", "/etc/hosts");
        args.put("fromLine", "10");
        args.put("noise_arg", "garbage");
        args.put("null_arg", null);
        malformed.setArguments(args);

        AtomicReference<AiToolCall> capturedCall = new AtomicReference<>();

        IToolManager toolManager = new StubToolManager("read_file", READ_FILE_SCHEMA) {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                capturedCall.set(call);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceEmittingToolCall(malformed))
                .toolManager(toolManager)
                .enableChainRepairer()
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        // Verify the tool manager received the repaired call
        assertNotNull(capturedCall.get());
        String inputText = capturedCall.get().getInput();
        assertNotNull(inputText);

        // Stage 1: name canonicalized to read_file
        // (toolName is read_file because the tool manager's callTool receives the repaired name)
        // Stage 3: id and fromLine coerced to int
        // Stage 4: noise_arg and null_arg removed
        // The tool manager input is a JSON string built from the repaired arguments
        assertTrue(inputText.contains("/etc/hosts"), "path should be present");
        assertFalse(inputText.contains("noise_arg"), "noise_arg should have been removed");
        assertFalse(inputText.contains("null_arg"), "null_arg should have been removed");
        // id should be coerced to integer (no quotes around the number)
        assertTrue(inputText.contains("\"id\":1"), "id should be coerced to int, got: " + inputText);
        assertTrue(inputText.contains("\"fromLine\":10"), "fromLine should be coerced to int, got: " + inputText);
    }

    /**
     * Backward-compat: with ChainRepairer NOT opted in (NoOp default), a
     * well-formed tool call passes through identically.
     */
    @Test
    void testNoOpDefaultPreservesBehavior() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall wellFormed = new ChatToolCall();
        wellFormed.setId("call_1");
        wellFormed.setName("read_file");
        wellFormed.setArguments(Map.of("path", "/etc/hosts"));

        AtomicReference<AiToolCall> capturedCall = new AtomicReference<>();

        IToolManager toolManager = new StubToolManager("read_file", READ_FILE_SCHEMA) {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                capturedCall.set(call);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        // No enableChainRepairer() — default NoOp
        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceEmittingToolCall(wellFormed))
                .toolManager(toolManager)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNotNull(capturedCall.get());
        // Well-formed call passes through unchanged
        assertTrue(capturedCall.get().getInput().contains("/etc/hosts"));
    }

    /**
     * Pass-through test: with ChainRepairer opted in but the call already
     * well-formed, the output equals the input (no spurious mutation).
     */
    @Test
    void testChainRepairerPassThroughOnCleanCall() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall wellFormed = new ChatToolCall();
        wellFormed.setId("call_1");
        wellFormed.setName("read_file");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", 1);
        args.put("explanation", "read hosts");
        args.put("path", "/etc/hosts");
        args.put("fromLine", 10);
        wellFormed.setArguments(args);

        AtomicReference<AiToolCall> capturedCall = new AtomicReference<>();

        IToolManager toolManager = new StubToolManager("read_file", READ_FILE_SCHEMA) {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                capturedCall.set(call);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceEmittingToolCall(wellFormed))
                .toolManager(toolManager)
                .enableChainRepairer()
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNotNull(capturedCall.get());

        String inputText = capturedCall.get().getInput();
        // All args should be present, no mutation
        assertTrue(inputText.contains("\"id\":1"));
        assertTrue(inputText.contains("\"fromLine\":10"));
        assertTrue(inputText.contains("/etc/hosts"));
        assertTrue(inputText.contains("read hosts"));
    }

    /**
     * Full path verification: the repaired call's name flows through to the
     * tool manager (the tool manager's callTool receives the canonical name,
     * not the malformed name).
     */
    @Test
    void testRepairedNameFlowsToToolManager() {
        AgentModel model = new AgentModel();
        model.setTools(Set.of("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall malformed = new ChatToolCall();
        malformed.setId("call_1");
        malformed.setName("read.file");
        malformed.setArguments(new LinkedHashMap<>());
        malformed.getArguments().put("path", "/etc/hosts");

        AtomicReference<String> capturedToolName = new AtomicReference<>();

        IToolManager toolManager = new StubToolManager("read_file", READ_FILE_SCHEMA) {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                capturedToolName.set(toolName);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatServiceEmittingToolCall(malformed))
                .toolManager(toolManager)
                .enableChainRepairer()
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        // The tool name passed to callTool should be the repaired canonical name
        assertEquals("read_file", capturedToolName.get());
    }
}
