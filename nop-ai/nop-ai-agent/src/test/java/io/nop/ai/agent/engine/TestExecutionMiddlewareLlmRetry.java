package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.hook.HookContext;
import io.nop.ai.agent.hook.HookResult;
import io.nop.ai.agent.middleware.AttemptContext;
import io.nop.ai.agent.middleware.ExecutionPoint;
import io.nop.ai.agent.middleware.IAgentMiddleware;
import io.nop.ai.agent.middleware.MiddlewareChain;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.AlwaysClosed;
import io.nop.ai.agent.reliability.NoRetryPolicy;
import io.nop.ai.agent.reliability.StandardRetryPolicy;
import io.nop.ai.agent.router.PassThroughModelRouter;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W3-1 (Phase 2): end-to-end tests for execution-level (per-attempt) middleware
 * inside {@link LlmCallCoordinator#doLlmCallWithRetry}. Verifies (items 2.1-2.5):
 * <ul>
 *   <li>2.1: PRE/POST_LLM_ATTEMPT fire around each {@code callChatWithTimeout}</li>
 *   <li>2.2: first attempt triggers execution middleware (before/after)</li>
 *   <li>2.3: retry re-triggers execution middleware (attempt N+1 re-evaluates,
 *       carrying retry signal + last attempt classification via AttemptContext)</li>
 *   <li>2.4: execution middleware Veto maps to retry decision per D3 (veto cap
 *       prevents infinite loop; veto does not record circuit failure)</li>
 *   <li>2.5: orthogonal coexistence with W2e error classification + retry</li>
 * </ul>
 *
 * <p>Wiring check (Rule #23): the coordinator actually invokes the execution
 * middleware inside the retry loop (not just the type existing), proven by the
 * recording middleware observing attempt-by-attempt firing.
 */
public class TestExecutionMiddlewareLlmRetry {

    /** Records every PRE/POST firing with the AttemptContext it saw. */
    static class RecordingExecMiddleware implements IAgentMiddleware {
        final ExecutionPoint point;
        final List<String> log;
        final List<AttemptContext> seenCtx;

        RecordingExecMiddleware(ExecutionPoint point, List<String> log, List<AttemptContext> seenCtx) {
            this.point = point;
            this.log = log;
            this.seenCtx = seenCtx;
        }

        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            AttemptContext ac = ctx.getAttemptContext();
            seenCtx.add(ac);
            log.add(point.name() + "@attempt=" + (ac != null ? ac.getAttempt() : "?")
                    + (ac != null && ac.isRetry() ? "(retry,lastClass=" + ac.getLastErrorClassification() + ")" : ""));
            return next.proceed(ctx);
        }
    }

    /** Vetoes on a specified attempt number (for veto tests). */
    static class VetoOnAttempt implements IAgentMiddleware {
        final ExecutionPoint point;
        final int vetoAtAttempt; // -1 = always veto
        final AtomicInteger fireCount = new AtomicInteger();

        VetoOnAttempt(ExecutionPoint point, int vetoAtAttempt) {
            this.point = point;
            this.vetoAtAttempt = vetoAtAttempt;
        }

        @Override
        public HookResult execute(HookContext ctx, MiddlewareChain next) {
            int n = fireCount.incrementAndGet();
            AttemptContext ac = ctx.getAttemptContext();
            int attempt = ac != null ? ac.getAttempt() : -1;
            if (vetoAtAttempt < 0 || attempt == vetoAtAttempt) {
                return new HookResult.VetoResult("veto@fire" + n + "/attempt" + attempt);
            }
            return next.proceed(ctx);
        }
    }

    private static ChatResponse successResponse() {
        ChatResponse r = new ChatResponse();
        ChatAssistantMessage msg = new ChatAssistantMessage();
        msg.setContent("ok");
        r.setMessage(msg);
        return r;
    }

    private static ChatResponse errorResponse(ErrorClassification classification) {
        return ChatResponse.error(classification, 500, "err", "boom", null);
    }

    /** Chat service whose first N calls throw transient, then succeed. */
    static class FlakyChatService implements IChatService {
        final AtomicInteger calls = new AtomicInteger();
        int failFirstN;
        ErrorClassification failClassification = ErrorClassification.TRANSIENT;

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int n = calls.incrementAndGet();
            if (n <= failFirstN) {
                return errorResponse(failClassification);
            }
            return successResponse();
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext c = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        c.setStatus(AgentExecStatus.running);
        return c;
    }

    private static LlmCallCoordinator coordinatorWith(DefaultHookRegistry registry,
                                                       IChatService chat,
                                                       io.nop.ai.agent.reliability.IRetryPolicy policy) {
        AgentHookInvoker invoker = new AgentHookInvoker(registry, null);
        return new LlmCallCoordinator(
                chat, policy, AlwaysClosed.alwaysClosed(),
                PassThroughModelRouter.passThrough(), 0, null, invoker);
    }

    private static ChatRequest request() {
        ChatOptions options = ChatOptions.builder().provider("p").model("m").build();
        ChatRequest req = new ChatRequest(new ArrayList<>());
        req.setOptions(options);
        return req;
    }

    // ============ 2.1 + 2.2: PRE/POST fire around each attempt on first success ============

    @Test
    void preAndPostLlmAttemptFireAroundSingleSuccessfulCall() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        List<AttemptContext> preCtx = new ArrayList<>();
        List<AttemptContext> postCtx = new ArrayList<>();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT,
                new RecordingExecMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, log, preCtx));
        registry.registerExecutionMiddleware(ExecutionPoint.POST_LLM_ATTEMPT,
                new RecordingExecMiddleware(ExecutionPoint.POST_LLM_ATTEMPT, log, postCtx));

        FlakyChatService chat = new FlakyChatService(); // succeeds first call
        LlmCallCoordinator coordinator = coordinatorWith(registry, chat, new StandardRetryPolicy());

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build());

        assertTrue(result.isSuccess());
        assertEquals(1, chat.calls.get(), "exactly one LLM attempt");
        // Wiring (Rule #23): both PRE and POST fired exactly once, around the single call
        assertEquals(2, log.size());
        assertEquals("PRE_LLM_ATTEMPT@attempt=0", log.get(0));
        assertEquals("POST_LLM_ATTEMPT@attempt=0", log.get(1));
        // First attempt is not a retry; no prior classification
        assertEquals(0, preCtx.get(0).getAttempt());
        assertFalse(preCtx.get(0).isRetry());
    }

    // ============ 2.3: retry re-triggers execution middleware with retry signal + last classification ============

    @Test
    void retryReEvaluatesExecutionMiddlewareCarryingRetrySignalAndLastClassification() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        List<AttemptContext> preCtxs = new ArrayList<>();
        List<AttemptContext> postCtxs = new ArrayList<>();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT,
                new RecordingExecMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, log, preCtxs));
        registry.registerExecutionMiddleware(ExecutionPoint.POST_LLM_ATTEMPT,
                new RecordingExecMiddleware(ExecutionPoint.POST_LLM_ATTEMPT, log, postCtxs));

        FlakyChatService chat = new FlakyChatService();
        chat.failFirstN = 2; // attempt 0 and 1 fail TRANSIENT; attempt 2 succeeds
        // StandardRetryPolicy retries TRANSIENT
        LlmCallCoordinator coordinator = coordinatorWith(registry, chat, new StandardRetryPolicy(5, 1L, 5L));

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build());

        assertTrue(result.isSuccess());
        assertEquals(3, chat.calls.get(), "3 attempts: fail, fail, succeed");

        // CORE VALUE (W3-1): execution middleware re-fired on EVERY attempt,
        // including retries. PRE fires 3 times, POST fires 3 times.
        assertEquals(3, preCtxs.size(), "PRE_LLM_ATTEMPT must fire once per attempt (3 attempts)");
        assertEquals(3, postCtxs.size(), "POST_LLM_ATTEMPT must fire once per attempt (3 attempts)");

        // Attempt 0: not a retry, no last classification
        assertFalse(preCtxs.get(0).isRetry());
        // Attempt 1: IS a retry, carries attempt 0's TRANSIENT classification
        assertTrue(preCtxs.get(1).isRetry(), "attempt 1 must be flagged as retry");
        assertEquals(1, preCtxs.get(1).getAttempt());
        assertEquals(ErrorClassification.TRANSIENT, preCtxs.get(1).getLastErrorClassification(),
                "retry attempt must see previous attempt's error classification (re-evaluation input)");
        // Attempt 2: IS a retry, carries attempt 1's TRANSIENT classification
        assertTrue(preCtxs.get(2).isRetry());
        assertEquals(ErrorClassification.TRANSIENT, preCtxs.get(2).getLastErrorClassification());
    }

    // ============ 2.4: PRE_LLM_ATTEMPT Veto → retry decision (STOP on NON_TRANSIENT with NoRetryPolicy) ============

    @Test
    void preLlmAttemptVetoRoutesToRetryDecisionAndStopsWithNoRetryPolicy() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, new VetoOnAttempt(ExecutionPoint.PRE_LLM_ATTEMPT, -1));

        FlakyChatService chat = new FlakyChatService(); // would succeed, but never called due to veto
        // NoRetryPolicy: NON_TRANSIENT → STOP
        LlmCallCoordinator coordinator = coordinatorWith(registry, chat, NoRetryPolicy.noRetry());

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build());

        // Veto → NON_TRANSIENT → NoRetryPolicy STOP → terminal failure result
        assertFalse(result.isSuccess(), "veto + STOP policy must terminate as failure");
        assertEquals(0, chat.calls.get(),
                "PRE_LLM_ATTEMPT veto must skip the actual call (callChatWithTimeout never invoked)");
        assertNotNull(result.response.getError());
        assertTrue(result.response.getError().contains("vetoed"));
    }

    // ============ 2.4: POST_LLM_ATTEMPT Veto rejects a successful response ============

    @Test
    void postLlmAttemptVetoRejectsSuccessfulResponseAndStops() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerExecutionMiddleware(ExecutionPoint.POST_LLM_ATTEMPT,
                new VetoOnAttempt(ExecutionPoint.POST_LLM_ATTEMPT, -1));

        FlakyChatService chat = new FlakyChatService(); // returns success
        LlmCallCoordinator coordinator = coordinatorWith(registry, chat, NoRetryPolicy.noRetry());

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build());

        // The call ran (POST fires after the call), returned success, but POST veto rejected it.
        assertFalse(result.isSuccess(), "POST veto must reject the successful response");
        assertEquals(1, chat.calls.get(), "POST fires after the call, so the call must have run once");
        assertTrue(result.response.getError().contains("vetoed"));
    }

    // ============ 2.4: veto cap prevents infinite loop when policy retries NON_TRANSIENT ============

    @Test
    void vetoCapPreventsInfiniteLoopAndFailsLoud() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        // Always-veto PRE middleware
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, new VetoOnAttempt(ExecutionPoint.PRE_LLM_ATTEMPT, -1));

        FlakyChatService chat = new FlakyChatService();
        // A policy that ALWAYS retries (even NON_TRANSIENT) — would loop forever without the cap.
        // We emulate by a policy with high maxAttempts; NON_TRANSIENT default-stops in StandardRetryPolicy,
        // so use TRANSIENT-flavoured behaviour via a custom policy that always returns RETRY.
        io.nop.ai.agent.reliability.IRetryPolicy alwaysRetry = retryCtx ->
                io.nop.ai.agent.reliability.RetryOutcome.retryAfter(0L);
        LlmCallCoordinator coordinator = coordinatorWith(registry, chat, alwaysRetry);

        // The veto cap (MAX_EXECUTION_VETOES = 3) must force fail-loud.
        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build()));
        assertTrue(ex.getMessage().contains("veto cap"),
                "exceeding the veto cap must fail-loud with a cap message, got: " + ex.getMessage());
        assertEquals(0, chat.calls.get(), "PRE veto always skips the call");
    }

    // ============ 2.4: veto does NOT record circuit failure (veto != model failure) ============

    @Test
    void vetoDoesNotRecordCircuitBreakerFailure() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, new VetoOnAttempt(ExecutionPoint.PRE_LLM_ATTEMPT, -1));

        // A circuit breaker that throws if recordFailure is ever called, so we can detect it.
        io.nop.ai.agent.reliability.ICircuitBreaker detector = new io.nop.ai.agent.reliability.ICircuitBreaker() {
            @Override
            public boolean allowCall(String modelKey) {
                return true;
            }

            @Override
            public void recordFailure(String modelKey) {
                throw new AssertionError("veto must NOT record circuit failure, but recordFailure was called for " + modelKey);
            }

            @Override
            public void recordSuccess(String modelKey) {
            }

            @Override
            public io.nop.ai.agent.reliability.CircuitState getState(String modelKey) {
                return io.nop.ai.agent.reliability.CircuitState.CLOSED;
            }
        };
        AgentHookInvoker invoker = new AgentHookInvoker(registry, null);
        LlmCallCoordinator coordinator = new LlmCallCoordinator(
                new FlakyChatService(), NoRetryPolicy.noRetry(), detector,
                PassThroughModelRouter.passThrough(), 0, null, invoker);

        // No assertion error thrown => recordFailure was not called on the veto path.
        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build());
        assertFalse(result.isSuccess());
    }

    // ============ 2.5: orthogonal coexistence — execution middleware + W2e error classification + retry ============

    @Test
    void executionMiddlewareCoexistsWithErrorClassificationAndRetry() {
        DefaultHookRegistry registry = new DefaultHookRegistry();
        List<String> log = new ArrayList<>();
        List<AttemptContext> preCtxs = new ArrayList<>();
        registry.registerExecutionMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT,
                new RecordingExecMiddleware(ExecutionPoint.PRE_LLM_ATTEMPT, log, preCtxs));

        FlakyChatService chat = new FlakyChatService();
        chat.failFirstN = 1; // attempt 0 TRANSIENT fail, attempt 1 success
        // StandardRetryPolicy handles TRANSIENT retry (W2e error-classification path)
        LlmCallCoordinator coordinator = coordinatorWith(registry, chat, new StandardRetryPolicy(3, 1L, 5L));

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build());

        // Both layers coexisted: execution middleware fired per-attempt AND the W2e
        // retry-on-TRANSIENT path drove the actual retry. No conflict.
        assertTrue(result.isSuccess());
        assertEquals(2, chat.calls.get());
        // Execution middleware fired on both attempts (orthogonal, not suppressed by retry)
        assertEquals(2, preCtxs.size());
        assertFalse(preCtxs.get(0).isRetry());
        assertTrue(preCtxs.get(1).isRetry());
        assertEquals(ErrorClassification.TRANSIENT, preCtxs.get(1).getLastErrorClassification());
    }

    // ============ zero-overhead: no execution middleware => existing path unchanged ============

    @Test
    void noExecutionMiddlewareMeansExistingRetryPathUnchanged() {
        DefaultHookRegistry registry = new DefaultHookRegistry(); // no execution middleware
        FlakyChatService chat = new FlakyChatService();
        chat.failFirstN = 1;
        LlmCallCoordinator coordinator = coordinatorWith(registry, chat, new StandardRetryPolicy(3, 1L, 5L));

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request(), ctx(), "s1", "agent",
                        ChatOptions.builder().provider("p").model("m").build());

        assertTrue(result.isSuccess());
        assertEquals(2, chat.calls.get(), "retry path unchanged when no execution middleware registered");
    }
}
