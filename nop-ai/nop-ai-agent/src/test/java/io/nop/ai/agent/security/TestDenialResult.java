package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies the {@link DenialResult} value semantics (design §6.3): factory
 * fields, the {@link DenialResult#repeatedSameIntent} convenience factory
 * (reason = {@code REPEATED_SAME_INTENT}, suggestedNextStep = {@code REPLAN},
 * retryable = false), null-rejection on required fields, and equals/hashCode
 * consistency — following the same immutable value-object pattern as
 * {@link ApprovalDecision} / {@link MatrixDecision} / {@link DenialRecord}.
 */
public class TestDenialResult {

    // ========================================================================
    // Factory
    // ========================================================================

    @Test
    void ofCapturesAllFields() {
        DenialResult r = DenialResult.of(
                DenialReason.THRESHOLD_EXCEEDED, DenialSuggestedStep.ASK_USER,
                "abc123", "threshold reached", true);
        assertEquals(DenialReason.THRESHOLD_EXCEEDED, r.getReason());
        assertEquals(DenialSuggestedStep.ASK_USER, r.getSuggestedNextStep());
        assertEquals("abc123", r.getActionFingerprint());
        assertEquals("threshold reached", r.getMessage());
        assertEquals(true, r.isRetryable());
    }

    @Test
    void ofAllowsNullOptionalFields() {
        DenialResult r = DenialResult.of(
                DenialReason.TIMEOUT, DenialSuggestedStep.REPLAN,
                null, null, false);
        assertNull(r.getActionFingerprint(), "actionFingerprint may be null");
        assertNull(r.getMessage(), "message may be null");
    }

    @Test
    void ofRejectsNullReason() {
        try {
            DenialResult.of(null, DenialSuggestedStep.REPLAN, "fp", "msg", false);
            fail("DenialResult.of must reject null reason");
        } catch (IllegalArgumentException expected) {
            // expected: reason is a required structured field
        }
    }

    @Test
    void ofRejectsNullSuggestedNextStep() {
        try {
            DenialResult.of(DenialReason.HUMAN_REJECTED, null, "fp", "msg", false);
            fail("DenialResult.of must reject null suggestedNextStep");
        } catch (IllegalArgumentException expected) {
            // expected: suggestedNextStep is a required structured field
        }
    }

    // ========================================================================
    // repeatedSameIntent convenience factory
    // ========================================================================

    @Test
    void repeatedSameIntentFactorySetsCoreBlindRetryFields() {
        DenialResult r = DenialResult.repeatedSameIntent("fp123", "blind retry");
        assertEquals(DenialReason.REPEATED_SAME_INTENT, r.getReason(),
                "repeatedSameIntent reason must be REPEATED_SAME_INTENT");
        assertEquals(DenialSuggestedStep.REPLAN, r.getSuggestedNextStep(),
                "repeatedSameIntent suggestedNextStep must be REPLAN");
        assertEquals(false, r.isRetryable(),
                "repeatedSameIntent must not be retryable (the agent must change approach, not re-submit)");
        assertEquals("fp123", r.getActionFingerprint());
        assertEquals("blind retry", r.getMessage());
    }

    @Test
    void repeatedSameIntentFactoryAllowsNullFingerprintAndMessage() {
        DenialResult r = DenialResult.repeatedSameIntent(null, null);
        assertNull(r.getActionFingerprint());
        assertNull(r.getMessage());
        assertEquals(DenialReason.REPEATED_SAME_INTENT, r.getReason());
    }

    // ========================================================================
    // equals / hashCode
    // ========================================================================

    @Test
    void equalsAndHashCodeConsistency() {
        DenialResult a = DenialResult.of(
                DenialReason.REPEATED_SAME_INTENT, DenialSuggestedStep.REPLAN,
                "fp", "msg", false);
        DenialResult b = DenialResult.of(
                DenialReason.REPEATED_SAME_INTENT, DenialSuggestedStep.REPLAN,
                "fp", "msg", false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentReasonsAreNotEqual() {
        DenialResult a = DenialResult.of(DenialReason.HUMAN_REJECTED,
                DenialSuggestedStep.REPLAN, null, null, false);
        DenialResult b = DenialResult.of(DenialReason.TIMEOUT,
                DenialSuggestedStep.REPLAN, null, null, false);
        assertNotEquals(a, b);
    }

    @Test
    void differentSuggestedStepsAreNotEqual() {
        DenialResult a = DenialResult.of(DenialReason.REPEATED_SAME_INTENT,
                DenialSuggestedStep.REPLAN, null, null, false);
        DenialResult b = DenialResult.of(DenialReason.REPEATED_SAME_INTENT,
                DenialSuggestedStep.ASK_USER, null, null, false);
        assertNotEquals(a, b);
    }

    @Test
    void differentFingerprintsAreNotEqual() {
        DenialResult a = DenialResult.of(DenialReason.REPEATED_SAME_INTENT,
                DenialSuggestedStep.REPLAN, "fp-a", null, false);
        DenialResult b = DenialResult.of(DenialReason.REPEATED_SAME_INTENT,
                DenialSuggestedStep.REPLAN, "fp-b", null, false);
        assertNotEquals(a, b);
    }

    @Test
    void differentRetryableAreNotEqual() {
        DenialResult a = DenialResult.of(DenialReason.THRESHOLD_EXCEEDED,
                DenialSuggestedStep.REPLAN, null, null, false);
        DenialResult b = DenialResult.of(DenialReason.THRESHOLD_EXCEEDED,
                DenialSuggestedStep.REPLAN, null, null, true);
        assertNotEquals(a, b);
    }

    @Test
    void equalsSelfAndNullAndOtherType() {
        DenialResult r = DenialResult.of(DenialReason.REPEATED_SAME_INTENT,
                DenialSuggestedStep.REPLAN, "fp", "msg", false);
        assertEquals(r, r);
        assertNotEquals(null, r);
        assertNotEquals("not-a-denial-result", r);
    }

    @Test
    void toStringContainsReason() {
        DenialResult r = DenialResult.of(DenialReason.REPEATED_SAME_INTENT,
                DenialSuggestedStep.REPLAN, "fp123", "blind retry", false);
        String s = r.toString();
        assertEquals(true, s.contains("REPEATED_SAME_INTENT"));
        assertEquals(true, s.contains("REPLAN"));
    }
}
