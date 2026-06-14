package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the {@link ApprovalDecision} value semantics (design §6.1): approved
 * vs denied predicates, approver identifier on approve, structured
 * {@link ApprovalDenialKind} (human_rejected / timeout / other) on deny, and
 * equals/hashCode consistency — following the same deny-with-reason pattern as
 * {@link MatrixDecision} / {@link ToolAccessResult}.
 */
public class TestApprovalDecision {

    // ========================================================================
    // Approve factories
    // ========================================================================

    @Test
    void approveCarriesApproverAndNoDenialFields() {
        ApprovalDecision d = ApprovalDecision.approve("alice");
        assertEquals(true, d.isApproved());
        assertEquals(false, d.isDenied());
        assertEquals("alice", d.getApprover());
        assertNull(d.getDenialKind());
        assertNull(d.getReason());
    }

    @Test
    void approveWithNullApproverDefaultsToSystem() {
        ApprovalDecision d = ApprovalDecision.approve(null);
        assertEquals(true, d.isApproved());
        assertEquals("system", d.getApprover(),
                "A null approver must default to 'system' for audit consistency");
    }

    @Test
    void approveWithAutoApprover() {
        ApprovalDecision d = ApprovalDecision.approve("auto");
        assertEquals("auto", d.getApprover());
    }

    // ========================================================================
    // Deny factories — denial-kind distinction (L3-G1 narrowing)
    // ========================================================================

    @Test
    void denyHumanRejectedCarriesKindAndReason() {
        ApprovalDecision d = ApprovalDecision.denyHumanRejected("user clicked reject");
        assertEquals(false, d.isApproved());
        assertEquals(true, d.isDenied());
        assertEquals(ApprovalDenialKind.HUMAN_REJECTED, d.getDenialKind());
        assertEquals("user clicked reject", d.getReason());
        assertNull(d.getApprover(), "A denied decision must not carry an approver");
    }

    @Test
    void denyTimeoutCarriesKindAndReason() {
        ApprovalDecision d = ApprovalDecision.denyTimeout("no response in 300s");
        assertEquals(true, d.isDenied());
        assertEquals(ApprovalDenialKind.TIMEOUT, d.getDenialKind());
        assertEquals("no response in 300s", d.getReason());
        assertNull(d.getApprover());
    }

    @Test
    void denyWithExplicitKindOther() {
        ApprovalDecision d = ApprovalDecision.deny(ApprovalDenialKind.OTHER, "gate offline");
        assertEquals(ApprovalDenialKind.OTHER, d.getDenialKind());
        assertEquals("gate offline", d.getReason());
    }

    @Test
    void denyWithNullKindDefaultsToOther() {
        ApprovalDecision d = ApprovalDecision.deny(null, "unknown");
        assertEquals(ApprovalDenialKind.OTHER, d.getDenialKind(),
                "A null denial kind must default to OTHER, not throw NPE");
    }

    @Test
    void humanRejectedAndTimeoutAreDistinguishable() {
        // The core L3-G1 narrowing: a human rejection and an approval timeout
        // must be distinguishable via the denial kind, not collapsed into one
        // ambiguous reason string.
        ApprovalDecision human = ApprovalDecision.denyHumanRejected("no");
        ApprovalDecision timeout = ApprovalDecision.denyTimeout("300s");

        assertNotEquals(human.getDenialKind(), timeout.getDenialKind(),
                "HUMAN_REJECTED and TIMEOUT must be distinct denial kinds");
        assertEquals(ApprovalDenialKind.HUMAN_REJECTED, human.getDenialKind());
        assertEquals(ApprovalDenialKind.TIMEOUT, timeout.getDenialKind());
    }

    // ========================================================================
    // equals / hashCode consistency
    // ========================================================================

    @Test
    void approveEquality() {
        ApprovalDecision a = ApprovalDecision.approve("auto");
        ApprovalDecision b = ApprovalDecision.approve("auto");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void denyHumanRejectedEquality() {
        ApprovalDecision a = ApprovalDecision.denyHumanRejected("no");
        ApprovalDecision b = ApprovalDecision.denyHumanRejected("no");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void denyTimeoutEquality() {
        ApprovalDecision a = ApprovalDecision.denyTimeout("t");
        ApprovalDecision b = ApprovalDecision.denyTimeout("t");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentApproversAreNotEqual() {
        ApprovalDecision a = ApprovalDecision.approve("auto");
        ApprovalDecision b = ApprovalDecision.approve("alice");
        assertNotEquals(a, b);
    }

    @Test
    void differentDenialKindsAreNotEqual() {
        ApprovalDecision a = ApprovalDecision.denyHumanRejected("x");
        ApprovalDecision b = ApprovalDecision.denyTimeout("x");
        assertNotEquals(a, b,
                "A human rejection and a timeout with the same reason must not be equal");
    }

    @Test
    void differentReasonsAreNotEqual() {
        ApprovalDecision a = ApprovalDecision.denyTimeout("300s");
        ApprovalDecision b = ApprovalDecision.denyTimeout("600s");
        assertNotEquals(a, b);
    }

    @Test
    void approveAndDenyAreNotEqual() {
        ApprovalDecision approve = ApprovalDecision.approve("auto");
        ApprovalDecision deny = ApprovalDecision.denyHumanRejected("no");
        assertNotEquals(approve, deny);
    }

    @Test
    void equalsSelfAndNullAndOtherType() {
        ApprovalDecision d = ApprovalDecision.approve("auto");
        assertEquals(d, d);
        assertNotEquals(null, d);
        assertNotEquals("not-a-decision", d);
    }

    // ========================================================================
    // toString readability
    // ========================================================================

    @Test
    void approveToStringIsReadable() {
        ApprovalDecision d = ApprovalDecision.approve("auto");
        String s = d.toString();
        assertEquals(true, s.contains("approved=true"));
        assertEquals(true, s.contains("approver='auto'"));
    }

    @Test
    void denyToStringIsReadable() {
        ApprovalDecision d = ApprovalDecision.denyHumanRejected("rejected by reviewer");
        String s = d.toString();
        assertEquals(true, s.contains("approved=false"));
        assertEquals(true, s.contains("HUMAN_REJECTED"));
        assertEquals(true, s.contains("rejected by reviewer"));
    }
}
