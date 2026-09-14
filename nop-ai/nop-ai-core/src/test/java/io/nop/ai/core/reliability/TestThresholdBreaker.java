package io.nop.ai.core.reliability;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 210 (L3-1) Phase 2 focused test for {@link ThresholdBreaker} (Minimum
 * Rules #25). Verifies each state-transition path of the three-state
 * consecutive-failure breaker + constructor validation + per-model isolation
 * + thread safety under concurrent access to the same model-key.
 */
public class TestThresholdBreaker {

    // ========================================================================
    // Constructor validation
    // ========================================================================

    @Test
    void constructorRejectsInvalidThreshold() {
        assertThrows(NopAiCoreException.class,
                () -> new ThresholdBreaker(0, 1000L),
                "failureThreshold must be >= 1");
        assertThrows(NopAiCoreException.class,
                () -> new ThresholdBreaker(-1, 1000L),
                "failureThreshold must be >= 1");
    }

    /**
     * P3-MA3-1 focused value-level assertion (plan 2026-08-01-0936-1): the
     * converted validation guard must fail fast with the module ErrorCode and
     * preserve the verbatim English message via the {@code msg} param.
     */
    @Test
    void validationFailureCarriesErrorCodeAndVerbatimMessage() {
        NopAiCoreException e = assertThrows(NopAiCoreException.class,
                () -> new ThresholdBreaker(0, 1000L));
        assertEquals(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG.getErrorCode(),
                e.getErrorCode());
        assertEquals("ThresholdBreaker failureThreshold must be >= 1: 0",
                e.getParams().get(NopAiCoreErrors.ARG_MSG));
        assertTrue(e.getMessage().contains("ThresholdBreaker failureThreshold must be >= 1: 0"));
    }

    @Test
    void constructorRejectsNegativeCooldown() {
        assertThrows(NopAiCoreException.class,
                () -> new ThresholdBreaker(3, -1L),
                "cooldownMs must be >= 0");
    }

    @Test
    void constructorRejectsNegativeProbeTimeout() {
        assertThrows(NopAiCoreException.class,
                () -> new ThresholdBreaker(3, 1000L, -1L),
                "probeTimeoutMs must be >= 0");
    }

    @Test
    void defaultsAreExposed() {
        ThresholdBreaker b = new ThresholdBreaker();
        assertEquals(ThresholdBreaker.DEFAULT_FAILURE_THRESHOLD, b.getFailureThreshold());
        assertEquals(ThresholdBreaker.DEFAULT_COOLDOWN_MS, b.getCooldownMs());
        assertEquals(ThresholdBreaker.DEFAULT_PROBE_TIMEOUT_MS, b.getProbeTimeoutMs());
    }

    @Test
    void nullModelKeyRejected() {
        ThresholdBreaker b = new ThresholdBreaker();
        assertThrows(NopAiCoreException.class, () -> b.allowCall(null));
        assertThrows(NopAiCoreException.class, () -> b.getState(null));
        assertThrows(NopAiCoreException.class, () -> b.recordSuccess(null));
        assertThrows(NopAiCoreException.class, () -> b.recordFailure(null));
    }

    // ========================================================================
    // CLOSED consecutive failures → OPEN (at threshold)
    // ========================================================================

    @Test
    void closedStaysClosedBelowThreshold() {
        ThresholdBreaker b = new ThresholdBreaker(3, 60_000L);
        // Two failures with threshold=3 → still CLOSED, calls still allowed.
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"),
                "Two failures with threshold=3 must keep the breaker CLOSED");
        assertTrue(b.allowCall("openai:gpt-4"),
                "Calls must be allowed while CLOSED");
    }

    @Test
    void closedTripsToOpenAtThreshold() {
        ThresholdBreaker b = new ThresholdBreaker(3, 60_000L);
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        assertEquals(CircuitState.OPEN, b.getState("openai:gpt-4"),
                "Three consecutive failures with threshold=3 must trip to OPEN");
        assertFalse(b.allowCall("openai:gpt-4"),
                "Calls must be rejected while OPEN");
    }

    @Test
    void successResetsConsecutiveFailureCounter() {
        ThresholdBreaker b = new ThresholdBreaker(3, 60_000L);
        b.recordFailure("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        // A success resets the counter, so the breaker must not trip on the
        // next failure even though there were 2 prior failures.
        b.recordSuccess("openai:gpt-4");
        b.recordFailure("openai:gpt-4");
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"),
                "A success must reset the consecutive-failure counter");
        assertTrue(b.allowCall("openai:gpt-4"));
    }

    // ========================================================================
    // OPEN cooldown → HALF_OPEN
    // ========================================================================

    @Test
    void openRejectsCallsWithinCooldown() {
        ThresholdBreaker b = new ThresholdBreaker(1, 60_000L);
        b.recordFailure("openai:gpt-4"); // threshold=1 → OPEN immediately
        // Within the 60s cooldown, calls must be rejected and state stays OPEN.
        assertFalse(b.allowCall("openai:gpt-4"),
                "Calls within cooldown must be rejected");
        assertEquals(CircuitState.OPEN, b.getState("openai:gpt-4"),
                "State must stay OPEN within cooldown");
    }

    @Test
    void openTransitionsToHalfOpenAfterCooldown() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 50L);
        b.recordFailure("openai:gpt-4"); // threshold=1 → OPEN
        assertEquals(CircuitState.OPEN, b.getState("openai:gpt-4"));
        // Wait for the cooldown to elapse.
        Thread.sleep(80L);
        // The next allowCall must lazily transition to HALF_OPEN and admit
        // the caller as the single probe.
        boolean allowed = b.allowCall("openai:gpt-4");
        assertTrue(allowed, "After cooldown, the first call must be admitted as the HALF_OPEN probe");
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"),
                "After cooldown + allowCall, state must be HALF_OPEN");
    }

    @Test
    void zeroCooldownProbesOnNextCall() {
        ThresholdBreaker b = new ThresholdBreaker(1, 0L);
        b.recordFailure("openai:gpt-4"); // threshold=1 → OPEN
        // cooldown=0 → the very next call must probe (transition to HALF_OPEN).
        assertTrue(b.allowCall("openai:gpt-4"),
                "cooldown=0 must admit the next call as a probe immediately");
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"));
    }

    // ========================================================================
    // HALF_OPEN success → CLOSED (reset)
    // ========================================================================

    @Test
    void halfOpenSuccessTransitionsToClosed() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 50L);
        b.recordFailure("openai:gpt-4"); // OPEN
        Thread.sleep(80L);
        assertTrue(b.allowCall("openai:gpt-4")); // → HALF_OPEN, probe in flight
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"));
        // Probe succeeds → CLOSED, counter cleared.
        b.recordSuccess("openai:gpt-4");
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"),
                "A successful HALF_OPEN probe must transition to CLOSED");
        assertTrue(b.allowCall("openai:gpt-4"),
                "After CLOSED reset, calls must be allowed");
    }

    // ========================================================================
    // HALF_OPEN failure → OPEN (restart cooldown)
    // ========================================================================

    @Test
    void halfOpenFailureTransitionsBackToOpen() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 50L);
        b.recordFailure("openai:gpt-4"); // OPEN
        Thread.sleep(80L);
        assertTrue(b.allowCall("openai:gpt-4")); // → HALF_OPEN, probe in flight
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"));
        // Probe fails → back to OPEN, cooldown restarted.
        b.recordFailure("openai:gpt-4");
        assertEquals(CircuitState.OPEN, b.getState("openai:gpt-4"),
                "A failed HALF_OPEN probe must transition back to OPEN");
        assertFalse(b.allowCall("openai:gpt-4"),
                "After OPEN restart, calls within the new cooldown must be rejected");
    }

    // ========================================================================
    // HALF_OPEN probe exclusivity: concurrent callers rejected
    // ========================================================================

    @Test
    void halfOpenProbeIsExclusive() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 50L);
        b.recordFailure("openai:gpt-4"); // OPEN
        Thread.sleep(80L);
        // First caller wins the probe slot → HALF_OPEN, admitted.
        assertTrue(b.allowCall("openai:gpt-4"));
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"));
        // Second concurrent caller (probe already in flight) → rejected.
        assertFalse(b.allowCall("openai:gpt-4"),
                "While the HALF_OPEN probe is in flight, concurrent callers must be rejected");
        assertFalse(b.allowCall("openai:gpt-4"),
                "While the HALF_OPEN probe is in flight, further callers must be rejected");
        // Probe completes (success) → slot released, next caller admitted.
        b.recordSuccess("openai:gpt-4");
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"));
        assertTrue(b.allowCall("openai:gpt-4"));
    }

    // ========================================================================
    // Per-model isolation: model A tripping does not affect model B
    // ========================================================================

    @Test
    void perModelStateIsIsolated() {
        ThresholdBreaker b = new ThresholdBreaker(2, 60_000L);
        // Trip model A.
        b.recordFailure("provider-a:model-a");
        b.recordFailure("provider-a:model-a");
        assertEquals(CircuitState.OPEN, b.getState("provider-a:model-a"));
        assertFalse(b.allowCall("provider-a:model-a"));
        // Model B must be unaffected.
        assertEquals(CircuitState.CLOSED, b.getState("provider-b:model-b"),
                "Tripping model A must not affect model B");
        assertTrue(b.allowCall("provider-b:model-b"),
                "Calls to model B must be allowed while model A is tripped");
        b.recordFailure("provider-b:model-b");
        assertEquals(CircuitState.CLOSED, b.getState("provider-b:model-b"),
                "Model B's counter is independent (1 failure, threshold=2)");
    }

    // ========================================================================
    // Thread safety: concurrent failures to same model-key trip exactly once
    // and concurrent allowCall calls never admit two probes
    // ========================================================================

    @Test
    void concurrentFailuresTripConsistently() throws InterruptedException {
        // threshold=5, 100 threads each record 1 failure on the same key.
        // After all complete the breaker must be OPEN (5+ consecutive failures).
        final ThresholdBreaker b = new ThresholdBreaker(5, 60_000L);
        final String key = "openai:gpt-4";
        int nThreads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(nThreads);
        try {
            for (int i = 0; i < nThreads; i++) {
                pool.submit(() -> {
                    try {
                        b.recordFailure(key);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(15, TimeUnit.SECONDS),
                    "All threads must complete within the timeout");
        } finally {
            pool.shutdownNow();
        }
        // 100 >= 5 → must be OPEN.
        assertEquals(CircuitState.OPEN, b.getState(key),
                "After 100 concurrent failures with threshold=5, breaker must be OPEN");
        assertFalse(b.allowCall(key),
                "Calls must be rejected after the breaker tripped");
    }

    @Test
    void concurrentAllowCallAdmitsAtMostOneHalfOpenProbe() throws InterruptedException {
        // threshold=1 → OPEN after one failure. cooldown=0 so every allowCall
        // sees the cooldown as elapsed and tries to transition/probe. Out of
        // N concurrent callers, at most ONE can be admitted as the probe at
        // a time; the rest must be rejected.
        final ThresholdBreaker b = new ThresholdBreaker(1, 0L);
        final String key = "openai:gpt-4";
        b.recordFailure(key); // OPEN
        int nThreads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(nThreads);
        AtomicInteger admitted = new AtomicInteger(0);
        try {
            for (int i = 0; i < nThreads; i++) {
                pool.submit(() -> {
                    try {
                        if (b.allowCall(key)) {
                            admitted.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(15, TimeUnit.SECONDS),
                    "All threads must complete within the timeout");
        } finally {
            pool.shutdownNow();
        }
        // At most one caller can be admitted as the probe at any given time.
        // (If the probe was consumed and not released, exactly 1; if a
        // caller's probe was consumed and then released via recordSuccess/
        // recordFailure before another caller checked, more than 1 could be
        // admitted sequentially — but the state must be consistent at each
        // instant. With no recording between calls, exactly 1 is admitted.)
        assertTrue(admitted.get() >= 1,
                "At least one caller must be admitted as the probe");
        assertTrue(admitted.get() <= 1,
                "At most one caller must be admitted as the probe at a time "
                        + "(got " + admitted.get() + ")");
        assertEquals(CircuitState.HALF_OPEN, b.getState(key),
                "After the probe is admitted and not completed, state must be HALF_OPEN");
    }

    // ========================================================================
    // HALF_OPEN probe-timeout escape: a probe that never reports back must not
    // wedge the breaker in HALF_OPEN forever (P2-REL)
    // ========================================================================

    @Test
    void stuckProbeTimesOutAndSlotIsRetaken() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 20L, 50L);
        b.recordFailure("openai:gpt-4"); // threshold=1 → OPEN
        Thread.sleep(40L); // cooldown elapsed
        assertTrue(b.allowCall("openai:gpt-4")); // → HALF_OPEN, probe in flight
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"));
        assertFalse(b.allowCall("openai:gpt-4"),
                "while the probe is in flight (within probeTimeout), callers must be rejected");

        // The probe never reports (no recordSuccess/recordFailure). After
        // probeTimeoutMs the next caller must be admitted as the new probe
        // (the escape path) — the breaker recovers instead of wedging.
        Thread.sleep(80L);
        assertTrue(b.allowCall("openai:gpt-4"),
                "a stuck probe must be escaped after probeTimeoutMs: the next caller re-takes the slot");
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"),
                "the escape keeps HALF_OPEN (no illegal state transition)");

        // The new probe reports success → normal CLOSED reset (unchanged).
        b.recordSuccess("openai:gpt-4");
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"));
        assertTrue(b.allowCall("openai:gpt-4"),
                "after the escaped probe succeeds, calls are allowed again");
    }

    @Test
    void stuckProbeEscapeKeepsProbeExclusivity() throws InterruptedException {
        // After an escape, the re-taken slot is exclusive again until the new
        // probe reports or times out — no double probe concurrency.
        ThresholdBreaker b = new ThresholdBreaker(1, 20L, 50L);
        b.recordFailure("openai:gpt-4");
        Thread.sleep(40L);
        assertTrue(b.allowCall("openai:gpt-4")); // probe #1
        Thread.sleep(80L);
        assertTrue(b.allowCall("openai:gpt-4")); // escape: probe #2 takes the slot
        assertFalse(b.allowCall("openai:gpt-4"),
                "after the escape re-takes the slot, concurrent callers are rejected again (exclusivity kept)");
    }

    @Test
    void probeTimeoutZeroDisablesEscape() throws InterruptedException {
        // probeTimeoutMs = 0 = escape disabled (explicit opt-out): a stuck
        // probe keeps rejecting callers (pre-P2-REL behaviour).
        ThresholdBreaker b = new ThresholdBreaker(1, 20L, 0L);
        b.recordFailure("openai:gpt-4");
        Thread.sleep(40L);
        assertTrue(b.allowCall("openai:gpt-4")); // probe in flight
        Thread.sleep(80L); // far past any plausible timeout
        assertFalse(b.allowCall("openai:gpt-4"),
                "probeTimeoutMs=0 must disable the escape (stuck probe still blocks)");
    }

    // ========================================================================
    // Untracked model-key defaults to CLOSED
    // ========================================================================

    @Test
    void untrackedModelKeyDefaultsToClosed() {
        ThresholdBreaker b = new ThresholdBreaker();
        assertEquals(CircuitState.CLOSED, b.getState("never-seen:model"),
                "An untracked model-key must report CLOSED");
        assertTrue(b.allowCall("never-seen:model"),
                "An untracked model-key must be allowed");
        // recordSuccess on an untracked key is a no-op (no entry created).
        b.recordSuccess("never-seen-success:model");
        assertEquals(CircuitState.CLOSED, b.getState("never-seen-success:model"));
    }
}
