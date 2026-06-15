package io.nop.ai.agent.reliability;

/**
 * LLM call error classification (design
 * {@code nop-ai-agent-llm-layer.md} §7.2 / plan 207 / L3-2). Drives the
 * retry decision in {@link IRetryPolicy}: only {@link #TRANSIENT} and
 * {@link #RATE_LIMITED} are eligible for programmatic retry;
 * {@link #NON_TRANSIENT} and {@link #QUOTA_EXCEEDED} fail fast.
 *
 * <ul>
 *   <li>{@link #TRANSIENT} — transient/server-side fault suitable for
 *       programmatic retry (5xx, network timeout, connection reset).</li>
 *   <li>{@link #NON_TRANSIENT} — client-side fault NOT suitable for
 *       programmatic retry (400/401/403/404 — parameter/auth/not-found
 *       errors; retrying the identical request will fail identically).</li>
 *   <li>{@link #RATE_LIMITED} — 429 provider rate limit; suitable for
 *       retry after a delay. In the current call path the HTTP exception
 *       does not carry the {@code Retry-After} header, so the retry uses
 *       exponential backoff rather than header-driven wait (Non-Goal).</li>
 *   <li>{@link #QUOTA_EXCEEDED} — account/billing quota exhausted (also
 *       surfaced as 429 by some providers, but semantically distinct from
 *       transient rate limiting); NOT retryable — retrying will not
 *       replenish quota.</li>
 * </ul>
 */
public enum ErrorClassification {
    TRANSIENT,
    NON_TRANSIENT,
    RATE_LIMITED,
    QUOTA_EXCEEDED
}
