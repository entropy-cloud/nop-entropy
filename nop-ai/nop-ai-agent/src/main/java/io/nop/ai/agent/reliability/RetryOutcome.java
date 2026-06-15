package io.nop.ai.agent.reliability;

/**
 * Immutable outcome of {@link IRetryPolicy#shouldRetry(RetryContext)}
 * (design {@code nop-ai-agent-llm-layer.md} §7.2 / plan 207 / L3-2).
 *
 * <p>Bundles a {@link RetryDecision} with the delay (in milliseconds) the
 * caller must wait before acting on the decision. For {@link RetryDecision#STOP}
 * and {@link RetryDecision#FALLBACK} the delay is {@code 0} (no wait —
 * propagate immediately). For {@link RetryDecision#RETRY} the delay is the
 * policy-computed backoff (e.g. exponential backoff in
 * {@code StandardRetryPolicy}).
 *
 * <p>This is a simple value-carrier rather than a sealed interface with
 * per-decision subtypes: the decision space is closed (3 values) and the
 * delay is the only varying attribute, so a flat record-style holder keeps
 * the policy implementations and the retry loop branch logic minimal and
 * auditable.
 */
public final class RetryOutcome {

    private final RetryDecision decision;
    private final long delayMs;

    public RetryOutcome(RetryDecision decision, long delayMs) {
        if (decision == null) {
            throw new IllegalArgumentException("RetryOutcome decision must not be null");
        }
        if (delayMs < 0) {
            throw new IllegalArgumentException("RetryOutcome delayMs must not be negative: " + delayMs);
        }
        this.decision = decision;
        this.delayMs = delayMs;
    }

    public RetryDecision getDecision() {
        return decision;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public boolean isRetry() {
        return decision == RetryDecision.RETRY;
    }

    public boolean isStop() {
        return decision == RetryDecision.STOP;
    }

    public boolean isFallback() {
        return decision == RetryDecision.FALLBACK;
    }

    /**
     * Convenience factory for a STOP outcome (zero delay).
     */
    public static RetryOutcome stop() {
        return new RetryOutcome(RetryDecision.STOP, 0L);
    }

    /**
     * Convenience factory for a FALLBACK outcome (zero delay — the
     * caller fails loud because no fallback chain is wired in this plan).
     */
    public static RetryOutcome fallback() {
        return new RetryOutcome(RetryDecision.FALLBACK, 0L);
    }

    /**
     * Convenience factory for a RETRY outcome with the given backoff delay.
     */
    public static RetryOutcome retryAfter(long delayMs) {
        return new RetryOutcome(RetryDecision.RETRY, delayMs);
    }
}
