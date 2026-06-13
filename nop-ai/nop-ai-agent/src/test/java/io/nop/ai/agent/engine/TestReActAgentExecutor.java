package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReActAgentExecutor {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentExecutionContext buildContext(int maxIterations) {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(maxIterations);
        return ctx;
    }

    private AgentExecutionContext buildContextWithTools(int maxIterations, Set<String> tools) {
        AgentModel model = new AgentModel();
        model.setTools(tools);
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(maxIterations);
        return ctx;
    }

    private ChatResponse buildSuccessResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private ChatResponse buildSuccessResponseWithToolCalls(List<ChatToolCall> toolCalls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(toolCalls);
        return ChatResponse.success(msg);
    }

    private ChatResponse buildErrorResponse(String errorMessage) {
        return ChatResponse.error("ERROR", errorMessage);
    }

    @Test
    void testNoToolCallImmediateReturn() {
        AgentExecutionContext ctx = buildContext(10);

        IChatService chatService = new StubChatService(buildSuccessResponse("Hello, I can help you."));
        IToolManager toolManager = new NoOpToolManager();

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(0, result.getTotalIterations());
        assertTrue(result.getMessages().stream()
                .anyMatch(m -> m instanceof ChatAssistantMessage));
        assertNull(result.getError());
    }

    @Test
    void testSingleToolCallLoop() {
        AgentExecutionContext ctx = buildContextWithTools(10, Collections.singleton("calculator"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_123");
        toolCall.setName("calculator");
        Map<String, Object> args = new HashMap<>();
        args.put("expression", "2+2");
        toolCall.setArguments(args);

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    resp = buildSuccessResponseWithToolCalls(List.of(toolCall));
                } else {
                    resp = buildSuccessResponse("The result is 4.");
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "4"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations());

        List<ChatMessage> messages = result.getMessages();
        boolean hasToolResponse = messages.stream()
                .anyMatch(m -> m instanceof ChatToolResponseMessage);
        assertTrue(hasToolResponse);

        assertEquals(2, callCount.get());
    }

    @Test
    void testMultipleToolCalls() {
        AgentExecutionContext ctx = buildContextWithTools(10, Set.of("tool_a", "tool_b"));

        ChatToolCall toolCallA = new ChatToolCall();
        toolCallA.setId("call_a");
        toolCallA.setName("tool_a");
        toolCallA.setArguments(Map.of("x", 1));

        ChatToolCall toolCallB = new ChatToolCall();
        toolCallB.setId("call_b");
        toolCallB.setName("tool_b");
        toolCallB.setArguments(Map.of("y", 2));

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    resp = buildSuccessResponseWithToolCalls(List.of(toolCallA, toolCallB));
                } else {
                    resp = buildSuccessResponse("Done.");
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if ("tool_a".equals(toolName)) {
                    return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result_a"));
                }
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(1, "result_b"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations());

        long toolResponseCount = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .count();
        assertEquals(2, toolResponseCount);
    }

    @Test
    void testMaxIterationsReached() {
        AgentExecutionContext ctx = buildContextWithTools(2, Collections.singleton("tool_a"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("tool_a");
        toolCall.setArguments(Map.of("x", 1));

        IChatService chatService = new StubChatService(
                buildSuccessResponseWithToolCalls(List.of(toolCall)));

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, result.getTotalIterations());
    }

    @Test
    void testLlmCallFailure() {
        AgentExecutionContext ctx = buildContext(10);

        IChatService chatService = new StubChatService(buildErrorResponse("rate_limit_exceeded"));
        IToolManager toolManager = new NoOpToolManager();

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertNotNull(result.getError());
        assertEquals("rate_limit_exceeded", result.getError());
    }

    @Test
    void testToolExecutionError() {
        AgentExecutionContext ctx = buildContextWithTools(10, Collections.singleton("failing_tool"));

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_err_1");
        toolCall.setName("failing_tool");
        toolCall.setArguments(Map.of("input", "test"));

        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                ChatResponse resp;
                if (n == 0) {
                    resp = buildSuccessResponseWithToolCalls(List.of(toolCall));
                } else {
                    resp = buildSuccessResponse("I see the tool failed.");
                }
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        IToolManager toolManager = new NoOpToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.errorResult(0, "tool crashed"));
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder().chatService(chatService).toolManager(toolManager).build();
        AgentExecutionResult result = executor.execute(ctx).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());

        List<ChatMessage> messages = result.getMessages();
        ChatToolResponseMessage toolResponse = messages.stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .findFirst()
                .orElse(null);
        assertNotNull(toolResponse);
        assertEquals("call_err_1", toolResponse.getToolCallId());
        assertTrue(toolResponse.getContent().contains("tool crashed"));

        assertEquals(2, callCount.get());
    }

    static class StubChatService implements IChatService {
        private final ChatResponse response;

        StubChatService(ChatResponse response) {
            this.response = response;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    static class NoOpToolManager implements IToolManager {
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
}
