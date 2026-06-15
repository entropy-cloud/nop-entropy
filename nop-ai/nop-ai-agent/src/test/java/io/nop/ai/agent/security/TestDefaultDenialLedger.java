package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 200 focused tests for {@link DefaultDenialLedger}:
 * verifies in-memory threshold-based counting and pause.
 */
public class TestDefaultDenialLedger {

    private static DenialRecord denial(String sessionId) {
        return DenialRecord.of(sessionId, "shell.exec",
                DenialLayerSource.LAYER1_TOOL_ACCESS, "test deny", "test-rule",
                System.currentTimeMillis());
    }

    @Test
    void recordDenialAccumulatesCount() {
        DefaultDenialLedger ledger = new DefaultDenialLedger(3);
        assertEquals(1, ledger.recordDenial(denial("s1")).getCount());
        assertEquals(2, ledger.recordDenial(denial("s1")).getCount());
        assertEquals(3, ledger.recordDenial(denial("s1")).getCount());
    }

    @Test
    void thresholdExceededAtConfiguredValue() {
        DefaultDenialLedger ledger = new DefaultDenialLedger(3);
        assertFalse(ledger.recordDenial(denial("s1")).isThresholdExceeded());
        assertFalse(ledger.recordDenial(denial("s1")).isThresholdExceeded());
        assertTrue(ledger.recordDenial(denial("s1")).isThresholdExceeded(),
                "threshold exceeded at count=3");
    }

    @Test
    void isPausedReflectsThreshold() {
        DefaultDenialLedger ledger = new DefaultDenialLedger(2);
        ledger.recordDenial(denial("s1"));
        assertFalse(ledger.isPaused("s1"));
        ledger.recordDenial(denial("s1"));
        assertTrue(ledger.isPaused("s1"));
    }

    @Test
    void resetClearsCountAndPause() {
        DefaultDenialLedger ledger = new DefaultDenialLedger(2);
        ledger.recordDenial(denial("s1"));
        ledger.recordDenial(denial("s1"));
        assertTrue(ledger.isPaused("s1"));
        assertEquals(2, ledger.getDenialCount("s1"));

        ledger.reset("s1");
        assertEquals(0, ledger.getDenialCount("s1"));
        assertFalse(ledger.isPaused("s1"));
    }

    @Test
    void perSessionCountsAreIndependent() {
        DefaultDenialLedger ledger = new DefaultDenialLedger(2);
        ledger.recordDenial(denial("sA"));
        ledger.recordDenial(denial("sA"));
        assertTrue(ledger.isPaused("sA"));
        assertFalse(ledger.isPaused("sB"));
        assertEquals(0, ledger.getDenialCount("sB"));
    }

    @Test
    void anonymousSessionNotCounted() {
        DefaultDenialLedger ledger = new DefaultDenialLedger(1);
        DenialRecord anonRecord = DenialRecord.of(null, "shell.exec",
                DenialLayerSource.LAYER1_TOOL_ACCESS, "anon", "rule", 0L);
        DenialRecordOutcome outcome = ledger.recordDenial(anonRecord);
        assertEquals(0, outcome.getCount());
        assertFalse(outcome.isThresholdExceeded());
        assertFalse(ledger.isPaused(null));
        assertEquals(0, ledger.getDenialCount(null));
    }

    @Test
    void defaultThresholdIsThree() {
        DefaultDenialLedger ledger = new DefaultDenialLedger();
        assertEquals(3, ledger.getDenialThreshold());
    }
}
