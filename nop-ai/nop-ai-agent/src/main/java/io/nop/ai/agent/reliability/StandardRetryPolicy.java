package io.nop.ai.agent.reliability;

/**
 * Functional {@link IRetryPolicy} implementing the Standard retry mode
 * (design {@code nop-ai-agent-llm-layer.md} §7.3 / plan 207 / L3-2).
 *
 * <p>Retries transient and rate-limited failures up to
 * {@code maxAttempts} total call attempts (1 initial call +
 * {@code maxAttempts - 1} retries), with exponential backoff
 * ({@code baseDelay * 2^attempt}, capped at {@code maxDelay}). Non-transient
 * and quota-exceeded failures fail fast (immediate STOP).
 *
 * <h2>Decision rules</h2>
 * <ul>
 *   <li>{@link ErrorClassification#TRANSIENT} /
 *       {@link ErrorClassification#RATE_LIMITED} → RETRY when
 *       {@code attempt < maxAttempts - 1} (there is still room for another
 *       attempt); STOP when {@code attempt >= maxAttempts - 1} (max attempts
 *       exhausted). The RETRY delay is
 *       {@code min(baseDelay * 2^attempt, maxDelay)}.</li>
 *   <li>{@link ErrorClassification#NON_TRANSIENT} /
 *       {@link ErrorClassification#QUOTA_EXCEEDED} → immediate STOP
 *       (retrying the identical request fails identically; quota is not
 *       replenished by retrying).</li>
 *   <li>{@code hasStreamedContent == true} → STOP (streaming guard, design
 *       §7.4: once content has streamed, FALLBACK/RETRY would duplicate
 *       output; the current call path is non-streaming so this branch is
 *       dormant — reserved for a streaming successor).</li>
 * </ul>
 *
 * <p><b>429 / Retry-After</b>: the current call path's exception does not
 * carry the {@code Retry-After} header (Non-Goal — the header is dropped by
 * {@code ChatServiceImpl}), so 429 RATE_LIMITED uses exponential backoff
 * rather than header-driven wait. A successor that extends
 * {@code ChatServiceImpl} to preserve headers can feed Retry-After into the
 * policy without changing this interface.
 *
 * <p>This implementation is stateless (all state lives in the
 * {@link RetryContext} passed to {@link #shouldRetry}), so a single instance
 * is safe to share across concurrent executions.
 */
public final class StandardRetryPolicy implements IRetryPolicy {

    /** Default max total call attempts: 1 initial call + 2 retries. */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    /** Default base backoff delay in milliseconds. */
    public static final long DEFAULT_BASE_DELAY_MS = 1000L;
    /** Default backoff cap in milliseconds. */
    public static final long DEFAULT_MAX_DELAY_MS = 30_000L;

    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;

    public StandardRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_DELAY_MS);
    }

    /**
     * @param maxAttempts  max total call attempts (must be &gt;= 1; 1 = no
     *                     retry — equivalent to {@link NoRetryPolicy} for
     *                     transient errors but still fail-fast for
     *                     non-transient)
     * @param baseDelayMs  base backoff delay in milliseconds (must be &gt;= 0;
     *                     0 = retry immediately with no wait)
     * @param maxDelayMs   backoff cap in milliseconds (must be &gt;= baseDelayMs)
     */
    public StandardRetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "StandardRetryPolicy maxAttempts must be >= 1: " + maxAttempts);
        }
        if (baseDelayMs < 0) {
            throw new IllegalArgumentException(
                    "StandardRetryPolicy baseDelayMs must be >= 0: " + baseDelayMs);
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException(
                    "StandardRetryPolicy maxDelayMs must be >= baseDelayMs: maxDelayMs="
                            + maxDelayMs + ", baseDelayMs=" + baseDelayMs);
        }
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    @Override
    public RetryOutcome shouldRetry(RetryContext context) {
        ErrorClassification classification = context.getErrorClassification();

        // Streaming guard (design §7.4): once content has streamed, do not
        // retry/fallback (would duplicate output). Dormant in the current
        // non-streaming call path (hasStreamedContent is always false).
        if (context.isHasStreamedContent()) {
            return RetryOutcome.stop();
        }

        // Non-transient / quota-exceeded → fail fast.
        if (classification != ErrorClassification.TRANSIENT
                && classification != ErrorClassification.RATE_LIMITED) {
            return RetryOutcome.stop();
        }

        // Max attempts exhausted → STOP. The caller (retry loop) rethrows the
        // last error (no silent skip — Minimum Rules #24).
        if (context.getAttempt() >= maxAttempts - 1) {
            return RetryOutcome.stop();
        }

        // Retryable + attempts remaining → RETRY with exponential backoff.
        long delay = computeBackoff(context.getAttempt());
        return RetryOutcome.retryAfter(delay);
    }

    /**
     * Exponential backoff: {@code min(baseDelayMs * 2^attempt, maxDelayMs)}.
     * Overflow-safe: if {@code 2^attempt} would overflow, the cap applies.
     */
    private long computeBackoff(int attempt) {
        if (baseDelayMs == 0) {
            return 0L;
        }
        // Shift carefully to avoid overflow: stop doubling once we reach the
        // cap. attempt is the failed-attempt index; the wait before the next
        // attempt is baseDelayMs * 2^attempt.
        long delay = baseDelayMs;
        for (int i = 0; i < attempt && delay < maxDelayMs; i++) {
            long next = delay << 1;
            // Guard against overflow (long overflow wraps negative).
            if (next < delay) {
                break;
            }
            delay = next;
        }
        return Math.min(delay, maxDelayMs);
    }
}
