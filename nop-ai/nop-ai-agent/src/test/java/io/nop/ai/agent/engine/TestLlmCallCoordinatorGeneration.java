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
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ICircuitBreaker;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.ai.core.reliability.RetryOutcome;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-C7-005（plan 2556 裁定 G）wiring carrier: {@link LlmCallCoordinator}
 * captures the circuit-breaker generation via {@code currentGeneration(key)}
 * at the allowCall site on the FINAL routed model key, and passes it back at
 * every {@code recordSuccess(key, gen)} / {@code recordFailure(key, gen, err)}
 * site through the gen-aware overloads.
 *
 * <p>(The stale-generation DISCARD behaviour itself is pinned in nop-ai-core
 * by {@code TestProbeGenerationToken}; this carrier pins the caller-side
 * wiring: a generation IS passed and it IS the final routed key's generation.)
 */
public class TestLlmCallCoordinatorGeneration {

    private static final String PROVIDER_A = "provider-a";
    private static final String MODEL_A = "model-a";
    private static final String PROVIDER_B = "provider-b";
    private static final String MODEL_B = "model-b";

    /**
     * Fake breaker that records every record* invocation's (key, gen) pair so
     * the wiring can be asserted exactly.
     */
    static final class GenCapturingBreaker implements ICircuitBreaker {
        final Map<String, Long> generations = new ConcurrentHashMap<>();
        final List<String> successKeys = new CopyOnWriteArrayList<>();
        final List<Long> successGens = new CopyOnWriteArrayList<>();
        final List<String> failureKeys = new CopyOnWriteArrayList<>();
        final List<Long> failureGens = new CopyOnWriteArrayList<>();
        final List<Throwable> failureErrors = new CopyOnWriteArrayList<>();

        @Override
        public boolean allowCall(String modelKey) {
            return true;
        }

        @Override
        public CircuitState getState(String modelKey) {
            return CircuitState.CLOSED;
        }

        @Override
        public void recordSuccess(String modelKey) {
            recordSuccess(modelKey, -1L);
        }

        @Override
        public void recordFailure(String modelKey) {
            recordFailure(modelKey, -1L, null);
        }

        @Override
        public long currentGeneration(String modelKey) {
            return generations.getOrDefault(modelKey, 0L);
        }

        @Override
        public void recordSuccess(String modelKey, long gen) {
            successKeys.add(modelKey);
            successGens.add(gen);
        }

        @Override
        public void recordFailure(String modelKey, long gen, Throwable err) {
            failureKeys.add(modelKey);
            failureGens.add(gen);
            failureErrors.add(err);
        }
    }

    @Test
    void normalPathPassesCapturedGenerationAtFinalKey() {
        GenCapturingBreaker breaker = new GenCapturingBreaker();
        breaker.generations.put("provider-a:model-a", 7L);

        LlmCallCoordinator coordinator = newCoordinator(
                new AlwaysOkChatService(), ctx -> RetryOutcome.stop(), breaker, singleHopRouter());

        ChatOptions options = ChatOptions.builder()
                .provider(PROVIDER_A).model(MODEL_A).build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess());
        assertEquals(List.of("provider-a:model-a"), breaker.successKeys,
                "the success receipt must target the final routed model key");
        assertEquals(List.of(7L), breaker.successGens,
                "the success receipt must carry the generation captured at "
                        + "allowCall on the final routed model key (3.1-C7-005 裁定 G)");
        assertTrue(breaker.failureKeys.isEmpty(), "no failure receipt on the success path");
    }

    @Test
    void fallbackPathCarriesFailureGenOnOldKeyAndSuccessGenOnNewFinalKey() {
        GenCapturingBreaker breaker = new GenCapturingBreaker();
        breaker.generations.put("provider-a:model-a", 3L);
        breaker.generations.put("provider-b:model-b", 5L);

        LlmCallCoordinator coordinator = newCoordinator(
                new FirstTransientThenSuccessChatService(),
                ctx -> RetryOutcome.fallback(), breaker, singleHopRouter());

        ChatOptions options = ChatOptions.builder()
                .provider(PROVIDER_A).model(MODEL_A).build();
        ChatRequest request = new ChatRequest(new ArrayList<>());
        request.setOptions(options);

        LlmCallCoordinator.LlmCallResult result =
                coordinator.doLlmCallWithRetry(request, ctx(), "s1", "agent", options);

        assertTrue(result.isSuccess());
        // The failure receipt targets the ORIGINAL key with the generation
        // captured at that key's allowCall.
        assertEquals(List.of("provider-a:model-a"), breaker.failureKeys);
        assertEquals(List.of(3L), breaker.failureGens,
                "the failure receipt must carry the old key's captured generation");
        // The success receipt targets the FINAL routed key (post-FALLBACK) with
        // its re-captured generation — the final-key capture rule.
        assertEquals(List.of("provider-b:model-b"), breaker.successKeys,
                "the success receipt must target the FINAL routed model key");
        assertEquals(List.of(5L), breaker.successGens,
                "the success receipt must carry the FINAL routed key's generation "
                        + "(re-captured after the FALLBACK switch)");
    }

    // ---- helpers ------------------------------------------------------------

    private static LlmCallCoordinator newCoordinator(IChatService chat,
                                                     IRetryPolicy policy,
                                                     ICircuitBreaker breaker,
                                                     IModelRouter router) {
        AgentHookInvoker invoker = new AgentHookInvoker(new DefaultHookRegistry(), null);
        return new LlmCallCoordinator(chat, policy, breaker, router, 0, null, invoker);
    }

    private static AgentExecutionContext ctx() {
        AgentExecutionContext ctx = new AgentExecutionContext(new io.nop.ai.agent.model.AgentModel());
        ctx.setStatus(AgentExecStatus.running);
        return ctx;
    }

    /** route() passes through; getFallback() offers exactly one hop A → B. */
    private static IModelRouter singleHopRouter() {
        return new IModelRouter() {
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

    static final class AlwaysOkChatService implements IChatService {
        @Override
        public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
            return successResponse();
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(call(request, cancelToken));
        }

        @Override
        public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    /** First call fails TRANSIENT, subsequent calls succeed. */
    static final class FirstTransientThenSuccessChatService implements IChatService {
        private final AtomicInteger callCount = new AtomicInteger();

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
}
