package io.nop.ai.core.reliability;

/**
 * Layer 3 extension point for retrying a failed single LLM call (design
 * {@code nop-ai-agent-llm-layer.md} §7 / {@code nop-ai-agent-reliability.md}
 * §3.1 / plan 207 / L3-2).
 *
 * <p>The ReAct loop's single LLM call point ({@code chatService.call(...)} in
 * {@code ReActAgentExecutor}) wraps the call in a retry loop that, on a thrown
 * exception, classifies the error into an {@link ErrorClassification}, builds a
 * {@link RetryContext}, and consults {@link #shouldRetry(RetryContext)}:
 * <ul>
 *   <li>{@link RetryOutcome#isRetry()} RETRY — wait {@code delayMs} then reissue
 *       the same {@code ChatRequest} (the loop reuses the same request object;
 *       no request mutation in this plan — image fallback is a Non-Goal).</li>
 *   <li>{@link RetryOutcome#isStop()} STOP — rethrow the last error
 *       immediately (fail fast).</li>
 *   <li>{@link RetryOutcome#isFallback()} FALLBACK — switch to a fallback
 *       recovery channel (wired since plan 2026-08-01-1505-1, NOT a
 *       fail-loud STOP). The retry loop routes the decision by
 *       {@code errorClassification}: {@code QUOTA_EXCEEDED} /
 *       {@code AUTH_INVALID} → the account chain (same model, next backup
 *       account), other retry-eligible classes → the model-tier fallback
 *       ({@code IModelRouter.getFallback}). Channel exhaustion fails loud;
 *       the loop is bounded by a total FALLBACK-step cap.</li>
 * </ul>
 *
 * <p><b>Pass-through semantics of the shipped default</b>: {@link NoRetryPolicy}
 * unconditionally returns STOP, so the retry loop executes the LLM call exactly
 * once and propagates any exception as-is. This is an <i>explicit</i>
 * pass-through, not a hidden gap: it preserves the engine's pre-plan-207
 * zero-retry behaviour verbatim, so wiring the retry loop is a zero-regression
 * change. A functional policy ({@code StandardRetryPolicy}) is registered
 * explicitly via {@code DefaultAgentEngine.setRetryPolicy}.
 *
 * <p><b>Contract for implementations</b>: {@link #shouldRetry} must never
 * return {@code null}. A policy that decides not to retry must return
 * {@link RetryOutcome#stop()} (or {@link RetryOutcome#fallback()}) rather than
 * null. Implementations must be safe to call from the ReAct loop's execution
 * thread (stateless or properly synchronised).
 */
public interface IRetryPolicy {

    /**
     * Decide whether to retry the failed LLM call described by
     * {@code context}.
     *
     * @param context non-null retry context (attempt, last error, classification)
     * @return a non-null {@link RetryOutcome}; never {@code null}
     */
    RetryOutcome shouldRetry(RetryContext context);
}
