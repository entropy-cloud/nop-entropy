package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.middleware.IAgentMiddleware;
import io.nop.ai.agent.middleware.MiddlewareChain;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.nop.ai.agent.support.ChatResponseFixtures;

/**
 * W5-3 (BAIL): integration tests for the fourth HookResult state. Verifies
 * the five required paths per design §5.4 and Minimum Rules #25:
 * <ol>
 *   <li>POST_REASONING BAIL: middleware returns BailResult → that round's
 *       response is discarded (tool_calls NOT executed) + re-prompt</li>
 *   <li>POST_CALL BAIL: middleware returns BailResult → AgentExecutionResult
 *       reflects guardrail-blocked status (bailReason non-null)</li>
 *   <li>Non-POST point BAIL: fail-loud (NopAiAgentException)</li>
 *   <li>POST_REASONING bail cap exceeded: fail-loud</li>
 *   <li>Backward compatibility: no BailResult → behavior unchanged</li>
 * </ol>
 */
public class TestBailInReActLoop {

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
        return ChatResponseFixtures.assistantWithToolCalls("", toolCall);
    }

    private IToolManager simpleToolManager(AtomicInteger toolCallCount) {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                if (toolCallCount != null) {
                    toolCallCount.incrementAndGet();
                }
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

    private IChatService chatServiceForSequence(List<ChatResponse> responses) {
        AtomicInteger idx = new AtomicInteger(0);
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                int n = idx.getAndIncrement();
                ChatResponse resp = n < responses.size() ? responses.get(n) : successResponse("done");
                return CompletableFuture.completedFuture(resp);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    // ============================================================
    // 1. POST_REASONING BAIL: discard response + re-prompt
    // ============================================================

    @Test
    void postReasoningBailDiscardsToolCallResponseAndRePrompts() {
        // Middleware bails ONCE on the tool-call response, then passes on all
        // subsequent POST_REASONING fires. POST_REASONING fires once per
        // iteration, so a BAIL (which triggers re-prompt) causes the next
        // iteration to re-fire POST_REASONING.
        AtomicInteger bailReturnedCount = new AtomicInteger(0);
        AtomicInteger toolCallCount = new AtomicInteger(0);
        IAgentMiddleware bailOnceMiddleware = (ctx, next) -> {
            HookResult r = next.proceed(ctx);
            if (r.isPass() && bailReturnedCount.get() == 0) {
                bailReturnedCount.incrementAndGet();
                return new HookResult.BailResult("blocked-on-first");
            }
            return r;
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_REASONING, bailOnceMiddleware);

        // LLM sequence: tool-call response (bailed → re-prompt), same tool-call
        // response (passes → tool executed), then success (completion).
        IChatService chatService = chatServiceForSequence(List.of(
                toolCallResponse("c1", "test-tool", null),
                toolCallResponse("c2", "test-tool", null),
                successResponse("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(toolCallCount))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, bailReturnedCount.get(), "BAIL should have been returned exactly once");
        // The first tool-call response (c1) was BAILed → its tool_calls must
        // NOT have been executed. The second (c2) passes → its tool_call IS
        // executed. So exactly 1 tool call.
        assertEquals(1, toolCallCount.get(),
                "BAILed round's tool_calls must NOT execute; only the retried round's should");
    }

    @Test
    void postReasoningBailDoesNotTreatResponseAsFinalAnswer() {
        AtomicInteger bailReturnedCount = new AtomicInteger(0);
        // LLM returns a plain text "final-looking" answer; middleware bails
        // → it must NOT be treated as the final answer. Next round returns
        // a different answer that completes.
        IAgentMiddleware bailOnceMiddleware = (ctx, next) -> {
            HookResult r = next.proceed(ctx);
            if (r.isPass() && bailReturnedCount.get() == 0) {
                bailReturnedCount.incrementAndGet();
                return new HookResult.BailResult("looks-final-but-blocked");
            }
            return r;
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_REASONING, bailOnceMiddleware);

        IChatService chatService = chatServiceForSequence(List.of(
                successResponse("blocked-final"),
                successResponse("real-done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, bailReturnedCount.get());
        // POST_REASONING bail does NOT set bailReason (only POST_CALL does)
        assertNull(result.getBailReason(),
                "POST_REASONING bail must not set the final result's bailReason");
    }

    // ============================================================
    // 2. POST_CALL BAIL: result reflects guardrail-blocked status
    // ============================================================

    @Test
    void postCallBailMarksResultAsGuardrailBlocked() {
        IAgentMiddleware bailMiddleware = (ctx, next) -> {
            next.proceed(ctx);
            return new HookResult.BailResult("final-response-blocked");
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_CALL, bailMiddleware);

        IChatService chatService = chatServiceForSequence(List.of(successResponse("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        // Status is still completed (execution finished), but bailReason
        // marks the final response as guardrail-blocked.
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNotNull(result.getBailReason(), "POST_CALL BAIL must set bailReason");
        assertEquals("final-response-blocked", result.getBailReason());
    }

    @Test
    void postCallPassLeavesBailReasonNull() {
        IAgentMiddleware passMiddleware = (ctx, next) -> next.proceed(ctx);

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_CALL, passMiddleware);

        IChatService chatService = chatServiceForSequence(List.of(successResponse("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNull(result.getBailReason(), "POST_CALL Pass must leave bailReason null");
    }

    // ============================================================
    // 3. Non-POST point BAIL: fail-loud
    // ============================================================

    @Test
    void bailAtPreReasoningFailsLoud() {
        IAgentMiddleware bailMiddleware = (ctx, next) -> {
            next.proceed(ctx);
            return new HookResult.BailResult("invalid-point");
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.PRE_REASONING, bailMiddleware);

        IChatService chatService = chatServiceForSequence(List.of(successResponse("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        // Fail-loud surfaces as a failed execution (NopAiAgentException caught
        // by the executor's catch → status=failed).
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "BAIL at non-POST point must fail-loud, not silently pass");
    }

    @Test
    void bailAtNonPostPointThrowsViaHookInvoker() {
        // Direct unit-level test: AgentHookInvoker.validateBailPoint fires.
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.PRE_CALL, ctx -> new HookResult.BailResult("bad"));

        io.nop.ai.agent.engine.AgentHookInvoker invoker =
                new io.nop.ai.agent.engine.AgentHookInvoker(registry, null);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                invoker.invokeHooks(AgentLifecyclePoint.PRE_CALL, buildContext(), "agent", null, null));
        assertTrue(ex.getMessage().contains("POST"),
                "Error message should mention POST points: " + ex.getMessage());
    }

    // ============================================================
    // 4. POST_REASONING bail cap exceeded: fail-loud
    // ============================================================

    @Test
    void postReasoningBailCapExceededFailsLoud() {
        // Middleware bails on EVERY POST_REASONING — cap will be exceeded.
        IAgentMiddleware alwaysBailMiddleware = (ctx, next) -> {
            next.proceed(ctx);
            return new HookResult.BailResult("always-blocked");
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_REASONING, alwaysBailMiddleware);

        // maxIterations high enough so the bail cap (3) is hit first.
        IChatService chatService = chatServiceForSequence(Collections.nCopies(20, successResponse("x")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "Bail cap exceeded must fail-loud, not silently continue or infinite-loop");
        assertNotNull(result.getError());
        // The fail-loud NopAiAgentException is recorded in lastError.
        assertTrue(result.getError().contains("bail cap"),
                "Error should mention bail cap: " + result.getError());
    }

    // ============================================================
    // 5. Backward compatibility: no BailResult → behavior unchanged
    // ============================================================

    @Test
    void noBailMiddlewareBehavesIdenticallyToPreBail() {
        // No middleware at all — pure hook returning Pass.
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.POST_REASONING, ctx -> HookResult.PassResult.instance());
        registry.register(AgentLifecyclePoint.POST_CALL, ctx -> HookResult.PassResult.instance());

        IChatService chatService = chatServiceForSequence(List.of(successResponse("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertNull(result.getBailReason(),
                "No BAIL → bailReason must be null (backward compat)");
    }

    @Test
    void postReasoningVetoAtPostStillIgnoredForBailDimension() {
        // Per design §5.4 裁定 F: the executor only consumes isBail() at POST
        // points; isVeto()/isReenter() at POST points remain in their pre-W5-3
        // state (returned value previously discarded). A hook returning Veto
        // at POST_REASONING must not trigger the BAIL branch (isBail()=false).
        AtomicInteger postReasoningFires = new AtomicInteger(0);
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.register(AgentLifecyclePoint.POST_REASONING, ctx -> {
            postReasoningFires.incrementAndGet();
            // Return Veto at POST — historically the return value was
            // discarded. W5-3 only checks isBail(), so Veto at POST must
            // NOT be misinterpreted as BAIL (zero-regression for Veto).
            return new HookResult.VetoResult("post-veto");
        });

        IChatService chatService = chatServiceForSequence(List.of(successResponse("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        // POST_REASONING fired, but Veto at POST is NOT consumed as BAIL —
        // the execution completes normally (Veto's POST consumption is a
        // separate independent issue per Non-Goals).
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, postReasoningFires.get());
        assertNull(result.getBailReason(),
                "Veto at POST must not be consumed as BAIL (裁定 F zero-regression)");
        assertNotEquals("post-veto", result.getBailReason());
    }
}
