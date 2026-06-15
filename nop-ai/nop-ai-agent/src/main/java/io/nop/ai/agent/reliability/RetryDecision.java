package io.nop.ai.agent.reliability;

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
 *   <li>{@link #FALLBACK} — switch to a fallback model. In this plan there
 *       is no fallback chain wired (Non-Goal — fallback model consumption
 *       is {@code IModelRouter} territory, plan 154), so the retry loop
 *       treats FALLBACK as a fail-loud STOP and records the decision (no
 *       silent skip, Minimum Rules #24).</li>
 * </ul>
 */
public enum RetryDecision {
    RETRY,
    STOP,
    FALLBACK
}
