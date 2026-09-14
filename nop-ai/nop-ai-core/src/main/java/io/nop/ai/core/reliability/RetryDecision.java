package io.nop.ai.core.reliability;

/**
 * Retry decision returned by {@link IRetryPolicy#shouldRetry(RetryContext)}
 * (design {@code nop-ai-agent-llm-layer.md} §7.2 / plan 207 / L3-2).
 *
 * <ul>
 *   <li>{@link #RETRY} — retry the same LLM call after waiting
 *       {@code delayMs} milliseconds (exponential backoff computed by the
 *       policy).</li>
 *   <li>{@link #STOP} — stop retrying and propagate the failure (the
 *       caller throws the last error). This is the {@link NoRetryPolicy}
 *       behaviour for every error.</li>
 *   <li>{@link #FALLBACK} — switch to a fallback recovery channel (wired
 *       since plan 2026-08-01-1505-1; NOT a fail-loud STOP). The retry
 *       loop ({@code LlmCallCoordinator}) routes the decision by
 *       {@code errorClassification}: {@code QUOTA_EXCEEDED} /
 *       {@code AUTH_INVALID} → the account chain (same model, next backup
 *       account, escalating to cross-provider failover), other
 *       retry-eligible classes → the model-tier fallback
 *       ({@code IModelRouter.getFallback}). Channel exhaustion fails loud
 *       (no silent skip, Minimum Rules #24); the loop is additionally
 *       bounded by a total FALLBACK-step cap.</li>
 * </ul>
 */
public enum RetryDecision {
    RETRY,
    STOP,
    FALLBACK
}
