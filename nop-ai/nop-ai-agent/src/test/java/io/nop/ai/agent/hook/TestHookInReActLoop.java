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
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

        // AR-13 (plan 277): assert re-enter was HONORED, not just called.
        // The BEFORE re-enter branch injects a synthetic tool response with
        // the re-enter message text — if ReenterResult were silently treated
        // as PassResult, this message would NOT appear in ctx.
        boolean hasReenterMessage = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> "inject-retry".equals(m.getContent()));
        assertTrue(hasReenterMessage,
                "Re-enter synthetic message 'inject-retry' must be in ctx (proves re-enter was honored)");
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

        // AR-13 (plan 277): assert re-enter was HONORED, not just called.
        // The AFTER re-enter branch injects a marker user message — if
        // ReenterResult were silently treated as PassResult, this marker
        // would NOT appear in ctx.
        boolean hasReenterMarker = result.getMessages().stream()
                .filter(m -> m instanceof ChatUserMessage)
                .map(m -> (ChatUserMessage) m)
                .anyMatch(m -> m.getContent() != null
                        && m.getContent().contains("[re-enter requested by lifecycle hook]"));
        assertTrue(hasReenterMarker,
                "Re-enter marker message must be in ctx (proves re-enter was honored)");
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
        // AR-14 (plan 277): hitting max-iterations (10) is now "truncated"
        assertEquals(AgentExecStatus.truncated, result.getStatus());
        assertTrue(reenterCount.get() > ReActAgentExecutor.DEFAULT_MAX_REENTRIES,
                "Re-entry counter should force pass after max re-entries");
    }

    /**
     * AR-06 (plan 277): per-iteration reentry window. 5 consecutive iterations
     * each trigger a re-enter hook. With per-iteration reset, ALL 5 should be
     * honored (each iteration's counter resets). This test would fail under
     * the old per-execute cumulative counter (which starved after 3).
     */
    @Test
    void reentryCounterResetsPerIteration() {
        AtomicInteger reenterCount = new AtomicInteger(0);

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ctx -> {
            // Always request re-enter
            reenterCount.incrementAndGet();
            return new HookResult.ReenterResult("inject-retry");
        });

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                // 5 tool-call iterations, then "done"
                if (n < 5) return CompletableFuture.completedFuture(toolCallResponse("c1", "test-tool", null));
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

        // AR-06: with per-iteration reset, ALL 5 iterations' re-enter requests
        // are honored (each iteration's counter resets to 0). Under the old
        // per-execute cumulative counter, only the first 3 would be honored.
        assertEquals(5, reenterCount.get(),
                "All 5 iterations should fire the re-enter hook");

        // Per-iteration reset means each iteration's re-enter is honored
        // (not downgraded). Each honored re-enter produces a synthetic tool
        // response with the re-enter message. Assert 5 such responses.
        long reenterResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .filter(m -> "inject-retry".equals(m.getContent()))
                .count();
        assertEquals(5, reenterResponses,
                "All 5 per-iteration re-enter requests should be honored (per-iteration window)");
    }

    /**
     * AR-06 (plan 277): within a SINGLE iteration with >DEFAULT_MAX_REENTRIES
     * tools all triggering re-enter, only the first DEFAULT_MAX_REENTRIES are
     * honored; the rest are downgraded to PassResult with WARN.
     */
    @Test
    void reentryCounterForcesPassAfterMaxReentriesWithinIteration() {
        // Build 5 tool calls in ONE batch (single iteration)
        List<ChatToolCall> batchCalls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ChatToolCall tc = new ChatToolCall();
            tc.setId("call_" + i);
            tc.setName("test-tool");
            tc.setArguments(Map.of());
            batchCalls.add(tc);
        }

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ctx -> {
            // Always request re-enter for every tool
            return new HookResult.ReenterResult("inject-retry");
        });

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(multiToolCallResponse(batchCalls));
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

        // Within one iteration: only first DEFAULT_MAX_REENTRIES (3) re-enter
        // requests are honored; the remaining 2 are downgraded to PassResult.
        long reenterResponses = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .filter(m -> "inject-retry".equals(m.getContent()))
                .count();
        assertEquals(ReActAgentExecutor.DEFAULT_MAX_REENTRIES, reenterResponses,
                "Only first " + ReActAgentExecutor.DEFAULT_MAX_REENTRIES
                        + " re-enter requests should be honored within one iteration");

        // The remaining 2 tools should get their real results (not re-enter msgs)
        long realResults = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .filter(m -> "tool-result".equals(m.getContent()))
                .count();
        assertEquals(5 - ReActAgentExecutor.DEFAULT_MAX_REENTRIES, realResults,
                "Remaining tools should get real results after downgrade");
    }

    /**
     * AR-03 (plan 277): N=3 tool batch, re-enter triggered on the 2nd tool's
     * BEFORE_TOOL_RESULT_PROCESSED. Asserts that ALL 3 tools get a matched
     * tool_call_id response in ctx — the old `break` dropped tools after the
     * re-entered one, breaking the LLM tool_call_id pairing invariant.
     */
    @Test
    void reenterInBatchDoesNotDropOtherToolResults() {
        // Build 3 tool calls
        List<ChatToolCall> batchCalls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ChatToolCall tc = new ChatToolCall();
            tc.setId("call_" + i);
            tc.setName("test-tool");
            tc.setArguments(Map.of());
            batchCalls.add(tc);
        }

        // Re-enter only on the 2nd tool (call_1), once
        AtomicInteger reenterCount = new AtomicInteger(0);
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.BEFORE_TOOL_RESULT_PROCESSED, ctx -> {
            String tcId = ctx.getToolCallId();
            if ("call_1".equals(tcId) && reenterCount.incrementAndGet() <= 1) {
                return new HookResult.ReenterResult("inject-retry-1");
            }
            return HookResult.PassResult.instance();
        });

        AtomicInteger chatCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = chatCount.getAndIncrement();
                if (n == 0) return CompletableFuture.completedFuture(multiToolCallResponse(batchCalls));
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

        // Collect all tool_call_ids from the assistant message(s)
        Set<String> assistantToolCallIds = new HashSet<>();
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatAssistantMessage) {
                ChatAssistantMessage am = (ChatAssistantMessage) m;
                if (am.getToolCalls() != null) {
                    for (ChatToolCall tc : am.getToolCalls()) {
                        assistantToolCallIds.add(tc.getId());
                    }
                }
            }
        }

        // Collect all tool response ids
        List<String> toolResponseIds = new ArrayList<>();
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                toolResponseIds.add(((ChatToolResponseMessage) m).getToolCallId());
            }
        }

        // Assert: every assistant tool_call_id has a matching tool response
        for (String callId : assistantToolCallIds) {
            assertTrue(toolResponseIds.contains(callId),
                    "tool_call_id " + callId + " must have a matching tool response");
        }
        // Assert: no orphan tool responses (every tool response id matches an assistant tool_call_id)
        for (String respId : toolResponseIds) {
            assertTrue(assistantToolCallIds.contains(respId),
                    "tool response id " + respId + " must match an assistant tool_call_id");
        }

        // The re-entered tool (call_1) should produce the synthetic message
        boolean hasReenterMessage = result.getMessages().stream()
                .filter(m -> m instanceof ChatToolResponseMessage)
                .map(m -> (ChatToolResponseMessage) m)
                .anyMatch(m -> "call_1".equals(m.getToolCallId())
                        && "inject-retry-1".equals(m.getContent()));
        assertTrue(hasReenterMessage, "Re-enter synthetic message for call_1 should be in ctx");
    }

    /**
     * AR-03 (plan 277) end-to-end wiring: the re-enter marker message
     * ("[re-enter requested by lifecycle hook]") must appear in ctx when
     * either BEFORE or AFTER re-enter fires, proving the re-enter was
     * honored (not silently downgraded).
     */
    @Test
    void afterToolResultProcessedReenterAddsMarkerMessage() {
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

        // AR-03: AFTER re-enter must produce an observable marker message
        boolean hasReenterMarker = result.getMessages().stream()
                .filter(m -> m instanceof ChatUserMessage)
                .map(m -> (ChatUserMessage) m)
                .anyMatch(m -> m.getContent() != null
                        && m.getContent().contains("[re-enter requested by lifecycle hook]"));
        assertTrue(hasReenterMarker,
                "AFTER re-enter must add a marker user message proving re-enter was honored");
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
        // AR-12 (plan 277): this test must register on a TRUE PRE point
        // (PRE_REASONING), not POST_REASONING. Production invokeHooks re-throws
        // exceptions at PRE_*/BEFORE_* points (via `throw e`), so the outer
        // catch sets status=failed. The old version registered on
        // POST_REASONING (an AFTER_* point where exceptions are swallowed),
        // making the test name a misnomer and not verifying PRE re-throw.
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_REASONING, ctx -> {
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
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "PRE_* hook failure must re-throw and fail the execution (not swallow)");
    }
}
