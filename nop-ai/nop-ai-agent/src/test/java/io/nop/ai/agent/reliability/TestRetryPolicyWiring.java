package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.DefaultAgentEngine;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 207 (L3-2) Phase 1 wiring + zero-regression test (Minimum Rules #23
 * wiring, #24 no silent skip, #25 new feature coverage).
 *
 * <p>Three concerns:
 * <ol>
 *   <li><b>Engine wiring</b>: {@link DefaultAgentEngine} defaults to
 *       {@link NoRetryPolicy}; {@code setRetryPolicy} overrides the default;
 *       null setter falls back to NoRetryPolicy.</li>
 *   <li><b>Zero-regression (default NoRetry)</b>: with the shipped default,
 *       a throwing {@code chatService.call(...)} executes exactly once and
 *       the execution surfaces as failed (status=failed, error recorded) —
 *       the same terminal state as the pre-plan-207 bare call. Proves the
 *       retry loop is wired but dormant (no behaviour change).</li>
 *   <li><b>No silent skip (FALLBACK fail-loud)</b>: a policy returning
 *       FALLBACK at runtime surfaces a {@code NopAiAgentException} whose
 *       message records the FALLBACK decision (the failure is recorded in
 *       the execution result's error — no silent swallow, Minimum Rules #24).</li>
 * </ol>
 */
public class TestRetryPolicyWiring {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ========================================================================
    // Engine wiring: default NoRetry + setter override + null fallback
    // ========================================================================

    @Test
    void engineDefaultsToNoRetryPolicy() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        IRetryPolicy policy = engine.getRetryPolicy();
        assertNotNull(policy, "Engine must default to a non-null retry policy");
        assertTrue(policy instanceof NoRetryPolicy,
                "Shipped default must be the NoRetryPolicy pass-through");
    }

    @Test
    void setRetryPolicyOverridesDefault() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        IRetryPolicy custom = ctx -> RetryOutcome.stop();
        engine.setRetryPolicy(custom);
        assertSame(custom, engine.getRetryPolicy(),
                "getRetryPolicy must return the exact instance set via setRetryPolicy");
    }

    @Test
    void setRetryPolicyNullFallsBackToNoRetry() {
        DefaultAgentEngine engine = new DefaultAgentEngine(noOpChatService(), noOpToolManager());
        engine.setRetryPolicy(null);
        IRetryPolicy policy = engine.getRetryPolicy();
        assertNotNull(policy, "null setter must fall back to a non-null NoRetry default");
        assertTrue(policy instanceof NoRetryPolicy);
    }

    // ========================================================================
    // Zero-regression: default NoRetry executes the call once and surfaces
    // failure (Minimum Rules #23: wiring verified by call-count == 1)
    // ========================================================================

    @Test
    void defaultNoRetryExecutesCallExactlyOnceAndSurfacesFailure() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        // The chat service throws a transient-looking exception. With the
        // shipped NoRetryPolicy default the retry loop must execute the call
        // exactly once and surface the failure (zero-regression: the
        // pre-plan-207 behaviour was a bare chatService.call with no retry,
        // caught by execute()'s catch block → status=failed).
        NopTimeoutException toThrow = new NopTimeoutException();
        IChatService chatService = new CountingThrowingChatService(callCount, toThrow);

        DefaultAgentEngine engine = newEngine(chatService);

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(15, TimeUnit.SECONDS);

        // Wiring proof: the LLM call was executed exactly once (NoRetry →
        // no retry attempt). This is the core anti-hollow assertion.
        assertEquals(1, callCount.get(),
                "NoRetryPolicy must execute the LLM call exactly once (no retry) — "
                        + "proves the retry loop is wired and consuming the policy");
        // Zero-regression: the execution surfaces as failed (same terminal
        // state as pre-plan-207 bare call).
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "With NoRetry default the LLM failure must surface as status=failed "
                        + "(zero-regression: identical terminal state to pre-plan-207)");
        assertNotNull(result.getError(),
                "The execution result must carry the failure error (not silently swallowed)");
    }

    // ========================================================================
    // No silent skip: FALLBACK decision fails loud at runtime (Minimum Rules #24)
    // ========================================================================

    @Test
    void fallbackDecisionFailsLoudAtRuntime() throws Exception {
        ResourceComponentManager.instance().loadComponentModel("/test-react-agent.agent.xml");

        AtomicInteger callCount = new AtomicInteger(0);
        NopTimeoutException toThrow = new NopTimeoutException();
        IChatService chatService = new CountingThrowingChatService(callCount, toThrow);

        DefaultAgentEngine engine = newEngine(chatService);
        // Policy that returns FALLBACK for any error. No fallback model chain
        // is wired in this plan (Non-Goal), so the retry loop must fail loud
        // (NopAiAgentException mentioning FALLBACK) rather than silently
        // continuing (Minimum Rules #24: no silent skip).
        engine.setRetryPolicy(ctx -> RetryOutcome.fallback());

        AgentMessageRequest request = new AgentMessageRequest("test-react-agent", "hi");
        CompletableFuture<AgentExecutionResult> future = engine.execute(request);
        AgentExecutionResult result = future.get(15, TimeUnit.SECONDS);

        assertEquals(1, callCount.get(),
                "FALLBACK stops immediately after the first failed call (no retry)");
        assertEquals(AgentExecStatus.failed, result.getStatus(),
                "FALLBACK with no fallback chain must fail the execution");
        // The recorded error must surface the FALLBACK decision (no silent
        // swallow — the operator can see WHY execution stopped).
        assertNotNull(result.getError(),
                "FALLBACK failure must be recorded in the execution result error");
        assertTrue(result.getError().contains("FALLBACK"),
                "FALLBACK failure error must mention FALLBACK so the operator can see "
                        + "why execution stopped (no silent skip). Error was: "
                        + result.getError());
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

    /**
     * Chat service that increments a counter then throws a fixed exception on
     * every call. Used to prove the retry loop calls it the expected number of
     * times.
     */
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

    private static IChatService noOpChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return null;
            }
        };
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
