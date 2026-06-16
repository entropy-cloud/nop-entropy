package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.engine.ReActAgentExecutor;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.router.Complexity;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.agent.router.SmartModelRouter;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.session.IModelSwitchedMessageWriter;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 213 (circuit-aware-routing) tests: the circuit-aware routing resolution
 * step wired into {@link ReActAgentExecutor} between {@code route()} and the
 * model-switched audit detection (Minimum Rules #22 end-to-end, #23 wiring,
 * #24 no silent skip, #25 new feature coverage).
 *
 * <p>Phase 1 (zero-regression / wiring):
 * <ul>
 *   <li>Default config ({@link AlwaysClosed} + {@link PassThroughModelRouter})
 *       is a no-op pass-through — primary model used, no fallback scan.</li>
 *   <li>{@link AlwaysClosed} + {@link SmartModelRouter} with a configured
 *       fallback chain still uses the primary (allowCall always true →
 *       resolution returns immediately).</li>
 * </ul>
 *
 * <p>Phase 2 (functional circuit-aware routing):
 * <ul>
 *   <li>Circuit-OPEN primary → resolution scans fallback chain → switches to a
 *       circuit-closed fallback → execution succeeds using the fallback.</li>
 *   <li>All models circuit-rejected → fail-fast {@code NopAiAgentException}
 *       listing every checked model-key + circuit state (no silent skip).</li>
 *   <li>{@link PassThroughModelRouter} (null fallback) + circuit-OPEN primary
 *       → fail-fast (backward-compatible with plan 210).</li>
 *   <li>Circuit resolution triggers the model-switched audit message (plan 205)
 *       with {@code fromModel}=primary, {@code toModel}=fallback.</li>
 *   <li>HALF_OPEN probe-busy primary → resolution scans to a CLOSED fallback.</li>
 *   <li>End-to-end through {@link DefaultAgentEngine} with a real
 *       {@link ThresholdBreaker} pre-tripped to OPEN (Minimum Rules #22).</li>
 * </ul>
 */
public class TestCircuitAwareRouting {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // Model-key composite format is "provider:model".
    private static final String PRIMARY_KEY = "p:primary";
    private static final String FALLBACK1_KEY = "p:fallback1";
    private static final String FALLBACK2_KEY = "p:fallback2";

    private static ChatOptions tier(String provider, String model) {
        ChatOptions o = new ChatOptions();
        o.setProvider(provider);
        o.setModel(model);
        return o;
    }

    private static AgentExecutionContext simpleContext(String sessionId) {
        return simpleContext(sessionId, Collections.emptySet());
    }

    private static AgentExecutionContext simpleContext(String sessionId, java.util.Set<String> tools) {
        AgentModel model = new AgentModel();
        model.setTools(tools);
        io.nop.ai.core.model.ChatOptionsModel co = new io.nop.ai.core.model.ChatOptionsModel();
        co.setModel("default");
        co.setProvider("default");
        model.setChatOptions(co);
        AgentExecutionContext ctx = AgentExecutionContext.create(model, sessionId);
        ctx.setMaxIterations(10);
        ctx.addMessage(new ChatUserMessage("hi"));
        return ctx;
    }

    private abstract static class StubToolManager implements IToolManager {
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
            AiToolModel m = new AiToolModel();
            m.setName(toolName);
            m.setDescription("stub");
            return m;
        }
    }

    /**
     * Chat service that returns a successful response (with usage) for every
     * call, recording the model keys actually sent to the LLM. Used to prove
     * which model the resolution selected.
     */
    private static final class RecordingSuccessChatService implements IChatService {
        final AtomicInteger callCount = new AtomicInteger(0);
        final List<String> modelsCalled = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            String model = request.getOptions().getModel();
            String provider = request.getOptions().getProvider();
            modelsCalled.add(provider + ":" + model);
            callCount.incrementAndGet();
            ChatAssistantMessage msg = new ChatAssistantMessage();
            msg.setContent("done.");
            ChatResponse resp = ChatResponse.success(msg);
            resp.setUsage(new ChatUsage(10, 5));
            return resp;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    /**
     * A circuit breaker scripted per model-key. Models not listed report
     * CLOSED and are allowed; listed models report the scripted state and are
     * rejected (allowCall false) — modelling both OPEN and HALF_OPEN-probe-busy
     * rejection. Used for precise resolution-logic tests.
     */
    private static final class ScriptedCircuitBreaker implements ICircuitBreaker {
        private final Map<String, CircuitState> states;

        ScriptedCircuitBreaker(Map<String, CircuitState> states) {
            this.states = states != null ? states : Collections.emptyMap();
        }

        @Override
        public boolean allowCall(String modelKey) {
            CircuitState s = states.getOrDefault(modelKey, CircuitState.CLOSED);
            // Only CLOSED is allowed; OPEN and HALF_OPEN (probe busy) reject.
            return s == CircuitState.CLOSED;
        }

        @Override
        public CircuitState getState(String modelKey) {
            return states.getOrDefault(modelKey, CircuitState.CLOSED);
        }

        @Override
        public void recordSuccess(String modelKey) {
        }

        @Override
        public void recordFailure(String modelKey) {
        }
    }

    /**
     * Capturing {@link IModelSwitchedMessageWriter} that records every
     * writeModelSwitched call so the test can assert the fromModel/toModel
     * audit fields.
     */
    private static final class CapturingModelSwitchedWriter implements IModelSwitchedMessageWriter {
        final List<String[]> captured = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void writeModelSwitched(String sessionId, String fromModel, String toModel,
                                       String routingReason, String complexity, long seq) {
            captured.add(new String[]{sessionId, fromModel, toModel, routingReason, complexity, String.valueOf(seq)});
        }
    }

    // ========================================================================
    // Phase 1 — zero-regression / wiring
    // ========================================================================

    /**
     * Default config (AlwaysClosed + PassThroughModelRouter): the resolution
     * step is a no-op pass-through. The primary model is used exactly once,
     * the execution completes, and no fallback scan is entered (Minimum Rules
     * #23 wiring — proves the resolution is wired and is a zero-regression
     * pass-through with the shipped defaults).
     */
    @Test
    void defaultConfigResolutionIsNoOp() {
        RecordingSuccessChatService chatService = new RecordingSuccessChatService();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                // Shipped defaults: PassThroughModelRouter + AlwaysClosed
                .build();

        AgentExecutionResult result = executor.execute(simpleContext("p1-default"))
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "default config must complete normally (zero-regression)");
        assertEquals(1, chatService.callCount.get(),
                "exactly one LLM call — resolution is a no-op pass-through");
        // The single call used the (un-routed) default model — PassThrough
        // returns options unchanged and AlwaysClosed allows it.
        assertEquals(1, chatService.modelsCalled.size());
    }

    /**
     * AlwaysClosed + SmartModelRouter with a configured fallback chain: because
     * allowCall always returns true, the resolution never enters the fallback
     * scan. The router's primary model is used, NOT the fallback. Proves the
     * resolution honours the circuit-closed primary and does not gratuitously
     * switch models.
     */
    @Test
    void alwaysClosedWithSmartModelRouterDoesNotScanFallback() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback1"))
                .build();
        RecordingSuccessChatService chatService = new RecordingSuccessChatService();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                // AlwaysClosed is the shipped default (no .circuitBreaker(...))
                .build();

        AgentExecutionResult result = executor.execute(simpleContext("p1-smart"))
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus());
        assertEquals(1, chatService.callCount.get(),
                "exactly one LLM call — primary used");
        assertEquals(PRIMARY_KEY, chatService.modelsCalled.get(0),
                "the router's PRIMARY model must be used (resolution did not scan "
                        + "because AlwaysClosed allows the primary)");
        assertFalse(chatService.modelsCalled.contains(FALLBACK1_KEY),
                "the fallback model must NOT have been used — no circuit rejection occurred");
    }

    // ========================================================================
    // Phase 2 — functional circuit-aware routing
    // ========================================================================

    /**
     * Circuit-OPEN primary → resolution scans the fallback chain → finds a
     * circuit-closed fallback → switches routedOptions to it → execution
     * succeeds using the fallback. The OPEN primary is NEVER sent to the LLM
     * (callCount for primary == 0). (Minimum Rules #23 wiring: the resolution
     * is provably invoked at runtime.)
     */
    @Test
    void circuitOpenPrimaryResolvesToCircuitClosedFallback() {
        Map<String, CircuitState> states = new HashMap<>();
        states.put(PRIMARY_KEY, CircuitState.OPEN);
        ScriptedCircuitBreaker breaker = new ScriptedCircuitBreaker(states);

        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback1"))
                .build();
        RecordingSuccessChatService chatService = new RecordingSuccessChatService();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .circuitBreaker(breaker)
                .build();

        AgentExecutionResult result = executor.execute(simpleContext("p2-open-primary"))
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "circuit-OPEN primary must be resolved to the fallback and complete");
        assertTrue(chatService.modelsCalled.contains(FALLBACK1_KEY),
                "the circuit-closed fallback must have been used");
        assertFalse(chatService.modelsCalled.contains(PRIMARY_KEY),
                "the circuit-OPEN primary must NOT have been sent to the LLM "
                        + "(resolution skipped it) — models called: " + chatService.modelsCalled);
        assertEquals(FALLBACK1_KEY, chatService.modelsCalled.get(chatService.modelsCalled.size() - 1),
                "the final (successful) LLM call must use the fallback");
    }

    /**
     * All models (primary + every fallback) circuit-rejected → fail-fast
     * {@code NopAiAgentException}. The error must mention the checked model
     * keys and circuit states, and must NOT silently continue (Minimum Rules
     * #24). No LLM call is issued.
     */
    @Test
    void allModelsCircuitOpenFailsFastWithDiagnostic() {
        Map<String, CircuitState> states = new HashMap<>();
        states.put(PRIMARY_KEY, CircuitState.OPEN);
        states.put(FALLBACK1_KEY, CircuitState.OPEN);
        states.put(FALLBACK2_KEY, CircuitState.OPEN);
        ScriptedCircuitBreaker breaker = new ScriptedCircuitBreaker(states);

        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback1"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback2"))
                .build();
        RecordingSuccessChatService chatService = new RecordingSuccessChatService();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .circuitBreaker(breaker)
                .build();

        AgentExecutionResult result = executor.execute(simpleContext("p2-all-open"))
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "all models circuit-OPEN must fail the execution (no silent skip)");
        assertEquals(0, chatService.callCount.get(),
                "no LLM call must be issued when every model is circuit-rejected");
        assertNotNull(result.getError(), "fail-fast must record an error");
        // The diagnostic must surface every checked model-key + its state.
        assertTrue(result.getError().contains("p:primary"),
                "error must mention the primary model-key: " + result.getError());
        assertTrue(result.getError().contains("p:fallback1"),
                "error must mention the first fallback model-key: " + result.getError());
        assertTrue(result.getError().contains("p:fallback2"),
                "error must mention the second fallback model-key: " + result.getError());
        assertTrue(result.getError().contains("OPEN"),
                "error must mention the circuit state: " + result.getError());
    }

    /**
     * PassThroughModelRouter (getFallback returns null) + circuit-OPEN primary
     * → resolution scans once, gets null, fails fast. Behaviour is
     * backward-compatible with plan 210's original outer circuit check
     * (circuit-OPEN → fail), except now the fail-fast happens at the resolution
     * step. No LLM call.
     */
    @Test
    void passThroughRouterCircuitOpenFailsFast() {
        Map<String, CircuitState> states = new HashMap<>();
        states.put("default:default", CircuitState.OPEN);
        ScriptedCircuitBreaker breaker = new ScriptedCircuitBreaker(states);

        RecordingSuccessChatService chatService = new RecordingSuccessChatService();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                // PassThroughModelRouter is the shipped default router
                .circuitBreaker(breaker)
                .build();

        AgentExecutionResult result = executor.execute(simpleContext("p2-passthrough"))
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "PassThrough + circuit-OPEN primary must fail fast (backward-compatible)");
        assertEquals(0, chatService.callCount.get(),
                "no LLM call must be issued when the primary is OPEN and no fallback exists");
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("default:default"),
                "error must mention the rejected model-key: " + result.getError());
    }

    /**
     * A stateful breaker that allows the primary model for the first two
     * {@code allowCall(primary)} invocations (covering iteration-0 resolution
     * + the post-resolution safety-net check) and rejects it from the third
     * invocation onward (the iteration-1 resolution). The fallback model is
     * always allowed. This deterministically simulates the primary circuit
     * tripping to OPEN between two ReAct iterations so the circuit-aware
     * resolution triggers an inter-iteration model switch.
     */
    private static final class PrimaryTripsAfterTwoAllowCalls implements ICircuitBreaker {
        final AtomicInteger primaryAllowCalls = new AtomicInteger(0);
        final String primaryKey;

        PrimaryTripsAfterTwoAllowCalls(String primaryKey) {
            this.primaryKey = primaryKey;
        }

        @Override
        public boolean allowCall(String modelKey) {
            if (primaryKey.equals(modelKey)) {
                int n = primaryAllowCalls.getAndIncrement();
                // n=0 (iter0 resolution) and n=1 (iter0 safety-net) pass;
                // n>=2 (iter1 resolution) trips to OPEN.
                return n < 2;
            }
            return true;
        }

        @Override
        public CircuitState getState(String modelKey) {
            if (primaryKey.equals(modelKey)) {
                return primaryAllowCalls.get() >= 2 ? CircuitState.OPEN : CircuitState.CLOSED;
            }
            return CircuitState.CLOSED;
        }

        @Override
        public void recordSuccess(String modelKey) {
        }

        @Override
        public void recordFailure(String modelKey) {
        }
    }

    /**
     * Two-turn chat service: the first call returns an assistant message with
     * a tool call (so the ReAct loop iterates), the second returns a final
     * answer. Records the model keys (provider:model) actually sent to the LLM.
     */
    private static IChatService twoTurnChatService(ChatToolCall toolCall, List<String> modelsCalled, AtomicInteger callCount) {
        return new IChatService() {
            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                int n = callCount.getAndIncrement();
                String key = request.getOptions().getProvider() + ":" + request.getOptions().getModel();
                modelsCalled.add(key);
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
                return resp;
            }

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
    }

    /**
     * Circuit resolution switching the model between two ReAct iterations
     * triggers the model-switched audit message (plan 205, role=80) with
     * fromModel=primary and toModel=fallback. Because the resolution runs
     * BEFORE the audit detection, the detection observes the post-resolution
     * final model — this test proves the positioning is correct.
     *
     * <p>Setup: a constant SmartModelRouter always routes to the primary; a
     * stateful breaker allows the primary on iteration 0 (resolution +
     * safety-net) and trips it to OPEN on iteration 1's resolution; the
     * resolution then scans the router's fallback chain and switches to the
     * fallback. The inter-iteration model change (primary → fallback) must
     * produce exactly one audit message with fromModel=primary, toModel=fallback.
     */
    @Test
    void circuitResolutionTriggersModelSwitchedAuditMessage() {
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback1"))
                .build();
        PrimaryTripsAfterTwoAllowCalls breaker = new PrimaryTripsAfterTwoAllowCalls(PRIMARY_KEY);
        CapturingModelSwitchedWriter writer = new CapturingModelSwitchedWriter();

        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId("call-audit");
        toolCall.setName("echo");
        toolCall.setArguments(Map.of("msg", "hi"));
        List<String> modelsCalled = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chatService = twoTurnChatService(toolCall, modelsCalled, callCount);

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .circuitBreaker(breaker)
                .modelSwitchedMessageWriter(writer)
                .build();

        AgentExecutionResult result = executor.execute(
                simpleContext("p2-audit", Collections.singleton("echo"))).toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "execution must complete across two iterations");
        assertEquals(2, callCount.get(), "two LLM calls (tool call + final)");
        // Iteration 0 used the primary; iteration 1 used the fallback (after
        // the resolution switched it).
        assertEquals(PRIMARY_KEY, modelsCalled.get(0),
                "iteration 0 must use the primary (circuit still closed)");
        assertEquals(FALLBACK1_KEY, modelsCalled.get(1),
                "iteration 1 must use the fallback (resolution switched it)");
        // Exactly one model-switched audit message, with the correct
        // fromModel/toModel reflecting the circuit-induced switch.
        assertEquals(1, writer.captured.size(),
                "the circuit-induced inter-iteration switch must produce exactly one audit message");
        String[] sw = writer.captured.get(0);
        assertEquals("p2-audit", sw[0], "sessionId must match");
        assertEquals(PRIMARY_KEY, sw[1], "fromModel must be the iteration-0 primary");
        assertEquals(FALLBACK1_KEY, sw[2], "toModel must be the resolved fallback");
    }

    // ========================================================================
    // Phase 2 — end-to-end through DefaultAgentEngine with a REAL

    /**
     * HALF_OPEN probe-busy primary (allowCall false, state HALF_OPEN) →
     * resolution scans to a CLOSED fallback → switches and continues. Models
     * the scenario where a concurrent caller holds the HALF_OPEN probe slot.
     */
    @Test
    void halfOpenProbeBusyResolvesToFallback() {
        Map<String, CircuitState> states = new HashMap<>();
        states.put(PRIMARY_KEY, CircuitState.HALF_OPEN);
        ScriptedCircuitBreaker breaker = new ScriptedCircuitBreaker(states);

        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback1"))
                .build();
        RecordingSuccessChatService chatService = new RecordingSuccessChatService();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(new StubToolManager() {})
                .modelRouter(router)
                .circuitBreaker(breaker)
                .build();

        AgentExecutionResult result = executor.execute(simpleContext("p2-halfopen"))
                .toCompletableFuture().join();

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "HALF_OPEN-probe-busy primary must be resolved to the fallback");
        assertTrue(chatService.modelsCalled.contains(FALLBACK1_KEY),
                "the fallback must have been used");
        assertFalse(chatService.modelsCalled.contains(PRIMARY_KEY),
                "the HALF_OPEN-probe-busy primary must NOT have been called");
    }

    // ========================================================================
    // Phase 2 — end-to-end through DefaultAgentEngine with a REAL
    // ThresholdBreaker (Minimum Rules #22)
    // ========================================================================

    /**
     * End-to-end: a real {@link ThresholdBreaker} is pre-tripped to OPEN for
     * the primary model (simulating accumulated failures across prior
     * executions). The execution starts → route() returns the primary →
     * circuit-aware resolution detects OPEN → scans the SmartModelRouter
     * fallback chain → switches to the circuit-closed fallback → the LLM call
     * succeeds on the fallback → the agent completes. The primary is never
     * called (callCount for primary == 0) and the fallback is called (≥1).
     * (Minimum Rules #22 end-to-end + #23 wiring.)
     */
    @Test
    void endToEndRealThresholdBreakerPreTrippedResolvesToFallback() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        // All tiers route to the same primary/fallback — robust to any
        // classification outcome.
        SmartModelRouter router = SmartModelRouter.builder()
                .tierModel(Complexity.SIMPLE, tier("p", "primary"))
                .fallback(Complexity.SIMPLE, tier("p", "fallback-ok"))
                .tierModel(Complexity.MEDIUM, tier("p", "primary"))
                .fallback(Complexity.MEDIUM, tier("p", "fallback-ok"))
                .tierModel(Complexity.COMPLEX, tier("p", "primary"))
                .fallback(Complexity.COMPLEX, tier("p", "fallback-ok"))
                .build();

        // Real ThresholdBreaker, low threshold. Pre-trip "p:primary" to OPEN
        // by recording threshold consecutive failures before the execution.
        // Default cooldown is 60s, so allowCall(primary) returns false during
        // the test (cooldown has not elapsed).
        ThresholdBreaker breaker = new ThresholdBreaker(2, 60_000L);
        breaker.recordFailure("p:primary");
        breaker.recordFailure("p:primary");
        // Sanity: the breaker must now report OPEN for the primary.
        assertEquals(CircuitState.OPEN, breaker.getState("p:primary"),
                "precondition: primary must be tripped to OPEN before execution");
        assertFalse(breaker.allowCall("p:primary"),
                "precondition: allowCall(primary) must be false while OPEN and cooldown not elapsed");

        AtomicReference<String> lastModel = new AtomicReference<>();
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicInteger primaryCallCount = new AtomicInteger(0);
        AtomicInteger fallbackCallCount = new AtomicInteger(0);
        IChatService chatService = new IChatService() {
            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                String model = request.getOptions().getModel();
                String key = request.getOptions().getProvider() + ":" + model;
                lastModel.set(key);
                callCount.incrementAndGet();
                if ("p:primary".equals(key)) {
                    primaryCallCount.incrementAndGet();
                } else if ("p:fallback-ok".equals(key)) {
                    fallbackCallCount.incrementAndGet();
                }
                ChatAssistantMessage msg = new ChatAssistantMessage();
                msg.setContent("done.");
                ChatResponse resp = ChatResponse.success(msg);
                resp.setUsage(new ChatUsage(10, 5));
                return resp;
            }

            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(call(request, cancelToken));
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };

        DefaultAgentEngine engine = new DefaultAgentEngine(
                chatService, noOpToolManager(), new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker(),
                NoOpContentGuardrail.noOp(),
                router);
        engine.setCircuitBreaker(breaker);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hello");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(30, TimeUnit.SECONDS);

        assertEquals(AgentExecStatus.completed, result.getStatus(),
                "end-to-end: circuit-OPEN primary must be resolved to the fallback and complete");
        assertEquals(0, primaryCallCount.get(),
                "the circuit-OPEN primary must NEVER be called (resolution skipped it)");
        assertTrue(fallbackCallCount.get() >= 1,
                "the circuit-closed fallback must have been called at least once");
        assertEquals("p:fallback-ok", lastModel.get(),
                "the final LLM call must use the fallback model");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static IToolManager noOpToolManager() {
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
                AiToolModel m = new AiToolModel();
                m.setName(toolName);
                m.setDescription("stub");
                return m;
            }
        };
    }
}
