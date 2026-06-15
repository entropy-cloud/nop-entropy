package io.nop.ai.agent.reliability;

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
        assertThrows(IllegalArgumentException.class,
                () -> new StandardRetryPolicy(0, 100L, 1000L));
    }

    @Test
    void rejectsNegativeBaseDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new StandardRetryPolicy(3, -1L, 1000L));
    }

    @Test
    void rejectsMaxDelayBelowBaseDelay() {
        assertThrows(IllegalArgumentException.class,
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
    // Exponential backoff formula and cap
    // ========================================================================

    @Test
    void backoffIsExponential() {
        StandardRetryPolicy p = new StandardRetryPolicy(5, 100L, 10_000L);
        // attempt 0 → 100 * 2^0 = 100
        assertEquals(100L, p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).getDelayMs(),
                "attempt 0 backoff = baseDelay * 2^0 = 100");
        // attempt 1 → 100 * 2^1 = 200
        assertEquals(200L, p.shouldRetry(ctx(1, ErrorClassification.TRANSIENT)).getDelayMs(),
                "attempt 1 backoff = baseDelay * 2^1 = 200");
        // attempt 2 → 100 * 2^2 = 400
        assertEquals(400L, p.shouldRetry(ctx(2, ErrorClassification.TRANSIENT)).getDelayMs(),
                "attempt 2 backoff = baseDelay * 2^2 = 400");
        // attempt 3 → 100 * 2^3 = 800
        assertEquals(800L, p.shouldRetry(ctx(3, ErrorClassification.TRANSIENT)).getDelayMs(),
                "attempt 3 backoff = baseDelay * 2^3 = 800");
    }

    @Test
    void backoffCappedAtMaxDelay() {
        // baseDelay=1000, maxDelay=3000 → attempt 0=1000, 1=2000, 2=4000 capped to 3000
        StandardRetryPolicy p = new StandardRetryPolicy(5, 1000L, 3000L);
        assertEquals(1000L, p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).getDelayMs());
        assertEquals(2000L, p.shouldRetry(ctx(1, ErrorClassification.TRANSIENT)).getDelayMs());
        assertEquals(3000L, p.shouldRetry(ctx(2, ErrorClassification.TRANSIENT)).getDelayMs(),
                "attempt 2 backoff must be capped at maxDelay=3000");
        assertEquals(3000L, p.shouldRetry(ctx(3, ErrorClassification.TRANSIENT)).getDelayMs(),
                "attempt 3 backoff must stay capped at maxDelay=3000");
    }

    @Test
    void zeroBaseDelayMeansImmediateRetry() {
        StandardRetryPolicy p = new StandardRetryPolicy(3, 0L, 1000L);
        assertEquals(0L, p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).getDelayMs(),
                "baseDelay=0 means retry immediately (no wait)");
        assertTrue(p.shouldRetry(ctx(0, ErrorClassification.TRANSIENT)).isRetry());
    }

    private static RetryContext ctx(int attempt, ErrorClassification classification) {
        return new RetryContext(attempt, new NopTimeoutException(), classification, false);
    }
}
