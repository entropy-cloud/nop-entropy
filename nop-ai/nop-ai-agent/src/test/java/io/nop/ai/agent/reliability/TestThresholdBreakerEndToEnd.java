package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
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
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 210 (L3-1) Phase 2 end-to-end + wiring test (Minimum Rules #22
 * end-to-end, #23 wiring, #24 no silent skip, #25 new feature coverage).
 *
 * <p>Proves the full path: {@code DefaultAgentEngine.execute()} → ReAct loop
 * → single-LLM-call retry-loop outer check → {@link ThresholdBreaker} →
 * consecutive failures (across execute() calls, since the breaker is an
 * engine-level field whose state persists across executions) trip the breaker
 * to OPEN → a subsequent execute()'s outer check rejects the call before
 * reaching {@code chatService}.
 *
 * <p>The breaker state is per-instance and accumulates across execute()
 * calls on the same engine (design §5.1: in-memory per-breaker-instance
 * state). With NoRetryPolicy each execute() issues exactly one LLM call that
 * fails and breaks the ReAct loop, so consecutive failures are observed
 * across multiple execute() invocations sharing the same engine + breaker.
 *
 * <p>Three scenarios:
 * <ol>
 *   <li><b>End-to-end trip + reject</b>: with ThresholdBreaker(threshold=2),
 *       two execute() calls each fail at the LLM call (recording one failure
 *       each → breaker trips to OPEN on the 2nd). A third execute()'s outer
 *       check rejects the call (0 additional chatService calls) and the
 *       execution surfaces as failed with an error mentioning OPEN.</li>
 *   <li><b>Pre-tripped breaker rejects before chatService</b>: the breaker is
 *       pre-tripped manually, then a single execute() is rejected at the
 *       outer check (0 chatService calls).</li>
 *   <li><b>Zero-regression with AlwaysClosed default</b>: the same scenario
 *       with the shipped default never trips and the failure does not
 *       mention OPEN (confirming the ThresholdBreaker path is opt-in).</li>
 * </ol>
 */
public class TestThresholdBreakerEndToEnd {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // The test-react-agent.agent.xml declares provider=test-provider,
    // model=test-model. buildModelKey normalizes to "test-provider:test-model".
    private static final String EXPECTED_MODEL_KEY = "test-provider:test-model";

    // ========================================================================
    // End-to-end: consecutive failures (across execute() calls) trip the
    // breaker; a subsequent execute() rejects the call before chatService
    // (Minimum Rules #22, #23)
    // ========================================================================

    @Test
    void consecutiveFailuresAcrossExecutionsTripAndReject() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        NopTimeoutException toThrow = new NopTimeoutException();
        IChatService chatService = new CountingThrowingChatService(callCount, toThrow);

        DefaultAgentEngine engine = newEngine(chatService);
        // threshold=2: after 2 consecutive failures the breaker trips to OPEN.
        ThresholdBreaker breaker = new ThresholdBreaker(2, 60_000L);
        engine.setCircuitBreaker(breaker);

        // Execute() #1: one LLM call → throws → recordFailure (failures=1,
        // still CLOSED) → execute fails with the LLM error (not circuit).
        AgentExecutionResult r1 = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi")).get(30, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.failed, r1.getStatus(),
                "First execute() must fail with the LLM error");
        assertEquals(CircuitState.CLOSED, breaker.getState(EXPECTED_MODEL_KEY),
                "After 1 failure (threshold=2), breaker must still be CLOSED");
        assertEquals(1, callCount.get(), "First execute() must issue exactly 1 chatService call");

        // Execute() #2: one LLM call → throws → recordFailure (failures=2,
        // trips to OPEN) → execute fails with the LLM error (the call was
        // admitted before the breaker tripped; the breaker trips in the catch).
        AgentExecutionResult r2 = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi")).get(30, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.failed, r2.getStatus(),
                "Second execute() must fail with the LLM error");
        // Wiring proof (Minimum Rules #23): the breaker is now OPEN for the
        // primary model key — proving the retry-loop outer check's
        // recordFailure calls were consumed by the registered breaker at
        // runtime.
        assertEquals(CircuitState.OPEN, breaker.getState(EXPECTED_MODEL_KEY),
                "After 2 consecutive failures (threshold=2), breaker must be OPEN");
        assertEquals(2, callCount.get(), "Second execute() must issue exactly 1 more call");

        // Execute() #3: the outer check rejects the call (breaker is OPEN)
        // → NopAiAgentException → execute fails with a circuit-rejection
        // error. chatService must NOT be called (callCount stays at 2).
        AgentExecutionResult r3 = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi")).get(30, TimeUnit.SECONDS);
        assertEquals(AgentExecStatus.failed, r3.getStatus(),
                "Third execute() must fail because the circuit is OPEN (no silent skip)");
        assertEquals(2, callCount.get(),
                "The OPEN breaker must reject the call BEFORE reaching chatService "
                        + "(callCount must stay at 2, got " + callCount.get() + ")");
        // No silent skip (Minimum Rules #24): the failure error must mention
        // the OPEN state so the operator can see why the call was rejected.
        assertNotNull(r3.getError(),
                "A circuit-rejected execution must record an error");
        assertTrue(r3.getError().contains("OPEN"),
                "The circuit-rejection error must mention OPEN. Error was: "
                        + r3.getError());
    }

    // ========================================================================
    // Pre-tripped breaker rejects the call before reaching chatService
    // ========================================================================

    @Test
    void preTrippedBreakerRejectsBeforeChatService() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("done");
        ChatResponse success = ChatResponse.success(msg);
        success.setRequestId("req-ok");
        IChatService chatService = new CountingSuccessChatService(callCount, success);

        DefaultAgentEngine engine = newEngine(chatService);
        ThresholdBreaker breaker = new ThresholdBreaker(1, 60_000L);
        engine.setCircuitBreaker(breaker);
        // Pre-trip the breaker on the exact key the engine will route to.
        breaker.recordFailure(EXPECTED_MODEL_KEY); // threshold=1 → OPEN
        assertEquals(CircuitState.OPEN, breaker.getState(EXPECTED_MODEL_KEY));

        AgentExecutionResult result = engine.execute(
                new AgentMessageRequest("test-react-agent", "hi")).get(30, TimeUnit.SECONDS);

        // Anti-hollow: chatService was NOT called.
        assertEquals(0, callCount.get(),
                "A pre-tripped breaker must reject the call BEFORE reaching chatService "
                        + "(got " + callCount.get() + " calls)");
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "A circuit-rejected call must fail the execution (no silent skip)");
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("OPEN"),
                "The failure error must mention OPEN. Error was: " + result.getError());
    }

    // ========================================================================
    // Zero-regression: AlwaysClosed default never trips on the same scenario
    // ========================================================================

    @Test
    void alwaysClosedDefaultNeverTrips() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        NopTimeoutException toThrow = new NopTimeoutException();
        IChatService chatService = new CountingThrowingChatService(callCount, toThrow);

        DefaultAgentEngine engine = newEngine(chatService);
        // Shipped default (AlwaysClosed) — the breaker never trips.
        // Verify the default is AlwaysClosed.
        assertTrue(engine.getCircuitBreaker() instanceof AlwaysClosed);

        // Multiple execute() calls accumulate failures but the breaker
        // never trips (AlwaysClosed records no-ops).
        for (int i = 0; i < 5; i++) {
            AgentExecutionResult r = engine.execute(
                    new AgentMessageRequest("test-react-agent", "hi")).get(30, TimeUnit.SECONDS);
            assertEquals(AgentExecStatus.failed, r.getStatus(),
                    "Each execute() must fail with the LLM error (not circuit)");
            assertNotEquals(true, r.getError().contains("OPEN"),
                    "With AlwaysClosed default the failure must never be a circuit rejection. "
                            + "Error was: " + r.getError());
        }
        // Every execute() issued exactly one call (no circuit rejection).
        assertEquals(5, callCount.get(),
                "AlwaysClosed must allow every call (no circuit rejection across 5 executions)");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static DefaultAgentEngine newEngine(IChatService chatService) {
        return new DefaultAgentEngine(
                chatService, noOpToolManager(), new InMemorySessionStore(),
                new AllowAllPermissionProvider(),
                new AllowAllToolAccessChecker(),
                new AllowAllPathAccessChecker(),
                NoOpContentGuardrail.noOp());
    }

    private static final class CountingThrowingChatService implements IChatService {
        private final AtomicInteger callCount;
        private final RuntimeException toThrow;

        CountingThrowingChatService(AtomicInteger callCount, RuntimeException toThrow) {
            this.callCount = callCount;
            this.toThrow = toThrow;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            CompletableFuture<ChatResponse> f = new CompletableFuture<>();
            f.completeExceptionally(toThrow);
            return f;
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            throw toThrow;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static final class CountingSuccessChatService implements IChatService {
        private final AtomicInteger callCount;
        private final ChatResponse success;

        CountingSuccessChatService(AtomicInteger callCount, ChatResponse success) {
            this.callCount = callCount;
            this.success = success;
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(success);
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return success;
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> {
            };
        }
    }

    private static IToolManager noOpToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return null;
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
}
