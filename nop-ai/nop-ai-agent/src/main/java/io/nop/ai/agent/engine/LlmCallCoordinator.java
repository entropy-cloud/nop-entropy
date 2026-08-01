package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.reliability.CircuitState;
import io.nop.ai.agent.reliability.ICircuitBreaker;
import io.nop.ai.agent.reliability.IRetryPolicy;
import io.nop.ai.agent.reliability.LlmErrorClassifier;
import io.nop.ai.agent.reliability.RetryContext;
import io.nop.ai.agent.reliability.RetryOutcome;
import io.nop.ai.agent.router.IModelRouter;
import io.nop.ai.agent.router.RoutingResult;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LLM-call lifecycle coordinator for the ReAct loop (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Owns the circuit-breaker check,
 * the retry loop (RETRY / STOP / FALLBACK), the wall-clock timeout wrapper
 * and the model-identity key building used for model-switched detection.
 * Failure handling delegates to the injected {@link AgentHookInvoker}
 * (on-error hooks + execution-failed event publication).
 */
public class LlmCallCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(LlmCallCoordinator.class);

    private final IChatService chatService;
    private final IRetryPolicy retryPolicy;
    private final ICircuitBreaker circuitBreaker;
    private final IModelRouter modelRouter;
    private final long llmTimeoutMs;
    private final Executor timeoutExecutor;
    private final AgentHookInvoker hookInvoker;

    public LlmCallCoordinator(IChatService chatService, IRetryPolicy retryPolicy,
                              ICircuitBreaker circuitBreaker, IModelRouter modelRouter,
                              long llmTimeoutMs, Executor timeoutExecutor,
                              AgentHookInvoker hookInvoker) {
        this.chatService = chatService;
        this.retryPolicy = retryPolicy;
        this.circuitBreaker = circuitBreaker;
        this.modelRouter = modelRouter;
        this.llmTimeoutMs = llmTimeoutMs;
        this.timeoutExecutor = timeoutExecutor;
        this.hookInvoker = hookInvoker;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    /**
     * Plan 304: extract the LLM call with circuit-breaker check and retry
     * loop into its own method so the reactLoop body delegates to a named
     * step. Returns an LlmCallResult holding the response, the final
     * routedOptions (which may have been reassigned by a FALLBACK switch),
     * the llmCallStart timestamp, and a success flag.
     */
    public LlmCallResult doLlmCallWithRetry(ChatRequest request,
                                             AgentExecutionContext ctx,
                                             String sessionId,
                                             String agentName,
                                             ChatOptions routedOptions) {
        String primaryModelKey = buildModelKey(routedOptions);
        if (!circuitBreaker.allowCall(primaryModelKey)) {
            CircuitState rejectedState = circuitBreaker.getState(primaryModelKey);
            LOG.warn("Circuit breaker rejected LLM call for model {} (state={}); "
                            + "failing fast. session={}", primaryModelKey, rejectedState, sessionId);
            throw new NopAiAgentException(
                    "Circuit breaker is " + rejectedState + " for model "
                            + primaryModelKey + "; rejecting call to avoid wasting "
                            + "time/tokens on a consecutively-failing model. Configure "
                            + "an IModelRouter fallback chain or wait for the breaker "
                            + "cooldown before retrying.");
        }
        long llmCallStart = System.currentTimeMillis();
        ChatResponse response;
        {
            int attempt = 0;
            Throwable lastError = null;
            ChatResponse attemptResponse = null;
            while (true) {
                try {
                    llmCallStart = System.currentTimeMillis();
                    attemptResponse = callChatWithTimeout(request);
                    if (attemptResponse.isSuccess()) {
                        break; // genuine success
                    }
                    // 响应级错误（W2e-2/W2e-3）：ChatServiceImpl 已规范化为携带
                    // errorClassification 的错误 ChatResponse（非 2xx 不再抛异常）。
                    // 读分类进入重试决策——不再像旧实现那样一律终止。
                    circuitBreaker.recordFailure(buildModelKey(routedOptions));
                    ErrorClassification classification = attemptResponse.getErrorClassification();
                    if (classification == null) {
                        classification = ErrorClassification.NON_TRANSIENT;
                    }
                    RetryContext retryCtx = new RetryContext(attempt, null, classification,
                            false, attemptResponse.getRetryAfterMs());
                    RetryOutcome outcome = retryPolicy.shouldRetry(retryCtx);
                    if (outcome == null) {
                        throw new NopAiAgentException(
                                "retryPolicy.shouldRetry() returned null for classification="
                                        + classification + ", attempt=" + attempt);
                    }
                    if (outcome.isRetry()) {
                        LOG.warn("LLM call returned error response (classification={}, "
                                        + "attempt={}, httpStatus={}), retrying after {} ms",
                                classification, attempt, attemptResponse.getHttpStatus(),
                                outcome.getDelayMs());
                        attempt++;
                        sleepBackoff(outcome.getDelayMs());
                        continue;
                    }
                    if (outcome.isFallback()) {
                        routedOptions = doFallbackSwitch(routedOptions, request, attempt,
                                classification, null);
                        attempt = 0;
                        continue;
                    }
                    // STOP：错误响应不可重试（QUOTA/AUTH/NON_TRANSIENT 今日保持 STOP，
                    // 账号链延期）。退出循环，由下方 !isSuccess() 终止分支处理。
                    break;
                } catch (RuntimeException | Error ex) {
                    // 传输级错误（无 HTTP 响应）：仍走 LlmErrorClassifier 启发式。
                    circuitBreaker.recordFailure(buildModelKey(routedOptions));
                    lastError = ex;
                    ErrorClassification classification = LlmErrorClassifier.classify(ex);
                    RetryContext retryCtx = new RetryContext(
                            attempt, ex, classification, false, null);
                    RetryOutcome outcome = retryPolicy.shouldRetry(retryCtx);
                    if (outcome == null) {
                        throw new NopAiAgentException(
                                "retryPolicy.shouldRetry() returned null for classification="
                                        + classification + ", attempt=" + attempt, ex);
                    }
                    if (outcome.isRetry()) {
                        LOG.warn("LLM call failed (classification={}, attempt={}), "
                                        + "retrying after {} ms: {}",
                                classification, attempt, outcome.getDelayMs(),
                                ex.toString());
                        attempt++;
                        sleepBackoff(outcome.getDelayMs());
                        continue;
                    }
                    if (outcome.isFallback()) {
                        routedOptions = doFallbackSwitch(routedOptions, request, attempt,
                                classification, ex);
                        attempt = 0;
                        lastError = null;
                        continue;
                    }
                    if (lastError instanceof RuntimeException) {
                        throw (RuntimeException) lastError;
                    }
                    throw (Error) lastError;
                }
            }
            response = attemptResponse;
        }

        if (!response.isSuccess()) {
            // 错误响应重试耗尽 / 不可重试分类（QUOTA/AUTH/NON_TRANSIENT）：终止。
            // 失败已在循环内记录（circuitBreaker.recordFailure），此处仅终止 + 通知。
            ctx.setStatus(AgentExecStatus.failed);
            ctx.setLastError(response.getError());
            hookInvoker.invokeOnError(ctx, agentName);
            hookInvoker.publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName,
                    response.getError());
            return new LlmCallResult(response, routedOptions, llmCallStart, false);
        }

        circuitBreaker.recordSuccess(buildModelKey(routedOptions));
        return new LlmCallResult(response, routedOptions, llmCallStart, true);
    }

    /**
     * 执行模型 tier 回退切换（FALLBACK）。从 {@code modelRouter.getFallback(current)} 取
     * 下一个模型，更新 {@code request} 的 options，返回新的 routedOptions。无可用回退模型时
     * fail-loud（Minimum Rules #24，不静默跳过）。{@code ex} 可空（响应级错误无异常）。
     */
    private ChatOptions doFallbackSwitch(ChatOptions routedOptions, ChatRequest request,
                                         int attempt, ErrorClassification classification,
                                         Throwable ex) {
        ChatOptions fallbackOptions = modelRouter.getFallback(routedOptions);
        if (fallbackOptions == null) {
            LOG.error("LLM call retry policy returned FALLBACK at "
                    + "attempt={} (classification={}), but the model "
                    + "router provided no fallback model — stopping "
                    + "execution. Last error: {}",
                    attempt, classification, ex != null ? ex.toString() : "(error response)");
            throw new NopAiAgentException(
                    "LLM call retry policy returned FALLBACK but no "
                            + "fallback model is available from the model "
                            + "router (classification=" + classification
                            + ", attempt=" + attempt + ")", ex);
        }
        int failedAttempt = attempt;
        String prevModelKey = buildModelKey(routedOptions);
        ChatOptions next = fallbackOptions;
        request.setOptions(next);
        LOG.warn("LLM call FALLBACK after attempt={} "
                        + "(classification={}): switching model {} -> {} "
                        + "(attempt reset to 0) and retrying",
                failedAttempt, classification, prevModelKey,
                buildModelKey(next));
        return next;
    }
    /**
     * Holds the result of {@link #doLlmCallWithRetry}: the ChatResponse,
     * the final routedOptions (possibly updated by FALLBACK), the
     * llmCallStart timestamp for usage recording, and a success flag.
     */
    static final class LlmCallResult {
        final ChatResponse response;
        final ChatOptions routedOptions;
        final long llmCallStart;
        final boolean success;

        LlmCallResult(ChatResponse response, ChatOptions routedOptions,
                      long llmCallStart, boolean success) {
            this.response = response;
            this.routedOptions = routedOptions;
            this.llmCallStart = llmCallStart;
            this.success = success;
        }

        boolean isSuccess() { return success; }
    }
    /**
     * Build the composite model identity key ({@code provider:model}) from a
     * {@link ChatOptions} instance, as returned by {@code RoutingResult.getOptions()}.
     * Null provider/model are normalized to empty strings so the key is always
     * non-null and comparable (plan 205 / L2-21). This is the model identity
     * used to detect switches between ReAct iterations.
     */
    public static String buildModelKey(ChatOptions options) {
        String provider = options.getProvider() != null ? options.getProvider() : "";
        String model = options.getModel() != null ? options.getModel() : "";
        return provider + ":" + model;
    }
    /**
     * Plan 271 (finding 14-03): invoke {@link IChatService#call} with a
     * wall-clock timeout. When {@code llmTimeoutMs > 0} and a
     * {@code timeoutExecutor} is configured, the synchronous call is dispatched
     * to the executor and bounded by {@code f.get(llmTimeoutMs)}; on timeout a
     * {@link CompletionException} wrapping a {@link TimeoutException} is thrown
     * and handled by the caller's retry/error-classification path. When the
     * timeout is disabled ({@code llmTimeoutMs <= 0}) or no executor is wired,
     * the call is invoked directly (backward-compatible).
     *
     * <p>Uses {@link CompletableFuture#get(long, TimeUnit)} instead of
     * {@code .orTimeout().join()} so that a forced cancel (which interrupts
     * the ReAct-loop thread via {@code cancelSession(forced=true)}) breaks the
     * wait immediately. {@code .join()} is not interruptible and would block
     * until the LLM call completes or the timeout fires, defeating the purpose
     * of a forced cancel.
     */
    public ChatResponse callChatWithTimeout(ChatRequest request) {
        if (llmTimeoutMs <= 0 || timeoutExecutor == null) {
            return chatService.call(request, null);
        }
        CompletableFuture<ChatResponse> f = CompletableFuture.supplyAsync(
                () -> chatService.call(request, null), timeoutExecutor);
        try {
            return f.get(llmTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopAiAgentException(
                    "LLM call interrupted (forced cancel or thread interrupt)", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new CompletionException(cause != null ? cause : e);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new CompletionException(e);
        }
    }
    /**
     * Plan 271 (finding 14-03): parse a {@link ChatToolCall#getId()} (String)
     * into the {@code int} expected by {@link AiToolCallResult#errorResult}.
     * Returns 0 when the id is non-numeric (the id is only used for result-to-
     * call matching, which is already satisfied by the ToolCallOutput wrapper).
     */
    public static int parseToolCallId(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
    /**
     * Plan 213 (circuit-aware-routing): resolve the routed options against the
     * circuit breaker BEFORE the model-switched audit detection and the retry
     * loop's outer circuit check. This upgrades the engine's handling of a
     * circuit-OPEN primary model from "reject → terminate the whole agent
     * execution" (plan 210 behaviour) to "reject → proactively scan the
     * {@link IModelRouter} fallback chain via {@code getFallback(...)} for a
     * circuit-allowed model → switch {@code routedOptions} to it and continue"
     * (design {@code nop-ai-agent-reliability.md} §3.3 / §5.2).
     *
     * <p><b>Algorithm</b>:
     * <ol>
     *   <li>Build the primary model-key from {@code routedOptions} and consult
     *       {@code circuitBreaker.allowCall(key)}.</li>
     *   <li>If {@code true} → the primary model is circuit-closed; return
     *       {@code routedOptions} unchanged (zero-overhead path; the shipped
     *       {@link AlwaysClosed} default always takes this branch, so wiring
     *       this step is a zero-regression change).</li>
     *   <li>If {@code false} → scan the fallback chain: repeatedly call
     *       {@code modelRouter.getFallback(current)}; for each non-null fallback,
     *       check {@code allowCall(fallback-key)}. The first circuit-allowed
     *       fallback is logged (circuit-induced switch) and returned as the
     *       new routed options.</li>
     *   <li>If {@code getFallback} returns {@code null} (chain exhausted) or
     *       every fallback is also circuit-rejected, fail fast with a
     *       {@link NopAiAgentException} listing every checked model-key + its
     *       circuit state + operator guidance (Minimum Rules #24 — no silent
     *       skip, no swallowing).</li>
     * </ol>
     *
     * <p><b>Positioning</b>: invoked between {@code modelRouter.route(...)} and
     * the model-switched audit detection (plan 205, role=80 message). This
     * ordering is deliberate: the resolution may change {@code routedOptions},
     * so the audit detection must observe the <i>post-resolution</i> final
     * model to correctly emit the model-switched message (the {@code fromModel}
     * is the previous iteration's model, the {@code toModel} is the
     * circuit-resolved model). The downstream outer circuit check (formerly
     * the primary rejection point) now acts as a safety-net for the rare
     * concurrent-circuit-trip race (a model that was circuit-cleared by this
     * resolution tripping OPEN between resolution and the check).
     *
     * <p><b>No RoutingResult mutation</b>: this method does not modify the
     * immutable {@link io.nop.ai.agent.router.RoutingResult} returned by
     * {@code route()} — the circuit-induced switch is recorded only via
     * {@code LOG.warn} and the natural model-switched audit message. The
     * {@code routingReason} stays as route()'s original decision.
     *
     * <p><b>Loop bound</b>: the fallback chain length is bounded by router
     * configuration (SmartModelRouter's configured chain, typically ≤5). A
     * defensive hard cap ({@link #MAX_FALLBACK_SCAN}) guards against a buggy
     * custom {@code IModelRouter} whose {@code getFallback} never returns
     * {@code null} — the cap fails loud rather than spinning forever.
     *
     * @param routedOptions the options returned by {@code route()}; non-null
     *      *      * @param sessionId     the session id for log correlation; may be null
     *                      (anonymous executions still resolve correctly)
     * @return the circuit-cleared options to use for this iteration (either
     *         the unchanged {@code routedOptions} or a circuit-allowed
     *         fallback); never null
     * @throws NopAiAgentException if the primary and every fallback model are
     *         all circuit-rejected (fail fast — no silent skip)
     */
    public ChatOptions resolveCircuitAware(ChatOptions routedOptions, String sessionId) {
        String primaryKey = buildModelKey(routedOptions);
        if (circuitBreaker.allowCall(primaryKey)) {
            // Zero-overhead path: primary model is circuit-closed. With the
            // shipped AlwaysClosed default this branch is always taken, so
            // wiring this resolution step is a zero-regression change.
            return routedOptions;
        }

        // Primary circuit is OPEN or HALF_OPEN-probe-busy → scan the fallback
        // chain for a circuit-allowed model. Collect every checked model-key
        // + state so the fail-fast diagnostic surfaces the full picture.
        CircuitState primaryState = circuitBreaker.getState(primaryKey);
        List<String> checked = new ArrayList<>();
        checked.add(primaryKey + "=" + primaryState);

        ChatOptions current = routedOptions;
        for (int step = 0; step < MAX_FALLBACK_SCAN; step++) {
            ChatOptions fallback = modelRouter.getFallback(current);
            if (fallback == null) {
                // Chain exhausted without a circuit-allowed model.
                break;
            }
            String fallbackKey = buildModelKey(fallback);
            if (circuitBreaker.allowCall(fallbackKey)) {
                LOG.warn("Circuit-aware routing switched model {} -> {} "
                                + "(primary {} circuit={}). session={}",
                        primaryKey, fallbackKey, primaryKey, primaryState, sessionId);
                return fallback;
            }
            checked.add(fallbackKey + "=" + circuitBreaker.getState(fallbackKey));
            current = fallback;
        }

        // All models circuit-rejected → fail fast (Minimum Rules #24 — no
        // silent skip, no swallowing). The message lists every checked
        // model-key + its circuit state so the operator can see the full
        // picture, plus actionable guidance.
        throw new NopAiAgentException(
                "Circuit breaker rejected all available models for session "
                        + sessionId + ". Checked models (key=circuitState): " + checked
                        + ". Wait for the breaker cooldown before retrying, or configure "
                        + "additional IModelRouter fallback models via SmartModelRouter.fallback(...).");
    }
    /**
     * Defensive hard cap on the circuit-aware fallback-chain scan. A correctly
     * implemented {@link IModelRouter} returns {@code null} from
     * {@code getFallback} when its (bounded) chain is exhausted; this cap only
     * triggers for a buggy custom router that never returns {@code null}, in
     * which case the fail-fast at {@link #resolveCircuitAware} surfaces the
     * problem rather than spinning forever. 64 is well above any realistic
     * configured chain length (SmartModelRouter's configured chains are
     * typically ≤5).
     */
    static final int MAX_FALLBACK_SCAN = 64;


    /**
     * Plan 207 (L3-2): sleep for the retry-policy-computed backoff delay.
     * A zero/negative delay is a no-op (the policy opted for immediate
     * retry). An interrupted sleep propagates as an {@link NopAiAgentException}
     * wrapping the {@link InterruptedException} (no silent swallow — Minimum
     * Rules #24) and re-sets the interrupt flag so upper layers honour it.
     */
    public static void sleepBackoff(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new NopAiAgentException(
                    "LLM retry backoff sleep interrupted: delayMs=" + delayMs, ie);
        }
    }
}

