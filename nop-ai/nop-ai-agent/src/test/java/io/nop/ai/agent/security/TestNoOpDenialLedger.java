package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link NoOpDenialLedger} contract (design §6.2 default): no
 * denials are counted, no sessions are paused, and the pass-through is a
 * semantically-correct no-count outcome (not a silent skip).
 */
public class TestNoOpDenialLedger {

    @Test
    void recordDenialReturnsZeroCountAndNotExceeded() {
        IDenialLedger ledger = NoOpDenialLedger.noOp();
        DenialRecord record = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "hardcoded deny", "deny-by-default", 1000L);
        DenialRecordOutcome outcome = ledger.recordDenial(record);
        assertEquals(0, outcome.getCount(),
                "NoOpDenialLedger must not count denials");
        assertFalse(outcome.isThresholdExceeded(),
                "NoOpDenialLedger must never report threshold exceeded");
    }

    @Test
    void isPausedAlwaysReturnsFalse() {
        IDenialLedger ledger = NoOpDenialLedger.noOp();
        assertFalse(ledger.isPaused("s1"));
        assertFalse(ledger.isPaused("s2"));
        assertFalse(ledger.isPaused(null),
                "NoOpDenialLedger.isPaused must return false for null session");
    }

    @Test
    void getDenialCountAlwaysReturnsZero() {
        IDenialLedger ledger = NoOpDenialLedger.noOp();
        assertEquals(0, ledger.getDenialCount("s1"));
        assertEquals(0, ledger.getDenialCount(null));
    }

    @Test
    void resetDoesNotThrow() {
        IDenialLedger ledger = NoOpDenialLedger.noOp();
        ledger.reset("s1");
        ledger.reset(null);
        // still consistent after reset
        assertEquals(0, ledger.getDenialCount("s1"));
        assertFalse(ledger.isPaused("s1"));
    }

    @Test
    void multipleRecordDenialsStillNotPaused() {
        IDenialLedger ledger = NoOpDenialLedger.noOp();
        for (int i = 0; i < 100; i++) {
            DenialRecord record = DenialRecord.of(
                    "s1", "shell.exec", DenialLayerSource.LAYER3_APPROVAL_GATE,
                    "no", "layer3_approval_gate", 1000L + i);
            DenialRecordOutcome outcome = ledger.recordDenial(record);
            assertEquals(0, outcome.getCount());
            assertFalse(outcome.isThresholdExceeded());
        }
        assertFalse(ledger.isPaused("s1"),
                "Even after many recordDenial calls, NoOpDenialLedger must not pause");
        assertEquals(0, ledger.getDenialCount("s1"));
    }

    @Test
    void singletonInstanceShared() {
        IDenialLedger a = NoOpDenialLedger.noOp();
        IDenialLedger b = NoOpDenialLedger.noOp();
        assertSame(a, b, "NoOpDenialLedger.noOp() must return a shared singleton");
    }

    /**
     * Sanity check: a functional (non-default) ledger producing real counts
     * and threshold-pause is observable through the contract surface,
     * confirming the {@link IDenialLedger} contract is not hollow — the
     * dispatch path (Phase 2) has real state to act on.
     */
    @Test
    void functionalLedgerCountingAndPauseIsObservableThroughContract() {
        IDenialLedger counting = new InMemoryCountingLedger(2);

        DenialRecord r1 = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "no", "rule", 1000L);
        DenialRecordOutcome o1 = counting.recordDenial(r1);
        assertEquals(1, o1.getCount(), "first denial -> count=1");
        assertFalse(o1.isThresholdExceeded(), "threshold=2, first deny not exceeded");
        assertFalse(counting.isPaused("s1"));

        DenialRecord r2 = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER3_APPROVAL_GATE,
                "no", "layer3_approval_gate", 1001L);
        DenialRecordOutcome o2 = counting.recordDenial(r2);
        assertEquals(2, o2.getCount(), "second denial -> count=2");
        assertTrue(o2.isThresholdExceeded(), "threshold=2, second deny exceeds");
        assertTrue(counting.isPaused("s1"));

        // Per-session independence: session s2 must not be affected by s1.
        assertEquals(0, counting.getDenialCount("s2"));
        assertFalse(counting.isPaused("s2"));

        // Reset clears the paused state.
        counting.reset("s1");
        assertFalse(counting.isPaused("s1"));
        assertEquals(0, counting.getDenialCount("s1"));
    }

    /**
     * Test-internal functional ledger: thread-safe per-session counter with
     * a configurable threshold. Used to prove the {@link IDenialLedger}
     * contract surface can carry real counting/pausing state. This same
     * shape is reused in the Phase 2 end-to-end dispatch-path tests.
     */
    static final class InMemoryCountingLedger implements IDenialLedger {
        private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> counts =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Set<String> paused =
                java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        private final int threshold;

        InMemoryCountingLedger(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public DenialRecordOutcome recordDenial(DenialRecord record) {
            String sid = record.getSessionId();
            if (sid == null) {
                return DenialRecordOutcome.of(0, false);
            }
            int c = counts.computeIfAbsent(sid, k -> new java.util.concurrent.atomic.AtomicInteger())
                    .incrementAndGet();
            boolean exceeded = c >= threshold;
            if (exceeded) {
                paused.add(sid);
            }
            return DenialRecordOutcome.of(c, exceeded);
        }

        @Override
        public boolean isPaused(String sessionId) {
            return sessionId != null && paused.contains(sessionId);
        }

        @Override
        public int getDenialCount(String sessionId) {
            if (sessionId == null) {
                return 0;
            }
            java.util.concurrent.atomic.AtomicInteger c = counts.get(sessionId);
            return c == null ? 0 : c.get();
        }

        @Override
        public void reset(String sessionId) {
            if (sessionId == null) {
                return;
            }
            counts.remove(sessionId);
            paused.remove(sessionId);
        }
    }

    // ========================================================================
    // DenialRecord value semantics
    // ========================================================================

    @Test
    void denialRecordFactoryCapturesAllFields() {
        DenialRecord r = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER2_SECURITY_POLICY,
                "restricted by matrix", "layer2_permission_matrix", 12345L);
        assertEquals("s1", r.getSessionId());
        assertEquals("shell.exec", r.getToolName());
        assertEquals(DenialLayerSource.LAYER2_SECURITY_POLICY, r.getLayerSource());
        assertEquals("restricted by matrix", r.getReason());
        assertEquals("layer2_permission_matrix", r.getMatchedRule());
        assertEquals(12345L, r.getTimestamp());
    }

    @Test
    void denialRecordFactoryRejectsNullLayerSource() {
        try {
            DenialRecord.of("s1", "shell.exec", null, "no", "rule", 1L);
            org.junit.jupiter.api.Assertions.fail(
                    "DenialRecord.of must reject null layerSource");
        } catch (IllegalArgumentException expected) {
            // expected: layer source must be present so each denial is attributable
        }
    }

    @Test
    void denialRecordNullOptionalFieldsAreAllowed() {
        DenialRecord r = DenialRecord.of(
                null, null, DenialLayerSource.LAYER1_TOOL_ACCESS,
                null, null, 0L);
        assertNull(r.getSessionId());
        assertNull(r.getToolName());
        assertNull(r.getReason());
        assertNull(r.getMatchedRule());
    }

    @Test
    void denialRecordEqualityAndHashCode() {
        DenialRecord a = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER3_APPROVAL_GATE,
                "no", "layer3_approval_gate", 1000L);
        DenialRecord b = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER3_APPROVAL_GATE,
                "no", "layer3_approval_gate", 1000L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void denialRecordDifferentLayersAreNotEqual() {
        DenialRecord layer1 = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "no", "rule", 1000L);
        DenialRecord layer2 = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER2_SECURITY_POLICY,
                "no", "rule", 1000L);
        DenialRecord layer3 = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER3_APPROVAL_GATE,
                "no", "rule", 1000L);
        assertNotEquals(layer1, layer2);
        assertNotEquals(layer2, layer3);
        assertNotEquals(layer1, layer3);
    }

    @Test
    void denialRecordDifferentReasonsAreNotEqual() {
        DenialRecord a = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "reason-a", "rule", 1000L);
        DenialRecord b = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "reason-b", "rule", 1000L);
        assertNotEquals(a, b);
    }

    @Test
    void denialRecordEqualsSelfAndNullAndOtherType() {
        DenialRecord r = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER1_TOOL_ACCESS,
                "no", "rule", 1L);
        assertEquals(r, r);
        assertNotEquals(null, r);
        assertNotEquals("not-a-record", r);
    }

    @Test
    void denialRecordToStringContainsLayerSource() {
        DenialRecord r = DenialRecord.of(
                "s1", "shell.exec", DenialLayerSource.LAYER3_APPROVAL_GATE,
                "no", "layer3_approval_gate", 1L);
        String s = r.toString();
        assertTrue(s.contains("LAYER3_APPROVAL_GATE"));
        assertTrue(s.contains("shell.exec"));
    }

    // ========================================================================
    // DenialRecordOutcome value semantics
    // ========================================================================

    @Test
    void outcomeFactoryCapturesCountAndThreshold() {
        DenialRecordOutcome o = DenialRecordOutcome.of(5, true);
        assertEquals(5, o.getCount());
        assertTrue(o.isThresholdExceeded());
    }

    @Test
    void outcomeFactoryRejectsNegativeCount() {
        try {
            DenialRecordOutcome.of(-1, false);
            org.junit.jupiter.api.Assertions.fail(
                    "DenialRecordOutcome.of must reject negative count");
        } catch (IllegalArgumentException expected) {
            // expected: counts cannot be negative
        }
    }

    @Test
    void outcomeEqualityAndHashCode() {
        DenialRecordOutcome a = DenialRecordOutcome.of(3, true);
        DenialRecordOutcome b = DenialRecordOutcome.of(3, true);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void outcomeDifferentCountsAreNotEqual() {
        DenialRecordOutcome a = DenialRecordOutcome.of(2, false);
        DenialRecordOutcome b = DenialRecordOutcome.of(3, true);
        assertNotEquals(a, b);
    }

    @Test
    void outcomeSameCountDifferentThresholdAreNotEqual() {
        DenialRecordOutcome a = DenialRecordOutcome.of(2, false);
        DenialRecordOutcome b = DenialRecordOutcome.of(2, true);
        assertNotEquals(a, b);
    }

    @Test
    void outcomeEqualsSelfAndNullAndOtherType() {
        DenialRecordOutcome o = DenialRecordOutcome.of(1, false);
        assertEquals(o, o);
        assertNotEquals(null, o);
        assertNotEquals("not-an-outcome", o);
    }

    @Test
    void outcomeToStringContainsCountAndThreshold() {
        DenialRecordOutcome o = DenialRecordOutcome.of(7, true);
        String s = o.toString();
        assertTrue(s.contains("count=7"));
        assertTrue(s.contains("thresholdExceeded=true"));
    }
}
