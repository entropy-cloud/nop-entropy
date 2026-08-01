package io.nop.ai.agent.reliability;

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
 * retry up to maxAttempts with exponential backoff; NON_TRANSIENT /
 * QUOTA_EXCEEDED fail fast; maxAttempts exhaustion → STOP; backoff formula
 * and cap.
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
    void quotaExceededFailsFastImmediately() {
        StandardRetryPolicy p = new StandardRetryPolicy(5, 10L, 1000L);
        RetryOutcome r = p.shouldRetry(ctx(0, ErrorClassification.QUOTA_EXCEEDED));
        assertTrue(r.isStop(), "QUOTA_EXCEEDED must STOP immediately (quota not replenished by retrying)");
        assertFalse(r.isRetry());
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
}
