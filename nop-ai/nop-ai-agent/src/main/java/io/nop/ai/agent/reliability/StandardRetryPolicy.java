package io.nop.ai.agent.reliability;

import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Functional {@link IRetryPolicy} implementing the Standard retry mode
 * (design {@code nop-ai-agent-llm-layer.md} §7.3 / plan 207 / L3-2).
 *
 * <p>Retries transient and rate-limited failures up to
 * {@code maxAttempts} total call attempts (1 initial call +
 * {@code maxAttempts - 1} retries), with exponential backoff plus
 * <b>full jitter</b>: the RETRY delay is uniformly random in
 * {@code [0, min(baseDelay * 2^attempt, maxDelay)]}. Jitter decorrelates
 * concurrent retry waves (thundering-herd suppression, MA6.3-AR-5); the
 * deterministic exponential formula is the upper-bound baseline. Non-transient
 * and quota-exceeded failures fail fast (immediate STOP).
 *
 * <h2>Decision rules</h2>
 * <ul>
 *   <li>{@link ErrorClassification#TRANSIENT} /
 *       {@link ErrorClassification#RATE_LIMITED} → RETRY when
 *       {@code attempt < maxAttempts - 1} (there is still room for another
 *       attempt); STOP when {@code attempt >= maxAttempts - 1} (max attempts
 *       exhausted). The RETRY delay is uniformly random in
 *       {@code [0, min(baseDelay * 2^attempt, maxDelay)]} (full jitter).</li>
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
 * <p><b>429 / Retry-After</b>（W2e-3 落地，设计 §3.7）：{@code RATE_LIMITED} 的 RETRY 延迟
 * 以 {@link RetryContext#getRetryAfterMs()} 作 <b>下限（floor）</b>——{@code delay = retryAfterMs +
 * uniform(0, jitterCap)}，永不低于 retryAfterMs。理由：服务器已告知精确等待，遵守它的重要性高于
 * thundering-herd 抑制（配额/限流信号下不应抢跑）。{@code retryAfterMs == null}（无服务器提示）时
 * 退回纯 full-jitter。{@code TRANSIENT} 仍用纯 full-jitter（无服务器提示）。两种策略不同须文档化。
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
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiAgentErrors.ARG_MSG,
                    "StandardRetryPolicy maxAttempts must be >= 1: " + maxAttempts);
        }
        if (baseDelayMs < 0) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiAgentErrors.ARG_MSG,
                    "StandardRetryPolicy baseDelayMs must be >= 0: " + baseDelayMs);
        }
        if (maxDelayMs < baseDelayMs) {
            throw new NopAiAgentException(NopAiAgentErrors.ERR_AI_AGENT_INVALID_ARG).param(NopAiAgentErrors.ARG_MSG,
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

        // Retryable + attempts remaining → RETRY with backoff.
        long delay;
        Long retryAfterMs = context.getRetryAfterMs();
        if (classification == ErrorClassification.RATE_LIMITED && retryAfterMs != null) {
            // 设计 §3.7：RATE_LIMITED 用 Retry-After 作 floor（永不低于服务器要求）。
            // delay = retryAfterMs + uniform(0, jitterCap)。retryAfterMs 可超过 maxDelay
            // （服务器明确要求的等待优先于 cap）。jitterCap 仍受 maxDelay 约束。
            delay = computeRateLimitedDelay(retryAfterMs, context.getAttempt());
        } else {
            // TRANSIENT / RATE_LIMITED 无 Retry-After 提示 → 纯 full-jitter 退避。
            delay = computeBackoff(context.getAttempt());
        }
        return RetryOutcome.retryAfter(delay);
    }

    /**
     * RATE_LIMITED floor + jitter（设计 §3.7）：
     * {@code delay = retryAfterMs + uniform(0, min(baseDelayMs * 2^attempt, maxDelayMs))}。
     * 永不低于 {@code retryAfterMs}（遵守服务器明确要求的等待）。{@code retryAfterMs} 可超过
     * {@code maxDelayMs}——服务器要求优先于 herd 抑制 cap。jitter 部分仍受 maxDelayMs 约束。
     */
    private long computeRateLimitedDelay(long retryAfterMs, int attempt) {
        long jitterCap = backoffCap(attempt);
        if (jitterCap <= 0) {
            return retryAfterMs;
        }
        long jitter = ThreadLocalRandom.current().nextLong(0, jitterCap + 1);
        return retryAfterMs + jitter;
    }

    /**
     * Exponential backoff with full jitter:
     * {@code uniform(0, min(baseDelayMs * 2^attempt, maxDelayMs))}. The
     * deterministic formula {@code min(baseDelayMs * 2^attempt, maxDelayMs)}
     * is the upper-bound baseline; a uniformly random offset in {@code [0, cap]}
     * decorrelates concurrent retry waves (thundering-herd suppression,
     * MA6.3-AR-5). Overflow-safe: if {@code 2^attempt} would overflow, the cap
     * applies. {@code baseDelayMs == 0} (or a zero cap) returns exactly 0.
     */
    private long computeBackoff(int attempt) {
        long cap = backoffCap(attempt);
        if (cap <= 0) {
            return 0L;
        }
        // Full jitter: uniform in [0, cap] (inclusive upper bound).
        return ThreadLocalRandom.current().nextLong(0, cap + 1);
    }

    /**
     * Computes the deterministic exponential cap {@code min(baseDelayMs * 2^attempt, maxDelayMs)}
     * (overflow-safe). Used both as the full-jitter upper bound (TRANSIENT) and as the
     * jitter ceiling added on top of the Retry-After floor (RATE_LIMITED).
     */
    private long backoffCap(int attempt) {
        if (baseDelayMs == 0) {
            return 0L;
        }
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
