package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.api.core.exceptions.NopTimeoutException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plan 207 (L3-2) Phase 1 focused test for {@link NoRetryPolicy} (Minimum
 * Rules #25). Verifies the shipped default unconditionally returns STOP for
 * every error classification and every attempt index, preserving the
 * engine's pre-plan-207 zero-retry behaviour.
 */
public class TestNoRetryPolicy {

    @Test
    void noRetryReturnsSingletonViaFactory() {
        IRetryPolicy a = NoRetryPolicy.noRetry();
        IRetryPolicy b = NoRetryPolicy.noRetry();
        assertSame(a, b, "noRetry() must return the same singleton instance");
    }

    @Test
    void noRetryAlwaysStopsForTransient() {
        RetryContext ctx = new RetryContext(
                0, new NopTimeoutException(), ErrorClassification.TRANSIENT, false);
        RetryOutcome outcome = NoRetryPolicy.noRetry().shouldRetry(ctx);
        assertEquals(RetryDecision.STOP, outcome.getDecision(),
                "NoRetry must STOP for TRANSIENT errors");
        assertEquals(0L, outcome.getDelayMs(),
                "STOP outcome must have zero delay");
        assertFalse(outcome.isRetry());
        assertFalse(outcome.isFallback());
    }

    @Test
    void noRetryAlwaysStopsForRateLimited() {
        RetryContext ctx = new RetryContext(
                2, new RuntimeException("429"), ErrorClassification.RATE_LIMITED, false);
        RetryOutcome outcome = NoRetryPolicy.noRetry().shouldRetry(ctx);
        assertEquals(RetryDecision.STOP, outcome.getDecision(),
                "NoRetry must STOP for RATE_LIMITED errors regardless of attempt index");
    }

    @Test
    void noRetryAlwaysStopsForNonTransient() {
        NopAiAgentException paramError = new NopAiAgentException("400 bad request");
        RetryContext ctx = new RetryContext(
                0, paramError, ErrorClassification.NON_TRANSIENT, false);
        RetryOutcome outcome = NoRetryPolicy.noRetry().shouldRetry(ctx);
        assertEquals(RetryDecision.STOP, outcome.getDecision(),
                "NoRetry must STOP for NON_TRANSIENT errors");
    }

    @Test
    void noRetryAlwaysStopsForQuotaExceeded() {
        RetryContext ctx = new RetryContext(
                5, new RuntimeException("quota"), ErrorClassification.QUOTA_EXCEEDED, false);
        RetryOutcome outcome = NoRetryPolicy.noRetry().shouldRetry(ctx);
        assertEquals(RetryDecision.STOP, outcome.getDecision(),
                "NoRetry must STOP for QUOTA_EXCEEDED errors at any attempt index");
    }
}
