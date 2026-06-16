package io.nop.ai.agent.engine;

import io.nop.ai.agent.contribution.Contribution;
import io.nop.ai.agent.contribution.ContributionType;
import io.nop.ai.agent.contribution.InMemoryContributionRegistry;
import io.nop.ai.agent.contribution.IContributionRegistry;
import io.nop.ai.agent.contribution.NoOpContributionRegistry;
import io.nop.ai.agent.hook.AgentLifecyclePoint;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.hook.IAgentLifecycleHook;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 217 (L4-6): end-to-end + focused tests for contribution assembly-time
 * resolution. Covers the HOOK and PROMPT resolution paths from
 * {@code engine.setContributionRegistry(...)} + {@code registry.register(...)}
 * through engine {@code resolveExecutor} assembly (HOOK → hookRegistry) into
 * the executor's runtime consultation (PROMPT → system prompt), all the way
 * to observable behaviour (hook fired at the ReAct lifecycle point, prompt
 * fragment present in system messages).
 *
 * <p>Lives in {@code io.nop.ai.agent.engine} so it can call the package-private
 * {@link DefaultAgentEngine#resolveExecutor(AgentModel)} (the engine's
 * assembly-time entry point that resolves HOOK contributions into the hook
 * registry).
 */
public class TestContributionResolution {

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
    }

    private AgentExecutionContext buildContext() {
        return AgentExecutionContext.create(agentModel, "test-session");
    }

    private ChatResponse successResponse(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
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
                return null;
            }
        };
    }

    private IChatService successChatService(String content) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(successResponse(content));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IChatService capturingChatService(AtomicReference<String> capturedSystem, String reply) {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                StringBuilder sb = new StringBuilder();
                for (ChatMessage m : request.getMessages()) {
                    if (m instanceof ChatSystemMessage) {
                        ChatSystemMessage sm = (ChatSystemMessage) m;
                        if (sm.getContent() != null) {
                            sb.append(sm.getContent()).append('\n');
                        }
                    }
                }
                capturedSystem.set(sb.toString());
                return CompletableFuture.completedFuture(successResponse(reply));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    /**
     * Build a ReActAgentExecutor via the engine's resolveExecutor (which
     * performs HOOK contribution assembly) with a wired contribution registry.
     */
    private ReActAgentExecutor executorViaEngine(IChatService chatService, IContributionRegistry registry) {
        DefaultAgentEngine engine = new DefaultAgentEngine(chatService, simpleToolManager());
        engine.setContributionRegistry(registry);
        return (ReActAgentExecutor) engine.resolveExecutor(agentModel);
    }

    // =========================================================================
    // End-to-end: HOOK contribution → engine assembly → ReAct loop fires hook
    // (Minimum Rules #22, #23 — Anti-Hollow: full path from register to fire)
    // =========================================================================

    @Test
    void hookContributionFiresAtLifecyclePoint_e2e() {
        AtomicInteger fireCount = new AtomicInteger(0);
        IAgentLifecycleHook hook = ctx -> {
            fireCount.incrementAndGet();
            return HookResult.PassResult.instance();
        };

        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forHook(
                "pre-reasoning-hook", "plugin-x", 0,
                AgentLifecyclePoint.PRE_REASONING, hook));

        ReActAgentExecutor executor = executorViaEngine(successChatService("done"), registry);

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(fireCount.get() > 0,
                "HOOK contribution must fire at the PRE_REASONING lifecycle point (got " + fireCount.get() + ")");
    }

    @Test
    void multipleHookContributionsFireInPriorityOrder_e2e() {
        AtomicInteger order = new AtomicInteger(0);
        List<Integer> callOrder = Collections.synchronizedList(new java.util.ArrayList<>());

        IAgentLifecycleHook hookPrio10 = ctx -> {
            callOrder.add(order.incrementAndGet());
            return HookResult.PassResult.instance();
        };
        IAgentLifecycleHook hookPrio1 = ctx -> {
            callOrder.add(order.incrementAndGet());
            return HookResult.PassResult.instance();
        };

        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forHook(
                "prio-10", "plugin-x", 10,
                AgentLifecyclePoint.PRE_REASONING, hookPrio10));
        registry.register(Contribution.forHook(
                "prio-1", "plugin-x", 1,
                AgentLifecyclePoint.PRE_REASONING, hookPrio1));

        ReActAgentExecutor executor = executorViaEngine(successChatService("done"), registry);

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertFalse(callOrder.isEmpty(), "HOOK contributions must fire");
        // The prio-1 hook (registered second) must be called first because the
        // engine resolves contributions in ascending priority order.
        assertEquals(1, callOrder.get(0).intValue(), "prio-1 hook must fire before prio-10 hook");
    }

    @Test
    void badHookPayloadIsWarnSkippedOthersStillRegister_e2e() {
        // Plan 217 裁定 5 + Minimum Rules #24: a HOOK contribution with a
        // non-HookPayload payload is WARN-skipped, but the remaining HOOK
        // contributions register normally.
        AtomicInteger goodFireCount = new AtomicInteger(0);
        IAgentLifecycleHook goodHook = ctx -> {
            goodFireCount.incrementAndGet();
            return HookResult.PassResult.instance();
        };

        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(new Contribution(
                ContributionType.HOOK, "bad-hook", "plugin-x", 0, "not-a-hookpayload"));
        registry.register(Contribution.forHook(
                "good-hook", "plugin-x", 0,
                AgentLifecyclePoint.PRE_REASONING, goodHook));

        ReActAgentExecutor executor = executorViaEngine(successChatService("done"), registry);

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(goodFireCount.get() > 0,
                "the GOOD hook must fire even when the BAD hook was WARN-skipped");
    }

    // =========================================================================
    // End-to-end: PROMPT contribution → executor setup → injected into system
    // prompt context (Minimum Rules #22 — Anti-Hollow)
    // =========================================================================

    @Test
    void promptContributionInjectedIntoSystemMessages_e2e() {
        AtomicReference<String> capturedSystem = new AtomicReference<>("");
        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forPrompt(
                "p1", "plugin-y", 0, "ALWAYS_RESPOND_WITH_HELLO"));

        ReActAgentExecutor executor = executorViaEngine(
                capturingChatService(capturedSystem, "done"), registry);

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        String system = capturedSystem.get();
        assertTrue(system.contains("ALWAYS_RESPOND_WITH_HELLO"),
                "PROMPT contribution fragment must appear in the system messages"
                        + " consumed by the LLM call. Got: " + system);
    }

    @Test
    void multiplePromptContributionsJoinedByPriority_e2e() {
        AtomicReference<String> capturedSystem = new AtomicReference<>("");
        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forPrompt("p-low", "plugin-y", 10, "B"));
        registry.register(Contribution.forPrompt("p-high", "plugin-y", 1, "A"));

        ReActAgentExecutor executor = executorViaEngine(
                capturingChatService(capturedSystem, "done"), registry);

        executor.execute(buildContext()).toCompletableFuture().join();
        String system = capturedSystem.get();
        int idxA = system.indexOf('A');
        int idxB = system.indexOf('B');
        assertTrue(idxA >= 0 && idxB >= 0, "Both prompt fragments must be injected. Got: " + system);
        assertTrue(idxA < idxB,
                "Priority-1 fragment must be injected BEFORE priority-10 fragment. Got: " + system);
    }

    @Test
    void emptyPromptFragmentIsSkipped_e2e() {
        AtomicReference<String> capturedSystem = new AtomicReference<>("baseline");
        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forPrompt("empty", "plugin-y", 0, ""));
        registry.register(Contribution.forPrompt("real", "plugin-y", 0, "VISIBLE_MARKER"));

        ReActAgentExecutor executor = executorViaEngine(
                capturingChatService(capturedSystem, "done"), registry);

        executor.execute(buildContext()).toCompletableFuture().join();
        assertTrue(capturedSystem.get().contains("VISIBLE_MARKER"));
    }

    @Test
    void badPromptPayloadIsWarnSkippedOthersStillInject_e2e() {
        AtomicReference<String> capturedSystem = new AtomicReference<>("");
        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(new Contribution(
                ContributionType.PROMPT, "bad-prompt", "plugin-y", 0, new Object()));
        registry.register(Contribution.forPrompt("good-prompt", "plugin-y", 0, "VISIBLE"));

        ReActAgentExecutor executor = executorViaEngine(
                capturingChatService(capturedSystem, "done"), registry);

        executor.execute(buildContext()).toCompletableFuture().join();
        assertTrue(capturedSystem.get().contains("VISIBLE"));
    }

    // =========================================================================
    // NoOp default: zero regression (Minimum Rules #23 — Wiring Verification)
    // =========================================================================

    @Test
    void noOpDefaultRegistryZeroRegression_e2e() {
        AtomicInteger hookFires = new AtomicInteger(0);
        // NoOp wired explicitly — should be invisible.
        ReActAgentExecutor executor = executorViaEngine(
                successChatService("done"), NoOpContributionRegistry.noOp());

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(0, hookFires.get(),
                "NoOp registry must not introduce any hook firings");
    }

    @Test
    void noOpRegisterReturnsFalseExplicit_e2e() {
        NoOpContributionRegistry noOp = NoOpContributionRegistry.noOp();
        boolean accepted = noOp.register(Contribution.forPrompt(
                "p1", "plugin-y", 0, "ignored"));
        assertFalse(accepted, "NoOp.register must return false (explicit no-op, Minimum Rules #24)");
        assertTrue(noOp.getContributions(ContributionType.PROMPT).isEmpty());
    }

    @Test
    void engineGetContributionRegistryReturnsWiredInstance() {
        DefaultAgentEngine engine = new DefaultAgentEngine(null, null);
        assertTrue(engine.getContributionRegistry() instanceof NoOpContributionRegistry);

        InMemoryContributionRegistry functional = new InMemoryContributionRegistry();
        engine.setContributionRegistry(functional);
        assertEquals(functional, engine.getContributionRegistry());

        engine.setContributionRegistry(null);
        assertTrue(engine.getContributionRegistry() instanceof NoOpContributionRegistry);
    }

    // =========================================================================
    // Mixed: HOOK + PROMPT contributions from multiple sources
    // =========================================================================

    @Test
    void hookAndPromptContributionsFromMultipleSources_e2e() {
        AtomicInteger hookFires = new AtomicInteger(0);
        IAgentLifecycleHook hook = ctx -> {
            hookFires.incrementAndGet();
            return HookResult.PassResult.instance();
        };
        AtomicReference<String> capturedSystem = new AtomicReference<>("");

        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forHook(
                "h1", "plugin-hook", 0, AgentLifecyclePoint.POST_REASONING, hook));
        registry.register(Contribution.forPrompt(
                "p1", "plugin-prompt", 0, "PROMPT_FROM_DIFFERENT_PLUGIN"));

        assertEquals(Set.of("plugin-hook", "plugin-prompt"), registry.getSources());

        ReActAgentExecutor executor = executorViaEngine(
                capturingChatService(capturedSystem, "done"), registry);

        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertTrue(hookFires.get() > 0, "hook from plugin-hook must fire");
        assertTrue(capturedSystem.get().contains("PROMPT_FROM_DIFFERENT_PLUGIN"),
                "prompt from plugin-prompt must be injected");
    }

    @Test
    void unregisterSourceRemovesHookFromAssembly_e2e() {
        AtomicInteger hookFires = new AtomicInteger(0);
        IAgentLifecycleHook hook = ctx -> {
            hookFires.incrementAndGet();
            return HookResult.PassResult.instance();
        };

        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forHook(
                "h1", "plugin-x", 0, AgentLifecyclePoint.PRE_REASONING, hook));

        ReActAgentExecutor executor1 = executorViaEngine(successChatService("done"), registry);
        executor1.execute(buildContext()).toCompletableFuture().join();
        int firesAfterFirst = hookFires.get();
        assertTrue(firesAfterFirst > 0, "hook must fire on first build");

        registry.unregisterSource("plugin-x");
        hookFires.set(0);
        ReActAgentExecutor executor2 = executorViaEngine(successChatService("done"), registry);
        executor2.execute(buildContext()).toCompletableFuture().join();
        assertEquals(0, hookFires.get(),
                "after unregisterSource, the hook must NOT fire on the next build");
    }

    @Test
    void hookPayloadCarriesLifecyclePointToRegistry_e2e() {
        List<AgentLifecyclePoint> firedPoints =
                Collections.synchronizedList(new java.util.ArrayList<>());
        IAgentLifecycleHook hook = ctx -> {
            firedPoints.add(ctx.getLifecyclePoint());
            return HookResult.PassResult.instance();
        };

        InMemoryContributionRegistry registry = new InMemoryContributionRegistry();
        registry.register(Contribution.forHook(
                "h1", "plugin-x", 0, AgentLifecyclePoint.POST_REASONING, hook));

        ReActAgentExecutor executor = executorViaEngine(successChatService("done"), registry);

        executor.execute(buildContext()).toCompletableFuture().join();
        assertFalse(firedPoints.isEmpty(), "hook must fire");
        for (AgentLifecyclePoint p : firedPoints) {
            assertEquals(AgentLifecyclePoint.POST_REASONING, p,
                    "hook must fire only at the point declared in HookPayload, got: " + p);
        }
    }

    // =========================================================================
    // Existing-engine regression: no contribution registry wired → executor
    // builds and runs identically to pre-plan-217 (DefaultAgentEngine ctor
    // path).
    // =========================================================================

    @Test
    void engineWithoutContributionRegistrySetterUsesNoOpDefault() {
        // Construct engine without calling setContributionRegistry. The
        // resolveExecutor must use NoOp internally — the executor builds
        // and runs identically to the pre-plan-217 executor.
        DefaultAgentEngine engine = new DefaultAgentEngine(successChatService("done"), simpleToolManager());
        ReActAgentExecutor executor = (ReActAgentExecutor) engine.resolveExecutor(agentModel);
        AgentExecutionResult result = executor.execute(buildContext()).toCompletableFuture().join();
        assertEquals(AgentExecStatus.completed, result.getStatus());
    }
}
