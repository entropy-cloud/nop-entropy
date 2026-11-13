package io.nop.ai.core.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carrier for 3.1-C7-005 (plan 2556 裁定 G): the probe generation token. A
 * receipt handed back with a stale generation (escaped past a later probe
 * re-admission via the probe-timeout escape) is WARN-discarded without any
 * breaker state flip; a fresh-generation receipt is effective; the probe
 * timeout is configurable via the 4-arg constructor (default raised 30s →
 * 90s); generation-less receipts fall back to legacy semantics.
 */
public class TestProbeGenerationToken {

    @Test
    void defaultProbeTimeoutRaisedTo90s() {
        assertEquals(90_000L, ThresholdBreaker.DEFAULT_PROBE_TIMEOUT_MS,
                "裁定 G: DEFAULT_PROBE_TIMEOUT_MS must be 90s (was 30s)");
        assertEquals(90_000L, new ThresholdBreaker().getProbeTimeoutMs());
    }

    @Test
    void probeTimeoutConfigurableViaConstructor() {
        ThresholdBreaker b = new ThresholdBreaker(3, 60_000L, 123_456L, 0L);
        assertEquals(123_456L, b.getProbeTimeoutMs(), "the ctor probe-timeout param must be honored");
        assertEquals(0L, b.getEntryTtlMs());
    }

    @Test
    void freshGenerationReceiptIsEffective() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 50L, 90_000L, 0L);
        b.recordFailure("openai:gpt-4"); // OPEN
        Thread.sleep(80L);
        assertTrue(b.allowCall("openai:gpt-4")); // → HALF_OPEN, probe admitted
        long gen = b.currentGeneration("openai:gpt-4");
        assertTrue(gen >= 1, "probe admission must bump the generation");
        b.recordSuccess("openai:gpt-4", gen);
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"),
                "a fresh-generation success receipt must close the circuit");
    }

    @Test
    void staleGenerationReceiptIsDiscardedWithoutStateFlip() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 20L, 50L, 0L);
        b.recordFailure("openai:gpt-4"); // OPEN
        Thread.sleep(40L);
        assertTrue(b.allowCall("openai:gpt-4")); // probe #1
        long gen1 = b.currentGeneration("openai:gpt-4");

        // Probe #1 never reports; after probeTimeoutMs the escape admits probe #2.
        Thread.sleep(80L);
        assertTrue(b.allowCall("openai:gpt-4")); // escape → probe #2
        long gen2 = b.currentGeneration("openai:gpt-4");
        assertTrue(gen2 > gen1, "the escape re-admission must bump the generation");

        // The escaped probe #1 now reports FAILURE with its stale token.
        b.recordFailure("openai:gpt-4", gen1, new RuntimeException("escaped probe finally failed"));
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"),
                "a stale failure receipt must be discarded (no OPEN flip)");
        assertFalse(b.allowCall("openai:gpt-4"),
                "probe #2 must still hold the slot after the stale receipt was discarded");

        // The current probe #2 reports failure with its fresh token → effective.
        b.recordFailure("openai:gpt-4", gen2, new RuntimeException("probe #2 failed"));
        assertEquals(CircuitState.OPEN, b.getState("openai:gpt-4"),
                "a fresh-generation failure receipt must flip HALF_OPEN → OPEN");
    }

    @Test
    void staleSuccessReceiptIsDiscarded() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 20L, 50L, 0L);
        b.recordFailure("openai:gpt-4");
        Thread.sleep(40L);
        assertTrue(b.allowCall("openai:gpt-4")); // probe #1
        long gen1 = b.currentGeneration("openai:gpt-4");
        Thread.sleep(80L);
        assertTrue(b.allowCall("openai:gpt-4")); // escape → probe #2
        long gen2 = b.currentGeneration("openai:gpt-4");

        b.recordSuccess("openai:gpt-4", gen1);
        assertEquals(CircuitState.HALF_OPEN, b.getState("openai:gpt-4"),
                "a stale success receipt must not close the circuit while probe #2 is in flight");
        // probe #2 slot still held:
        assertFalse(b.allowCall("openai:gpt-4"));

        b.recordSuccess("openai:gpt-4", gen2);
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"));
    }

    @Test
    void generationUnknownFallsBackToLegacySemantics() throws InterruptedException {
        ThresholdBreaker b = new ThresholdBreaker(1, 20L, 90_000L, 0L);
        b.recordFailure("openai:gpt-4"); // OPEN
        Thread.sleep(40L);
        assertTrue(b.allowCall("openai:gpt-4")); // → HALF_OPEN probe
        // Un-upgraded caller: a GENERATION_UNKNOWN receipt keeps legacy semantics —
        // a legacy success on HALF_OPEN resets to CLOSED exactly as before 裁定 G.
        b.recordSuccess("openai:gpt-4", ICircuitBreaker.GENERATION_UNKNOWN);
        assertEquals(CircuitState.CLOSED, b.getState("openai:gpt-4"),
                "the compat fallback must apply legacy semantics (HALF_OPEN success → CLOSED)");
    }

    @Test
    void currentGenerationDefaultsOnUntrackedKey() {
        ThresholdBreaker b = new ThresholdBreaker();
        assertEquals(0L, b.currentGeneration("never-seen:model"),
                "an untracked key reports generation 0 (fresh)");
    }

    @Test
    void iCircuitBreakerDefaultMethodsKeepCompatFallback() {
        // An implementation that does not upgrade (AlwaysClosed shape): default
        // currentGeneration = -1, gen-aware record* fall back to legacy no-ops.
        ICircuitBreaker breaker = AlwaysClosed.alwaysClosed();
        assertEquals(ICircuitBreaker.GENERATION_UNKNOWN, breaker.currentGeneration("any:model"));
        breaker.recordSuccess("any:model", 42L); // must not throw
        breaker.recordFailure("any:model", 42L, new RuntimeException("x")); // must not throw
        assertEquals(CircuitState.CLOSED, breaker.getState("any:model"));
    }
}
