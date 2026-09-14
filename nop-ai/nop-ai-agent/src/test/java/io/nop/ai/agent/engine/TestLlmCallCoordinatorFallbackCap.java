package io.nop.ai.agent.engine;

import io.nop.ai.agent.hook.DefaultHookRegistry;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.reliability.AlwaysClosed;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.ai.core.reliability.RetryContext;
import io.nop.ai.core.reliability.RetryOutcome;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-REL regression test: the {@link LlmCallCoordinator} FALLBACK loop has a
 * total step cap ({@code MAX_FALLBACK_STEPS}) — a custom {@link IModelRouter}
 * whose {@code getFallback} cycles A→B→A (or an account/failover chain that
 * cycles) must fail loud instead of issuing real LLM calls forever.
 *
 * <p>Harness: direct {@link LlmCallCoordinator} construction (same as
 * {@code TestAccountFallbackChain}), fake {@link IChatService} returning a
 * TRANSIENT error response on every call, a cycling router, and a retry
 * policy that always returns FALLBACK for TRANSIENT.
 */
public class TestLlmCallCoordinatorFallbackCap {

    private static final String PROVIDER_A = "provider-a";
    private static final String MODEL_A = "model-a";
    private static final String PROVIDER_B = "provider-b";
    private static final String MODEL_B = "model-b";

    // ========================================================================
    // A→B→A cycling router: the FALLBACK loop must terminate fail-loud
    // ========================================================================

    @Test
    void cyclingFallbackRouterFailsLoudWithinCap() {
        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chat = new TransientErrorChatService(callCount);
        // Router cycles A→B→A forever (never returns null → pre-P2-REL while(true)).
        IModelRouter cyclingRouter = new CyclingRouter();
        IRetryPolicy alwaysFallback = context -> RetryOutcome.fallback();

        LlmCallCoordinator coordinator = newCoordinator(chat, alwaysFallback, cyclingRouter);

        ChatOptions options = ChatOptions.builder()
                .provider(PROVIDER_A).model(MODEL_A).build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                        coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options),
                "a cycling A->B->A FALLBACK loop must terminate fail-loud (not spin forever)");
        assertTrue(ex.getMessage().contains("FALLBACK step cap"),
                "fail-loud error must identify the FALLBACK step cap. Was: " + ex.getMessage());
        // Bounded: initial call + at most MAX_FALLBACK_STEPS switched attempts.
        assertTrue(callCount.get() <= LlmCallCoordinator.MAX_FALLBACK_STEPS + 1,
                "LLM call count must be bounded by the cap (got " + callCount.get()
                        + ", cap " + LlmCallCoordinator.MAX_FALLBACK_STEPS + ")");
    }

    // ========================================================================
    // Normal single FALLBACK must be unaffected (zero regression)
    // ========================================================================

    @Test
    void singleFallbackStillSucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);
        IChatService chat = new FirstTransientThenSuccessChatService(callCount);
        // A→B then null (chain exhausted after one fallback).
        IModelRouter singleStepRouter = new IModelRouter() {
            @Override
            public RoutingResult route(List<ChatMessage> messages, ChatOptions options,
                                       AgentExecutionContext ctx) {
                return new RoutingResult(options, "test", "test");
            }

            @Override
            public ChatOptions getFallback(ChatOptions currentOptions) {
                if (PROVIDER_A.equals(currentOptions.getProvider())) {
                    return ChatOptions.builder()
                            .provider(PROVIDER_B).model(MODEL_B).build();
                }
                return null;
            }
        };
        IRetryPolicy alwaysFallback = context -> RetryOutcome.fallback();

        LlmCallCoordinator coordinator = newCoordinator(chat, alwaysFallback, singleStepRouter);

        ChatOptions options = ChatOptions.builder()
                .provider(PROVIDER_A).model(MODEL_A).build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess(),
                "a single FALLBACK switch (A -> B) followed by success must not be affected by the cap");
        assertEquals(2, callCount.get(), "call sequence: A (transient) -> B (success)");
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static LlmCallCoordinator newCoordinator(IChatService chat,
                                                      IRetryPolicy policy,
                                                      IModelRouter router) {
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        return new LlmCallCoordinator(
                chat, policy, AlwaysClosed.alwaysClosed(), router, 0, null, invoker);
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        ctx.setStatus(AgentExecStatus.running);
        return ctx;
    }

    private static ChatResponse transientError() {
        return ChatResponse.error(ErrorClassification.TRANSIENT, 503,
                "server_error", "unavailable", null);
    }

    private static ChatResponse successResponse() {
        ChatResponse r = new ChatResponse();
        io.nop.ai.api.chat.messages.ChatAssistantMessage msg =
                new io.nop.ai.api.chat.messages.ChatAssistantMessage();
        msg.setContent("ok");
        r.addMessage(msg);
        return r;
    }

    /** Always returns a TRANSIENT error response (the FALLBACK decision never succeeds). */
    private static final class TransientErrorChatService implements IChatService {
        private final AtomicInteger callCount;

        TransientErrorChatService(AtomicInteger callCount) {
            this.callCount = callCount;
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return transientError();
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            callCount.incrementAndGet();
            return CompletableFuture.completedFuture(transientError());
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    /** First call fails TRANSIENT, subsequent calls succeed (single-fallback scenario). */
    private static final class FirstTransientThenSuccessChatService implements IChatService {
        private final AtomicInteger callCount;

        FirstTransientThenSuccessChatService(AtomicInteger callCount) {
            this.callCount = callCount;
        }

        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            int n = callCount.incrementAndGet();
            return n == 1 ? transientError() : successResponse();
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            int n = callCount.incrementAndGet();
            return CompletableFuture.completedFuture(n == 1 ? transientError() : successResponse());
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    /** getFallback cycles A→B→A forever (never returns null). */
    private static final class CyclingRouter implements IModelRouter {
        @Override
        public RoutingResult route(List<ChatMessage> messages, ChatOptions options,
                                   AgentExecutionContext ctx) {
            return new RoutingResult(options, "test", "test");
        }

        @Override
        public ChatOptions getFallback(ChatOptions currentOptions) {
            if (PROVIDER_A.equals(currentOptions.getProvider())) {
                return ChatOptions.builder()
                        .provider(PROVIDER_B).model(MODEL_B).build();
            }
            return ChatOptions.builder()
                    .provider(PROVIDER_A).model(MODEL_A).build();
        }
    }
}