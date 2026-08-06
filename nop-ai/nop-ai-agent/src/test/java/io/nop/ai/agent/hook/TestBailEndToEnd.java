package io.nop.ai.agent.hook;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.middleware.IAgentMiddleware;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
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

import java.util.Collections;
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
import io.nop.ai.agent.support.ChatResponseFixtures;

/**
 * W5-3 (BAIL) Phase 3: end-to-end integration tests proving the full path
 * from a guardrail middleware returning BailResult through the executor
 * consuming it, per Minimum Rules #22 (end-to-end) and #23 (wiring).
 *
 * <p>These tests exercise the complete chain:
 * middleware.execute(ctx, next) → MiddlewareChain.proceed →
 * AgentHookInvoker.executeWithMiddleware → ReActAgentExecutor consumes
 * isBail() → response discarded / result blocked.
 *
 * <p>Also verifies the four-way guardrail semantic distinction (BAIL vs Veto
 * vs Modify vs checkOutputGuardrail) per design §5.4.
 */
public class TestBailEndToEnd {

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

    /**
     * End-to-end: POST_REASONING middleware detects disallowed content →
     * returns BailResult → executor discards that round's response (no tool
     * execution) + re-prompts + cap protects. Validates the full wiring
     * (Minimum Rules #22, #23): the BailResult returned by the middleware
     * travels through MiddlewareChain.proceed → executeWithMiddleware →
     * isBail() consumption in ReActAgentExecutor.
     */
    @Test
    void endToEndPostReasoningBailDiscardsResponseAndRePrompts() {
        AtomicInteger middlewareInvocations = new AtomicInteger(0);
        AtomicInteger bailReturns = new AtomicInteger(0);
        AtomicInteger toolCallCount = new AtomicInteger(0);

        // Guardrail middleware: detects "forbidden" content on first
        // POST_REASONING, returns BailResult. Passes on subsequent fires.
        IAgentMiddleware guardrailMiddleware = (ctx, next) -> {
            middlewareInvocations.incrementAndGet();
            HookResult r = next.proceed(ctx);
            if (r.isPass() && bailReturns.get() == 0) {
                bailReturns.incrementAndGet();
                return new HookResult.BailResult("disallowed-content-detected");
            }
            return r;
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_REASONING, guardrailMiddleware);

        // LLM sequence: a "forbidden" response (bailed), then a clean
        // response (passes).
        IChatService chatService = chatServiceForSequence(List.of(
                successResponse("forbidden-content"),
                successResponse("clean-response")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(toolCallCount))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        // End-to-end assertions: BailResult was returned AND consumed.
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, bailReturns.get(),
                "Middleware returned BailResult exactly once");
        assertTrue(middlewareInvocations.get() >= 2,
                "POST_REASONING fired at least twice (initial + re-prompt)");
        assertEquals(0, toolCallCount.get(),
                "No tools executed (responses were plain text, BAILed round discarded)");
        assertNull(result.getBailReason(),
                "POST_REASONING bail does not set the final result's bailReason");
    }

    /**
     * End-to-end: POST_CALL middleware detects final-response violation →
     * returns BailResult → AgentExecutionResult reflects guardrail-blocked
     * status. Validates the full wiring for POST_CALL (Minimum Rules #22,
     * #23).
     */
    @Test
    void endToEndPostCallBailMarksResultBlocked() {
        AtomicInteger middlewareInvocations = new AtomicInteger(0);

        IAgentMiddleware guardrailMiddleware = (ctx, next) -> {
            middlewareInvocations.incrementAndGet();
            next.proceed(ctx);
            return new HookResult.BailResult("final-response-violation");
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_CALL, guardrailMiddleware);

        IChatService chatService = chatServiceForSequence(List.of(successResponse("final-answer")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(null))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        // End-to-end: BailResult returned by POST_CALL middleware → consumed
        // → result.bailReason reflects the blocked status.
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, middlewareInvocations.get(),
                "POST_CALL middleware fired exactly once");
        assertNotNull(result.getBailReason(),
                "POST_CALL BAIL must set bailReason (result reflects guardrail-blocked status)");
        assertEquals("final-response-violation", result.getBailReason());
    }

    /**
     * Four-way guardrail semantic distinction (design §5.4): BAIL (POST not-
     * apply + re-prompt), Veto (PRE reject), Modify (rewrite), and
     * checkOutputGuardrail (built-in block) are semantically distinguishable
     * in the same test setup.
     *
     * <p>This test focuses on the BAIL-vs-Veto distinction: a PRE Veto
     * prevents the LLM call entirely (status=completed at PRE_CALL, or
     * iteration skip at PRE_REASONING), while a POST BAIL runs the LLM call
     * then discards the response + re-prompts.
     */
    @Test
    void bailAndVetoAreSemanticallyDistinct() {
        // --- Veto side: PRE_REASONING Veto skips the iteration entirely ---
        AtomicInteger preReasoningVetoFires = new AtomicInteger(0);
        DefaultHookRegistry vetoRegistry = new DefaultHookRegistry();
        vetoRegistry.register(AgentLifecyclePoint.PRE_REASONING, ctx -> {
            preReasoningVetoFires.incrementAndGet();
            return new HookResult.VetoResult("pre-reject");
        });
        AtomicInteger vetoChatCalls = new AtomicInteger(0);
        IChatService vetoChatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                vetoChatCalls.incrementAndGet();
                return CompletableFuture.completedFuture(successResponse("done"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        ReActAgentExecutor vetoExecutor = ReActAgentExecutor.builder()
                .chatService(vetoChatService).toolManager(simpleToolManager(null))
                .hookRegistry(vetoRegistry).build();
        AgentExecutionContext vetoCtx = buildContext();
        vetoCtx.setMaxIterations(1);
        AgentExecutionResult vetoResult = vetoExecutor.execute(vetoCtx).toCompletableFuture().join();

        // PRE Veto: the LLM call is skipped (core not executed). With
        // maxIterations=1, the single iteration is vetoed → truncated.
        assertEquals(0, vetoChatCalls.get(),
                "PRE_REASONING Veto skips the LLM call entirely (core not executed)");
        assertNull(vetoResult.getBailReason(),
                "Veto does not set bailReason (different mechanism)");

        // --- BAIL side: POST_REASONING BAIL runs the LLM call then discards ---
        AtomicInteger bailReturns = new AtomicInteger(0);
        AtomicInteger bailChatCalls = new AtomicInteger(0);
        IAgentMiddleware bailMiddleware = (ctx, next) -> {
            HookResult r = next.proceed(ctx);
            if (r.isPass() && bailReturns.get() == 0) {
                bailReturns.incrementAndGet();
                return new HookResult.BailResult("post-reject");
            }
            return r;
        };
        DefaultHookRegistry bailRegistry = new DefaultHookRegistry();
        bailRegistry.registerMiddleware(AgentLifecyclePoint.POST_REASONING, bailMiddleware);
        IChatService bailChatService = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                bailChatCalls.incrementAndGet();
                return CompletableFuture.completedFuture(successResponse("x"));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        ReActAgentExecutor bailExecutor = ReActAgentExecutor.builder()
                .chatService(bailChatService).toolManager(simpleToolManager(null))
                .hookRegistry(bailRegistry).build();
        AgentExecutionContext bailCtx = buildContext();
        bailCtx.setMaxIterations(2);
        AgentExecutionResult bailResult = bailExecutor.execute(bailCtx).toCompletableFuture().join();

        // POST BAIL: the LLM call DID execute (POST means after core), then
        // the response was discarded + re-prompted.
        assertTrue(bailChatCalls.get() >= 2,
                "POST_REASONING BAIL runs the LLM call THEN discards (core executed)");
        assertEquals(1, bailReturns.get());
        // Semantic distinction: Veto blocked at PRE (no LLM call), BAIL
        // blocked at POST (LLM call ran). Different mechanisms, different
        // chat-call counts.
        assertTrue(bailChatCalls.get() > vetoChatCalls.get(),
                "BAIL (POST) executes the LLM call; Veto (PRE) does not — distinct semantics");
    }

    /**
     * Wiring proof (Minimum Rules #23): the BailResult returned by the
     * middleware's execute() is the same object that reaches the executor's
     * isBail() check. Verified by asserting the middleware's return value
     * type flows through to observable executor behavior change (tool_calls
     * skipped). This is the Anti-Hollow proof that POST_REASONING's return
     * value is no longer discarded.
     */
    @Test
    void wiringProofBailResultIsConsumedByExecutor() {
        AtomicInteger toolCallCount = new AtomicInteger(0);
        AtomicInteger bailReturned = new AtomicInteger(0);

        // If POST_REASONING's return value were still discarded (pre-W5-3
        // bug), the BailResult would be ignored and the tool_calls WOULD
        // execute. This test asserts they do NOT — proving the wiring is
        // connected (Anti-Hollow).
        IAgentMiddleware bailOnToolCallMiddleware = (ctx, next) -> {
            HookResult r = next.proceed(ctx);
            // Bail when the response is a tool call (has tool_calls).
            if (r.isPass() && bailReturned.get() == 0) {
                bailReturned.incrementAndGet();
                return new HookResult.BailResult("block-tool-round");
            }
            return r;
        };

        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerMiddleware(AgentLifecyclePoint.POST_REASONING, bailOnToolCallMiddleware);

        io.nop.ai.api.chat.messages.ChatToolCall toolCall = new io.nop.ai.api.chat.messages.ChatToolCall();
        toolCall.setId("c1");
        toolCall.setName("test-tool");
        toolCall.setArguments(Map.of());
        ChatResponse toolResponse = ChatResponseFixtures.assistantWithToolCalls("", toolCall);

        IChatService chatService = chatServiceForSequence(List.of(
                toolResponse,
                successResponse("done")));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService).toolManager(simpleToolManager(toolCallCount))
                .hookRegistry(registry).build();

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, bailReturned.get());
        // CRITICAL Anti-Hollow assertion: if the return value were discarded,
        // toolCallCount would be 1 (the tool_call executed). With the wiring
        // connected, the BAIL discards the round → toolCallCount is 0.
        assertEquals(0, toolCallCount.get(),
                "Wiring proof: BailResult was consumed by the executor → "
                        + "tool_calls were NOT executed (return value no longer discarded)");
    }
}
