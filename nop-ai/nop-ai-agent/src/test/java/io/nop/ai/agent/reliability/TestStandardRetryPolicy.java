package io.nop.ai.agent.reliability;

import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.api.core.exceptions.NopTimeoutException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 207 (L3-2) Phase 2 focused test for {@link StandardRetryPolicy}
 * (Minimum Rules #25). Verifies every decision path: TRANSIENT/RATE_LIMITED
 * retry up to maxAttempts with exponential backoff; NON_TRANSIENT fail fast;
 * QUOTA_EXCEEDED/AUTH_INVALID → FALLBACK（plan 2026-08-01-1505-1，账号链路由）;
 * maxAttempts exhaustion → STOP; backoff formula and cap.
 */
public class TestStandardRetryPolicy {

    // ========================================================================
    // Constructor validation
    // ========================================================================

    @Test
    void rejectsMaxAttemptsLessThanOne() {
        assertThrows(NopAiAgentException.class,
                () -> new StandardRetryPolicy(0, 100L, 1000L));
    }

    @Test
    void rejectsNegativeBaseDelay() {
        assertThrows(NopAiAgentException.class,
                () -> new StandardRetryPolicy(3, -1L, 1000L));
    }

    @Test
    void rejectsMaxDelayBelowBaseDelay() {
        assertThrows(NopAiAgentException.class,
                () -> new StandardRetryPolicy(3, 1000L, 100L));
    }

    @Test
    void defaultsAreExposed() {
        StandardRetryPolicy p = new StandardRetryPolicy();
        assertEquals(StandardRetryPolicy.DEFAULT_MAX_ATTEMPTS, p.getMaxAttempts());
        assertEquals(StandardRetryPolicy.DEFAULT_BASE_DELAY_MS, p.getBaseDelayMs());
        assertEquals(StandardRetryPolicy.DEFAULT_MAX_DELAY_MS, p.getMaxDelayMs());
    }

    // ========================================================================
    // TRANSIENT retry path
    // ========================================================================

    @Test
    void transientRetriesUntilMaxAttempts() {
        StandardRetryPolicy p = new StandardRetryPolicy(3, 10L, 1000L);
        // attempt 0 < (3-1)=2 → RETRY
        RetryOutcome r0 = p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT));
        assertTrue(r0.isRetry(), "attempt 0 (transient) must RETRY when attempts remain");
        // attempt 1 < 2 → RETRY
        RetryOutcome r1 = p.shouldRetry(ctx(1, ErrorClassification.TRANSIENT));
        assertTrue(r1.isRetry(), "attempt 1 (transient) must RETRY when attempts remain");
        // attempt 2 >= 2 → STOP (max attempts exhausted)
        RetryOutcome r2 = p.shouldRetry(ctx(2, ErrorClassification.TRANSIENT));
        assertTrue(r2.isStop(), "attempt 2 (transient) must STOP when maxAttempts exhausted");
    }

    @Test
    void maxAttemptsOneMeansNoRetry() {
        // maxAttempts=1 → the first failure (attempt 0) immediately STOPs
        // (0 >= 1-1 = 0).
        StandardRetryPolicy p = new StandardRetryPolicy(1, 10L, 1000L);
        RetryOutcome r = p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT));
        assertTrue(r.isStop(), "maxAttempts=1 must STOP on the first failure (no retry)");
    }

    // ========================================================================
    // RATE_LIMITED retry path (429)
    // ========================================================================

    @Test
    void rateLimitedRetriesUntilMaxAttempts() {
        StandardRetryPolicy p = new StandardRetryPolicy(3, 10L, 1000L);
        assertTrue(p.shouldRetry(ctx(0, ErrorClassification.RATE_LIMITED)).isRetry(),
                "429 RATE_LIMITED at attempt 0 must RETRY");
        assertTrue(p.shouldRetry(ctx(1, ErrorClassification.RATE_LIMITED)).isRetry(),
                "429 RATE_LIMITED at attempt 1 must RETRY");
        assertTrue(p.shouldRetry(ctx(2, ErrorClassification.RATE_LIMITED)).isStop(),
                "429 RATE_LIMITED at attempt 2 (maxAttempts=3) must STOP");
    }

    // ========================================================================
    // NON_TRANSIENT / QUOTA_EXCEEDED fail fast
    // ========================================================================

    @Test
    void nonTransientFailsFastImmediately() {
        StandardRetryPolicy p = new StandardRetryPolicy(5, 10L, 1000L);
        // Even at attempt 0 with maxAttempts=5, NON_TRANSIENT must STOP
        // immediately (retrying the identical request fails identically).
        RetryOutcome r = p.shouldRetry(ctx(0, ErrorClassification.NON_TRANSIENT));
        assertTrue(r.isStop(), "NON_TRANSIENT must STOP immediately regardless of attempts remaining");
        assertEquals(0L, r.getDelayMs());
    }

    @Test
    void quotaExceededReturnsFallback() {
        // Plan 2026-08-01-1505-1（设计 §6.8）：QUOTA_EXCEEDED 从 STOP 改为 FALLBACK——
        // 交由重试循环按 errorClassification 路由到账号链（同模型换 key/账号）。
        StandardRetryPolicy p = new StandardRetryPolicy(5, 10L, 1000L);
        RetryOutcome r = p.shouldRetry(ctx(0, ErrorClassification.QUOTA_EXCEEDED));
        assertTrue(r.isFallback(),
                "QUOTA_EXCEEDED must return FALLBACK (account-chain routing), not STOP");
        assertFalse(r.isRetry());
        assertFalse(r.isStop());
        assertEquals(0L, r.getDelayMs(), "FALLBACK has zero delay (immediate account switch)");
    }

    @Test
    void authInvalidReturnsFallback() {
        // AUTH_INVALID 同样走账号链（key 失效就换备用账号，设计 §3.2/§3.6）。
        // 本文件此前无 AUTH 用例——新增覆盖。
        StandardRetryPolicy p = new StandardRetryPolicy(5, 10L, 1000L);
        RetryOutcome r = p.shouldRetry(ctx(0, ErrorClassification.AUTH_INVALID));
        assertTrue(r.isFallback(),
                "AUTH_INVALID must return FALLBACK (account-chain routing), not STOP");
        assertFalse(r.isStop());
    }

    // ========================================================================
    // Streaming guard (dormant: hasStreamedContent always false in this plan)
    // ========================================================================

    @Test
    void streamedContentStopsEvenForTransient() {
        StandardRetryPolicy p = new StandardRetryPolicy(5, 10L, 1000L);
        RetryContext streamed = new RetryContext(
                0, new NopTimeoutException(), ErrorClassification.TRANSIENT, true);
        assertTrue(p.shouldRetry(streamed).isStop(),
                "hasStreamedContent=true must STOP even for transient (streaming guard, design §7.4)");
    }

    // ========================================================================
    // Exponential backoff formula, cap and full jitter (MA6.3-AR-5)
    // ========================================================================

    @Test
    void backoffStaysWithinJitterRange() {
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 10_000L);
        // Full jitter: delay is uniformly random in [0, min(base*2^attempt, max)].
        // The deterministic formula is the upper bound, never an exact value.
        assertDelayInRange(p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 100L, "attempt 0 upper bound = baseDelay * 2^0 = 100");
        assertDelayInRange(p.shouldRetry(ctx(1, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 200L, "attempt 1 upper bound = baseDelay * 2^1 = 200");
        assertDelayInRange(p.shouldRetry(ctx(2, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 400L, "attempt 2 upper bound = baseDelay * 2^2 = 400");
        assertDelayInRange(p.shouldRetry(ctx(3, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 800L, "attempt 3 upper bound = baseDelay * 2^3 = 800");
    }

    @Test
    void backoffJitterUpperBoundCappedAtMaxDelay() {
        // baseDelay=1000, maxDelay=3000 → upper bound: attempt 0=1000, 1=2000,
        // 2=4000 capped to 3000. Every sample must stay within [0, cap].
        StandardRetryPolicy p = new StandardRetryPolicy(5, 1000L, 3000L);
        assertDelayInRange(p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 1000L, "attempt 0 upper bound = baseDelay = 1000");
        assertDelayInRange(p.shouldRetry(ctx(1, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 2000L, "attempt 1 upper bound = 2000");
        assertDelayInRange(p.shouldRetry(ctx(2, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 3000L, "attempt 2 upper bound must be capped at maxDelay=3000");
        assertDelayInRange(p.shouldRetry(ctx(3, ErrorClassification.TRANSIENT)).getDelayMs(),
                0L, 3000L, "attempt 3 upper bound must stay capped at maxDelay=3000");
    }

    @Test
    void backoffJitterProducesDifferentValues() {
        // Real randomness (not a constant offset): across many draws over the
        // range [0, 800] (attempt 3, baseDelay=100 → cap 100*2^3=800), observing
        // at least two distinct values. The probability of all draws landing on
        // the same value is negligible ((1/801)^100 per value), so this is a
        // stable anti-hollow assertion: jitter must be a real random source,
        // not a deterministic shift. (attempt 3 < maxAttempts-1=4 → RETRY.)
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 10_000L);
        long first = p.shouldRetry(ctx(3, ErrorClassification.TRANSIENT)).getDelayMs();
        assertTrue(first >= 0 && first <= 800L, "delay must stay in [0, 800]: " + first);
        boolean sawDifferent = false;
        for (int i = 0; i < 100; i++) {
            long next = p.shouldRetry(ctx(3, ErrorClassification.TRANSIENT)).getDelayMs();
            assertTrue(next >= 0 && next <= 800L, "delay must stay in [0, 800]: " + next);
            if (next != first) {
                sawDifferent = true;
                break;
            }
        }
        assertTrue(sawDifferent,
                "Full jitter must produce different values across repeated calls (real random source)");
    }

    @Test
    void zeroBaseDelayMeansImmediateRetry() {
        StandardRetryPolicy p = new StandardRetryPolicy(3, 0L, 1000L);
        assertEquals(0L, p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).getDelayMs(),
                "baseDelay=0 means retry immediately (no wait)");
        assertTrue(p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).isRetry());
    }

    private static void assertDelayInRange(long delay, long min, long max, String message) {
        assertTrue(delay >= min && delay <= max,
                message + " — actual delay: " + delay);
    }

    private static RetryContext ctx(int attempt, ErrorClassification classification) {
        return new RetryContext(attempt, new NopTimeoutException(), classification, false);
    }

    // ========================================================================
    // RATE_LIMITED Retry-After floor (W2e-3 / design §3.7)
    // ========================================================================

    @Test
    void rateLimitedDelayRespectsRetryAfterAsFloor() {
        // RATE_LIMITED + retryAfterMs → delay = retryAfterMs + uniform(0, jitterCap),
        // 永不低于 retryAfterMs。baseDelay=100, maxDelay=1000, attempt=0 → jitterCap=100.
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 1000L);
        for (int i = 0; i < 50; i++) {
            long delay = p.shouldRetry(ctxRetryAfter(0, ErrorClassification.RATE_LIMITED, 2000L)).getDelayMs();
            assertTrue(delay >= 2000L && delay <= 2100L,
                    "RATE_LIMITED delay must be in [retryAfterMs, retryAfterMs+jitterCap]=[2000,2100]: " + delay);
        }
    }

    @Test
    void rateLimitedFloorGrowsWithAttemptJitterCap() {
        // attempt=1 → jitterCap = min(100*2, 1000) = 200 → delay ∈ [2000, 2200].
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 1000L);
        for (int i = 0; i < 50; i++) {
            long delay = p.shouldRetry(ctxRetryAfter(1, ErrorClassification.RATE_LIMITED, 2000L)).getDelayMs();
            assertTrue(delay >= 2000L && delay <= 2200L,
                    "RATE_LIMITED delay at attempt=1 must be in [2000, 2200]: " + delay);
        }
    }

    @Test
    void rateLimitedWithoutRetryAfterFallsBackToFullJitter() {
        // 无 Retry-After 提示 → 纯 full-jitter（不崩），delay ∈ [0, cap]，可低于任意 floor。
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 1000L);
        RetryOutcome r = p.shouldRetry(ctx(0, ErrorClassification.RATE_LIMITED));
        assertTrue(r.isRetry(), "RATE_LIMITED without retryAfterMs must still RETRY");
        assertTrue(r.getDelayMs() >= 0L && r.getDelayMs() <= 100L,
                "RATE_LIMITED without retryAfterMs → pure full-jitter in [0, baseDelay]: " + r.getDelayMs());
    }

    @Test
    void transientIsNotAffectedByRetryAfter() {
        // TRANSIENT 仍用纯 full-jitter，retryAfterMs 不影响它（delay 可远低于 retryAfterMs）。
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 1000L);
        long delay = p.shouldRetry(ctxRetryAfter(0, ErrorClassification.TRANSIENT, 10_000L)).getDelayMs();
        assertTrue(delay >= 0L && delay <= 100L,
                "TRANSIENT delay must stay in [0, baseDelay=100], ignoring retryAfterMs: " + delay);
    }

    @Test
    void rateLimitedRetryAfterExceedingMaxDelayIsHonored() {
        // retryAfterMs 超过 maxDelay 也要遵守（服务器明确要求的等待优先于 herd 抑制 cap）。
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 1000L);
        long delay = p.shouldRetry(ctxRetryAfter(0, ErrorClassification.RATE_LIMITED, 5000L)).getDelayMs();
        assertTrue(delay >= 5000L,
                "RATE_LIMITED delay must honor retryAfterMs even when it exceeds maxDelay: " + delay);
    }

    private static RetryContext ctxRetryAfter(int attempt, ErrorClassification classification, long retryAfterMs) {
        return new RetryContext(attempt, new NopTimeoutException(), classification, false, retryAfterMs);
    }
}
