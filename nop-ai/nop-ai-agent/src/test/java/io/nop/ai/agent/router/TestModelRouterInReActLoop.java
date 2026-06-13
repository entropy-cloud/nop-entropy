package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestModelRouterInReActLoop {

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
            return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "result"));
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
    void passThroughRouterIsCalledBeforeLlmCall() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicInteger routeCallCount = new AtomicInteger(0);
        AtomicReference<List<ChatMessage>> capturedMessages = new AtomicReference<>();
        AtomicReference<ChatOptions> capturedOptions = new AtomicReference<>();

        IModelRouter trackingRouter = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                routeCallCount.incrementAndGet();
                capturedMessages.set(new ArrayList<>(messages));
                capturedOptions.set(options);
                return new RoutingResult(options, null, "tracking");
            }
        };

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Hello.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(trackingRouter)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(1, routeCallCount.get(), "Model router should be called once for single-turn");
        assertNotNull(capturedMessages.get(), "Router should receive messages");
        assertNotNull(capturedOptions.get(), "Router should receive options");
    }

    @Test
    void passThroughRouterReceivesCorrectMessagesAndOptions() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        io.nop.ai.core.model.ChatOptionsModel chatOptionsModel = new io.nop.ai.core.model.ChatOptionsModel();
        chatOptionsModel.setModel("gpt-4o");
        chatOptionsModel.setProvider("openai");
        model.setChatOptions(chatOptionsModel);
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        AtomicReference<String> capturedModel = new AtomicReference<>();

        IModelRouter inspectingRouter = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                capturedModel.set(options.getModel());
                return new RoutingResult(options, null, "inspecting");
            }
        };

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Response.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(inspectingRouter)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals("gpt-4o", capturedModel.get(), "Router should see the model from ChatOptionsModel");
    }

    @Test
    void returnedChatOptionsAreUsedForActualCall() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.emptySet());
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatOptions overriddenOptions = new ChatOptions();
        overriddenOptions.setModel("claude-3");
        overriddenOptions.setProvider("anthropic");

        IModelRouter overridingRouter = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                return new RoutingResult(overriddenOptions, "rerouted", "override-test");
            }
        };

        AtomicReference<ChatOptions> optionsSentToLlm = new AtomicReference<>();

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                optionsSentToLlm.set(request.getOptions());
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("Response.");
                return CompletableFuture.completedFuture(ChatResponse.success(msg));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(overridingRouter)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertSame(overriddenOptions, optionsSentToLlm.get(),
                "ChatOptions returned by router should be used for chatService.call()");
        assertEquals("claude-3", optionsSentToLlm.get().getModel());
        assertEquals("anthropic", optionsSentToLlm.get().getProvider());
    }

    @Test
    void customRouterCanChangeModelProvider() {
        AgentModel model = new AgentModel();
        model.setTools(Collections.singleton("read_file"));
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        ctx.setMaxIterations(10);

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call_1");
        toolCall.setName("read_file");
        toolCall.setArguments(Map.of("path", "/tmp/test"));

        AtomicInteger routeCount = new AtomicInteger(0);

        IModelRouter router = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
                routeCount.incrementAndGet();
                ChatOptions modified = options.copy();
                modified.setModel("gpt-4-turbo");
                return new RoutingResult(modified, "complex", "complexity-based");
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
                    msg.setContent("Reading.");
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
                return subscriber -> {};
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
                .modelRouter(router)
                .build();

        executor.execute(ctx).toCompletableFuture().join();

        assertEquals(2, routeCount.get(), "Router should be called for each LLM call");
    }

    @Test
    void passThroughDefaultDoesNotInterfereWithExistingBehavior() {
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
                    msg.setContent("Echoing.");
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
                return subscriber -> {};
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
        assertEquals(2, chatCallCount.get());
    }
}
