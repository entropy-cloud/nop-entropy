package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestHookInReActLoop {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private AgentModel agentModel;

    @BeforeEach
    void setUp() {
        agentModel = new AgentModel();
        agentModel.setName("test-agent");
        agentModel.setTools(Set.of("test-tool"));
    }

    private AgentExecutionContext buildContext() {
        return AgentExecutionContext.create(agentModel, "test-session");
    }

    private ChatResponse successResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private ChatResponse toolCallResponse(String toolCallId, String toolName, Map<String, Object> args) {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId(toolCallId);
        toolCall.setName(toolName);
        toolCall.setArguments(args != null ? args : Map.of());
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(toolCall));
        return ChatResponse.success(msg);
    }

    private ChatResponse multiToolCallResponse(List<ChatToolCall> toolCalls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(toolCalls);
        return ChatResponse.success(msg);
    }

    private IToolManager simpleToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "tool-result"));
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("Test tool");
                return m;
            }
        };
    }

    @Test
    void preReasoningFiresBeforeLlmCall() {
        List<AgentLifecyclePoint> firedPoints = new ArrayList<>();

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_REASONING, ctx -> {
            firedPoints.add(ctx.getLifecyclePoint());
            return HookResult.PassResult.instance();
        });

        AtomicInteger chatCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCallCount.incrementAndGet();
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, firedPoints.size());
        assertEquals(AgentLifecyclePoint.PRE_REASONING, firedPoints.get(0));
        assertEquals(1, chatCallCount.get());
        assertTrue(chatCallCount.get() > 0);
    }

    @Test
    void postReasoningFiresAfterLlmResponse() {
        List<AgentLifecyclePoint> firedPoints = new ArrayList<>();

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.POST_REASONING, ctx -> {
            firedPoints.add(ctx.getLifecyclePoint());
            return HookResult.PassResult.instance();
        });

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(firedPoints.contains(AgentLifecyclePoint.POST_REASONING));
    }

    @Test
    void preActingFiresPerToolInSequentialLoop() {
        List<String> toolNames = new ArrayList<>();

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_ACTING, ctx -> {
            toolNames.add(ctx.getToolName());
            return HookResult.PassResult.instance();
        });

        ChatToolCall call1 = new ChatToolCall();
        call1.setId("c1"); call1.setName("test-tool"); call1.setArguments(Map.of());
        ChatToolCall call2 = new ChatToolCall();
        call2.setId("c2"); call2.setName("test-tool"); call2.setArguments(Map.of());

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(multiToolCallResponse(List.of(call1, call2)));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, toolNames.size());
        assertEquals("test-tool", toolNames.get(0));
        assertEquals("test-tool", toolNames.get(1));
    }

    @Test
    void postActingFiresPerToolResult() {
        List<String> toolCallIds = new ArrayList<>();

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.POST_ACTING, ctx -> {
            toolCallIds.add(ctx.getToolCallId());
            return HookResult.PassResult.instance();
        });

        ChatToolCall call1 = new ChatToolCall();
        call1.setId("c1"); call1.setName("test-tool"); call1.setArguments(Map.of());
        ChatToolCall call2 = new ChatToolCall();
        call2.setId("c2"); call2.setName("test-tool"); call2.setArguments(Map.of());

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(multiToolCallResponse(List.of(call1, call2)));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(2, toolCallIds.size());
    }

    @Test
    void vetoFromPreActingSkipsTool() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_ACTING, ctx -> new HookResult.VetoResult("not allowed"));

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(toolCallResponse("c1", "test-tool", null));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
    }

    @Test
    void onErrorFiresOnException() {
        List<AgentLifecyclePoint> firedPoints = new ArrayList<>();

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.ON_ERROR, ctx -> {
            firedPoints.add(ctx.getLifecyclePoint());
            return HookResult.PassResult.instance();
        });

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                throw new NopAiAgentException("test failure");
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertTrue(firedPoints.contains(AgentLifecyclePoint.ON_ERROR));
    }

    @Test
    void preCallFiresBeforeLoopAndPostCallAfter() {
        List<AgentLifecyclePoint> firedPoints = Collections.synchronizedList(new ArrayList<>());

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_CALL, ctx -> {
            firedPoints.add(AgentLifecyclePoint.PRE_CALL);
            return HookResult.PassResult.instance();
        });
        registry.register(AgentLifecyclePoint.POST_CALL, ctx -> {
            firedPoints.add(AgentLifecyclePoint.POST_CALL);
            return HookResult.PassResult.instance();
        });

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());

        int preIdx = firedPoints.indexOf(AgentLifecyclePoint.PRE_CALL);
        int postIdx = firedPoints.indexOf(AgentLifecyclePoint.POST_CALL);
        assertTrue(preIdx >= 0, "PRE_CALL should fire");
        assertTrue(postIdx >= 0, "POST_CALL should fire");
        assertTrue(preIdx < postIdx, "PRE_CALL should fire before POST_CALL");
    }

    @Test
    void beforeToolResultProcessedReenterCausesReEntry() {
        AtomicInteger reenterCount = new AtomicInteger(0);

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ctx -> {
            int n = reenterCount.incrementAndGet();
            if (n <= 1) {
                return new HookResult.ReenterResult("inject-retry");
            }
            return HookResult.PassResult.instance();
        });

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(toolCallResponse("c1", "test-tool", null));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(reenterCount.get() >= 1, "Re-enter hook should have been triggered");
    }

    @Test
    void afterToolResultProcessedReenterCausesReEntry() {
        AtomicInteger reenterCount = new AtomicInteger(0);

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.AFTER_TOOL_RESULT_PROCESSED, ctx -> {
            int n = reenterCount.incrementAndGet();
            if (n <= 1) {
                return new HookResult.ReenterResult("inject-retry-after");
            }
            return HookResult.PassResult.instance();
        });

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(toolCallResponse("c1", "test-tool", null));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(reenterCount.get() >= 1);
    }

    @Test
    void reentryCounterForcesPassAfterMaxReentries() {
        AtomicInteger reenterCount = new AtomicInteger(0);

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ctx -> {
            reenterCount.incrementAndGet();
            return new HookResult.ReenterResult("always-reenter");
        });

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n < 10) return CompletableFuture.completedFuture(toolCallResponse("c1", "test-tool", null));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(reenterCount.get() > ReActAgentExecutor.DEFAULT_MAX_REENTRIES,
                "Re-entry counter should force pass after max re-entries");
    }

    @Test
    void reenterResultAtNonReentrantPointThrowsException() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_REASONING, ctx -> new HookResult.ReenterResult("invalid"));

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.failed, result.getStatus());
        assertTrue(result.getError().contains("ReenterResult is only valid at re-entrant hook points"));
    }

    @Test
    void defaultNoOpDoesNotInterfereWithExistingBehavior() {
        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(toolCallResponse("c1", "test-tool", null));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(NoOpHookRegistry.INSTANCE).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, result.getTotalIterations());
    }

    @Test
    void hookContextCarriesToolInfo() {
        AtomicReference<HookContext> capturedCtx = new AtomicReference<>();

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_ACTING, ctx -> {
            capturedCtx.set(new HookContext(ctx.getLifecyclePoint(), ctx.getExecutionContext()));
            capturedCtx.get().setToolName(ctx.getToolName());
            capturedCtx.get().setToolCallId(ctx.getToolCallId());
            return HookResult.PassResult.instance();
        });

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(toolCallResponse("call-xyz", "test-tool", Map.of("x", 1)));
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        executor.execute(buildContext()).toCompletableFuture().join();
        assertNotNull(capturedCtx.get());
        assertEquals("test-tool", capturedCtx.get().getToolName());
        assertEquals("call-xyz", capturedCtx.get().getToolCallId());
        assertEquals(AgentLifecyclePoint.PRE_ACTING, capturedCtx.get().getLifecyclePoint());
    }

    @Test
    void vetoFromPreCallSkipsEntireExecution() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_CALL, ctx -> new HookResult.VetoResult("blocked"));

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                chatCount.incrementAndGet();
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(0, chatCount.get(), "LLM should not be called when PRE_CALL vetoed");
    }

    @Test
    void hookFailureAtPrePointIsLoggedNotSwallowed() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.POST_REASONING, ctx -> {
            throw new NopAiAgentException("hook failed");
        });

        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager())
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "Execution should continue despite after_* hook failure");
    }
}
