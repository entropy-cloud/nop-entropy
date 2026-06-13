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
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestToolCallRepairerInReActLoop {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private abstract static class StubToolManager implements IToolManager {
        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
        }

        @Override
        public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
            return null;
        }

        @Override
        public List<AiToolModel> listTools() {
            return Collections.emptyList();
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            AiToolModel model = new AiToolModel();
            model.setName(toolName);
            model.setDescription("Mock tool: " + toolName);
            return model;
        }
    }

    @Test
    void testRepairerIsCalledDuringReActLoop() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/etc/hosts"));

        AtomicInteger repairCount = new AtomicInteger(0);
        AtomicReference<ChatToolCall> capturedCall = new AtomicReference<>();

        IToolCallRepairer repairer = new IToolCallRepairer() {
            @Override
            public ChatToolCall repair(ChatToolCall tc, AgentExecutionContext ctx) {
                repairCount.incrementAndGet();
                capturedCall.set(tc);
                return tc;
            }
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
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

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "file content"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .toolCallRepairer(repairer)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, repairCount.get());
        assertNotNull(capturedCall.get());
        assertEquals("call_1", capturedCall.get().getId());
    }

    @Test
    void testRepairedCallIsUsedForDownstreamProcessing() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("calculator"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("calculator");
        Map<String, Object> originalArgs = new HashMap<>();
        originalArgs.put("expression", "2+2");
        toolCall.setArguments(originalArgs);

        IToolCallRepairer repairer = new IToolCallRepairer() {
            @Override
            public ChatToolCall repair(ChatToolCall tc, AgentExecutionContext ctx) {
                ChatToolCall repaired = tc.copy();
                Map<String, Object> repairedArgs = new HashMap<>(repaired.getArguments());
                repairedArgs.put("expression", "3+3");
                repaired.setArguments(repairedArgs);
                return repaired;
            }
        };

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("");
                    msg.setToolCalls(List.of(toolCall));
                    resp = ChatResponse.success(msg);
                } else {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("The result is 6.");
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

        AtomicReference<AiToolCall> capturedToolCall = new AtomicReference<>();
        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                capturedToolCall.set(call);
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "6"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .toolCallRepairer(repairer)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        assertNotNull(capturedToolCall.get());
        String inputText = capturedToolCall.get().getInput();
        assertTrue(inputText.contains("3+3"), "Repaired arguments should be used, got: " + inputText);
    }

    @Test
    void testDefaultNoOpRepairerPreservesExistingBehavior() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("echo"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hello"));

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCallCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("");
                    msg.setToolCalls(List.of(toolCall));
                    resp = ChatResponse.success(msg);
                } else {
                    ChatAssistantMessage msg = new ChatAssistantMessage();
                    msg.setContent("Echo done.");
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

        IToolManager toolManager = new StubToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "hello"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .build();

        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations());
    }
}
