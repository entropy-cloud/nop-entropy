package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ChannelKind;
import io.nop.ai.agent.security.DefaultPostDenialGuard;
import io.nop.ai.agent.security.FingerprintPostDenialGuard;
import io.nop.ai.agent.security.IPostDenialGuard;
import io.nop.ai.agent.security.PassThroughPostDenialGuard;
import io.nop.ai.agent.security.Principal;
import io.nop.ai.agent.security.ToolAccessResult;
import io.nop.ai.agent.security.IToolAccessChecker;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 end-to-end tests: verifies the Layer 3 post-denial-guard
 * dispatch-path integration is actually connected — from
 * {@link AgentMessageRequest}, through {@code DefaultAgentEngine.doExecute},
 * the {@link ReActAgentExecutor} Builder, into the dispatch loop where the
 * pre-Layer-1 consultation blocks blind retries and the post-deny recording
 * feeds the guard's per-session denied set.
 *
 * <p>Includes (design §6.3 / L3-7):
 * <ul>
 *   <li>Scenario A — single-iteration multi-call: identical tool calls →
 *       first denied by Layer 1 (recording), second blocked by guard
 *       <i>before</i> Layer 1 (consultation hit).</li>
 *   <li>Scenario B — cross-iteration: identical tool call across iterations →
 *       guard consultation hit on the 2nd iteration.</li>
 *   <li>Scenario C — parameter change (legitimate follow-up): different
 *       arguments → different fingerprint → guard consultation passes →
 *       Layer 1 normal execution.</li>
 *   <li>Wiring verification ({@code checkBeforeDispatch} +
 *       {@code recordDeniedAction} actually invoked in the dispatch loop).</li>
 *   <li>Backward-compat ({@link PassThroughPostDenialGuard} default produces
 *       zero spurious guard-denials, Layer 1 runs for every call).</li>
 *   <li>Engine setter/getter wiring + null fallback.</li>
 * </ul>
 */
public class TestDispatchPathPostDenialGuard {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Test-internal functional components
    // ========================================================================

    /**
     * Test-internal tool-access checker that denies every tool call and
     * counts how many times {@code checkAccess} was invoked, so tests can
     * assert "Layer 1 was NOT called for the guard-blocked call".
     */
    static final class CountingDenyAllTools implements IToolAccessChecker {
        final AtomicInteger checkCount = new AtomicInteger();

        @Override
        public ToolAccessResult checkAccess(String toolName, AgentExecutionContext ctx) {
            checkCount.incrementAndGet();
            return ToolAccessResult.deny("test deny-all rule");
        }
    }

    /**
     * Test-internal {@link IPostDenialGuard} that counts consultation and
     * recording invocations, delegating to a real {@link FingerprintPostDenialGuard}
     * for functional behavior. Used for wiring verification.
     */
    static final class CountingFingerprintGuard implements IPostDenialGuard {
        final FingerprintPostDenialGuard delegate = new FingerprintPostDenialGuard();
        final AtomicInteger consultCount = new AtomicInteger();
        final AtomicInteger recordCount = new AtomicInteger();

        @Override
        public io.nop.ai.agent.security.DenialResult checkBeforeDispatch(
                String sessionId, String toolName, Map<String, Object> arguments, String workDir) {
            consultCount.incrementAndGet();
            return delegate.checkBeforeDispatch(sessionId, toolName, arguments, workDir);
        }

        @Override
        public void recordDeniedAction(String sessionId, String toolName,
                                       Map<String, Object> arguments, String workDir) {
            recordCount.incrementAndGet();
            delegate.recordDeniedAction(sessionId, toolName, arguments, workDir);
        }

        @Override
        public void reset(String sessionId) {
            delegate.reset(sessionId);
        }
    }

    // ========================================================================
    // Mocks
    // ========================================================================

    static final class RecordingChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger();
        final List<ChatResponse> scripted;

        RecordingChatService(List<ChatResponse> scripted) {
            this.scripted = scripted;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int idx = Math.min(callCount.getAndIncrement(), scripted.size() - 1);
            return scripted.get(idx);
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {};
        }
    }

    private static ChatResponse assistantWithToolCalls(ChatToolCall... calls) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("");
        msg.setToolCalls(List.of(calls));
        return ChatResponse.success(msg);
    }

    private static ChatResponse finalAssistant(String content) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent(content);
        return ChatResponse.success(msg);
    }

    private static ChatToolCall toolCall(String id, String name, Map<String, Object> args) {
        ChatToolCall c = new ChatToolCall();
        c.setId(id);
        c.setName(name);
        c.setArguments(args);
        return c;
    }

    private static IToolManager stubToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
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

    private static boolean containsMessage(AgentExecutionResult result, String needle) {
        for (ChatMessage m : result.getMessages()) {
            if (m instanceof ChatToolResponseMessage) {
                ChatToolResponseMessage tr = (ChatToolResponseMessage) m;
                String body = tr.getContent() != null ? tr.getContent() : "";
                if (body.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========================================================================
    // Scenario A: single-iteration multi-call — blind retry blocked before Layer 1
    // ========================================================================

    /**
     * Scenario A (design §6.3 / L3-7): LLM returns 2 identical tool calls in
     * one response. CountingDenyAllTools denies everything.
     * <ul>
     *   <li>Call 1: guard consultation returns null (not yet recorded) →
     *       Layer 1 checkAccess denies → recording adds the fingerprint →
     *       continue.</li>
     *   <li>Call 2 (identical): guard consultation hits (REPEATED_SAME_INTENT)
     *       → Layer 1 checkAccess is NOT called → guard-deny path → continue.</li>
     * </ul>
     * Asserts Layer 1 checkAccess was called exactly once (only for call 1),
     * proving the blind retry was intercepted before the Layer 1 check.
     */
    @Test
    void scenarioA_singleIterationBlindRetryBlockedBeforeLayer1() {
        CountingFingerprintGuard guard = new CountingFingerprintGuard();
        CountingDenyAllTools tools = new CountingDenyAllTools();

        Map<String, Object> args = Map.of("command", "rm -rf /");
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(
                        toolCall("call_a1", "shell.exec", args),
                        toolCall("call_a2", "shell.exec", args)),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                tools);
        engine.setPostDenialGuard(guard);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Wiring verification (Minimum Rules #23): both guard methods were
        // actually invoked in the dispatch loop.
        assertTrue(guard.consultCount.get() >= 2,
                "checkBeforeDispatch must be called for both tool calls (wiring). Got: "
                        + guard.consultCount.get());
        assertTrue(guard.recordCount.get() >= 1,
                "recordDeniedAction must be called after the first deny (wiring). Got: "
                        + guard.recordCount.get());

        // Anti-Hollow (Minimum Rules #22): Layer 1 was called exactly ONCE —
        // only for the first tool call. The second (identical) call was
        // intercepted by the guard consultation before reaching Layer 1.
        assertEquals(1, tools.checkCount.get(),
                "Layer 1 checkAccess must be called exactly once (only for call 1). "
                        + "A count of 2 means the guard consultation did NOT intercept the blind retry. Got: "
                        + tools.checkCount.get());

        // Both calls were denied — neither produced a successful tool result.
        assertFalse(containsMessage(result, "ok"),
                "No tool call should execute successfully — both were denied.");
    }

    // ========================================================================
    // Scenario B: cross-iteration blind retry blocked before Layer 1
    // ========================================================================

    /**
     * Scenario B (design §6.3 / L3-7): LLM returns 1 identical tool call per
     * response across 2 iterations.
     * <ul>
     *   <li>Iteration 1: guard consultation returns null (not recorded) →
     *       Layer 1 denies → recording.</li>
     *   <li>Iteration 2: guard consultation hits (fingerprint already in the
     *       session set) → Layer 1 NOT called → guard-deny.</li>
     * </ul>
     */
    @Test
    void scenarioB_crossIterationBlindRetryBlockedBeforeLayer1() {
        CountingFingerprintGuard guard = new CountingFingerprintGuard();
        CountingDenyAllTools tools = new CountingDenyAllTools();

        Map<String, Object> args = Map.of("command", "rm -rf /");
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_b1", "shell.exec", args)),
                assistantWithToolCalls(toolCall("call_b2", "shell.exec", args)),
                assistantWithToolCalls(toolCall("call_b3", "shell.exec", args))
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                tools);
        engine.setPostDenialGuard(guard);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();

        // Anti-Hollow: Layer 1 was called exactly ONCE (iteration 1). On
        // iteration 2, the guard consultation intercepted the blind retry.
        // (Iteration 3 may or may not run depending on whether iteration 2's
        // guard-deny is also recorded + threshold; here there is no ledger
        // threshold so the loop continues, but every subsequent iteration's
        // consultation hits.)
        assertTrue(tools.checkCount.get() <= 1,
                "Layer 1 checkAccess must be called at most once (only iteration 1). "
                        + "More calls mean the guard consultation did NOT intercept cross-iteration blind retries. Got: "
                        + tools.checkCount.get());

        // The guard consultation must have run for each iteration that started.
        assertTrue(guard.consultCount.get() >= 2,
                "checkBeforeDispatch must run on each iteration. Got: " + guard.consultCount.get());
    }

    // ========================================================================
    // Scenario C: parameter change (legitimate follow-up) → allowed
    // ========================================================================

    /**
     * Scenario C (design §6.3): LLM returns 2 tool calls with the same tool
     * name but DIFFERENT arguments (a legitimate follow-up). The first is
     * denied by Layer 1 and recorded. The second has a different fingerprint
     * (different arguments) → guard consultation returns null → Layer 1 runs.
     *
     * <p>This verifies the exact-fingerprint matching correctly allows
     * legitimate follow-ups that change parameters.
     */
    @Test
    void scenarioC_parameterChangeAllowedLegitimateFollowUp() {
        CountingFingerprintGuard guard = new CountingFingerprintGuard();
        CountingDenyAllTools tools = new CountingDenyAllTools();

        Map<String, Object> args1 = Map.of("path", "/a/b.txt");
        Map<String, Object> args2 = Map.of("path", "/a/c.txt");
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(
                        toolCall("call_c1", "write-file", args1),
                        toolCall("call_c2", "write-file", args2)),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                tools);
        engine.setPostDenialGuard(guard);

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        engine.execute(req).toCompletableFuture().join();

        // Anti-Hollow: Layer 1 was called for BOTH tool calls — the second
        // call had different arguments (different fingerprint), so the guard
        // consultation let it through to Layer 1.
        assertEquals(2, tools.checkCount.get(),
                "Layer 1 checkAccess must be called for BOTH calls (different args = different fingerprint = legitimate follow-up). Got: "
                        + tools.checkCount.get());

        // The first deny was recorded (into the guard); the second call's
        // consultation returned null (not a blind retry).
        assertTrue(guard.recordCount.get() >= 1,
                "The first deny must be recorded. Got: " + guard.recordCount.get());
    }

    // ========================================================================
    // Backward-compat: PassThroughPostDenialGuard default = 0 spurious guard-denials
    // ========================================================================

    /**
     * Backward compatibility (Minimum Rules #24): with the shipped
     * {@link PassThroughPostDenialGuard} default (no guard registered), the
     * engine behaves exactly as before wiring — Layer 1 runs for every tool
     * call, and no guard-deny ever fires.
     */
    @Test
    void passThroughDefault_noSpuriousGuardDenials_layer1RunsForEveryCall() {
        CountingDenyAllTools tools = new CountingDenyAllTools();

        Map<String, Object> args = Map.of("command", "ls");
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(
                        toolCall("call_p1", "shell.exec", args),
                        toolCall("call_p2", "shell.exec", args)),
                finalAssistant("done")
        ));

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, stubToolManager(),
                new io.nop.ai.agent.session.InMemorySessionStore(),
                new io.nop.ai.agent.security.AllowAllPermissionProvider(),
                tools);
        // Deliberately opt into PassThroughPostDenialGuard to verify the
        // backward-compat behavior (default is now DefaultPostDenialGuard).
        engine.setPostDenialGuard(PassThroughPostDenialGuard.passThrough());

        AgentMessageRequest req = new AgentMessageRequest(
                "test-react-agent", "run", null, null, ChannelKind.WEBUI, Principal.user());

        AgentExecutionResult result = engine.execute(req).toCompletableFuture().join();

        // Layer 1 ran for BOTH tool calls — the pass-through does not
        // block any retry.
        assertEquals(2, tools.checkCount.get(),
                "With PassThroughPostDenialGuard, Layer 1 must run for every tool call. Got: "
                        + tools.checkCount.get());

        // No guard-deny messages — the pass-through never produces a
        // REPEATED_SAME_INTENT denial.
        assertFalse(containsMessage(result, "Repeated same denied action"),
                "PassThroughPostDenialGuard must never produce a guard-deny.");
    }

    // ========================================================================
    // Wiring: engine setter/getter + null fallback + Builder wiring
    // ========================================================================

    /**
     * Engine setter/getter wiring: registering a functional guard makes it
     * retrievable; passing null to the setter falls back to the pass-through
     * default (not silently ignored).
     */
    @Test
    void engineSetterGetterAndNullFallback() {
        DefaultAgentEngine engine = new DefaultAgentEngine(
                new RecordingChatService(List.of(finalAssistant("done"))),
                stubToolManager());

        // Default is DefaultPostDenialGuard.
        assertNotNull(engine.getPostDenialGuard(), "engine must expose a default post-denial guard");
        assertTrue(engine.getPostDenialGuard() instanceof DefaultPostDenialGuard,
                "Default post-denial guard must be DefaultPostDenialGuard");

        // Setter registers a functional guard.
        FingerprintPostDenialGuard functional = new FingerprintPostDenialGuard();
        engine.setPostDenialGuard(functional);
        assertSame(functional, engine.getPostDenialGuard(),
                "getPostDenialGuard must return the registered functional guard");

        // Setter with null falls back to the default (not null, not silently ignored).
        engine.setPostDenialGuard(null);
        assertTrue(engine.getPostDenialGuard() instanceof DefaultPostDenialGuard,
                "setPostDenialGuard(null) must fall back to DefaultPostDenialGuard, not null");
    }

    /**
     * Builder wiring verification (Minimum Rules #23): the guard passed via
     * the Builder is actually consulted in the dispatch loop.
     */
    @Test
    void guardConsultedViaBuilderWiring() {
        CountingFingerprintGuard guard = new CountingFingerprintGuard();
        CountingDenyAllTools tools = new CountingDenyAllTools();

        Map<String, Object> args = Map.of("command", "ls");
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(toolCall("call_bw1", "shell.exec", args)),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .toolAccessChecker(tools)
                .postDenialGuard(guard)
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(
                buildMinimalReactAgentModel(), "session-bw");
        executor.execute(ctx).toCompletableFuture().join();

        assertTrue(guard.consultCount.get() >= 1,
                "Builder-wired guard must be consulted in the dispatch loop. Got: "
                        + guard.consultCount.get());
        assertTrue(guard.recordCount.get() >= 1,
                "Builder-wired guard must have the deny recorded. Got: "
                        + guard.recordCount.get());
    }

    /**
     * Builder null-fallback: when no guard is supplied, the executor defaults
     * to {@link DefaultPostDenialGuard} (fingerprint-based). With two identical
     * shell.exec calls and a DenyAll tool checker, the second identical call
     * is blocked as a blind retry — so Layer 1 runs only for the first call.
     */
    @Test
    void builderNullFallbackDefaultsToDefaultPostDenialGuard() {
        CountingDenyAllTools tools = new CountingDenyAllTools();

        Map<String, Object> args = Map.of("command", "ls");
        RecordingChatService chat = new RecordingChatService(List.of(
                assistantWithToolCalls(
                        toolCall("call_nf1", "shell.exec", args),
                        toolCall("call_nf2", "shell.exec", args)),
                finalAssistant("done")
        ));

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chat)
                .toolManager(stubToolManager())
                .toolAccessChecker(tools)
                // no postDenialGuard() — defaults to DefaultPostDenialGuard
                .build();

        AgentExecutionContext ctx = AgentExecutionContext.create(
                buildMinimalReactAgentModel(), "session-nf");
        executor.execute(ctx).toCompletableFuture().join();

        // DefaultPostDenialGuard blocks the second identical call as a blind
        // retry — Layer 1 runs only for the first call.
        assertEquals(1, tools.checkCount.get(),
                "Builder with no guard defaults to DefaultPostDenialGuard — second identical call "
                        + "is blocked as blind retry, Layer 1 runs once. Got: "
                        + tools.checkCount.get());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static io.nop.ai.agent.model.AgentModel buildMinimalReactAgentModel() {
        io.nop.ai.agent.model.AgentModel model = new io.nop.ai.agent.model.AgentModel();
        model.setName("test-react-agent");
        model.setMode("react");
        io.nop.ai.core.model.ChatOptionsModel opts = new io.nop.ai.core.model.ChatOptionsModel();
        opts.setMaxTokens(32000);
        model.setChatOptions(opts);
        return model;
    }
}
