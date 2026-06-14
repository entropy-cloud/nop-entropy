package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link AutoApproveGate} contract (design §6.1 default): all
 * security levels, all channels, all principals are auto-approved, and the
 * pass-through is a semantically-correct approve (not a silent no-op).
 */
public class TestAutoApproveGate {

    @Test
    void allSecurityLevelsAreApproved() {
        IApprovalGate gate = AutoApproveGate.autoApprove();
        for (SecurityLevel level : SecurityLevel.values()) {
            ApprovalDecision d = gate.requestApproval(
                    level, "shell.exec", ChannelKind.WEBUI, Principal.user(), "s1", "agentA");
            assertTrue(d.isApproved(),
                    "AutoApproveGate must approve level " + level + " (pass-through default)");
            assertTrue(!d.isDenied(),
                    "AutoApproveGate must not deny level " + level);
        }
    }

    @Test
    void approverIsAuto() {
        IApprovalGate gate = AutoApproveGate.autoApprove();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.RESTRICTED, "shell.exec", ChannelKind.API, Principal.user(), "s1", "agentA");
        assertEquals(AutoApproveGate.APPROVER, d.getApprover(),
                "AutoApproveGate approver identifier must be 'auto'");
        assertEquals("auto", d.getApprover());
    }

    @Test
    void nullChannelAndPrincipalDoNotThrow() {
        IApprovalGate gate = AutoApproveGate.autoApprove();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.ELEVATED, "fs.write", null, null, null, null);
        assertTrue(d.isApproved(),
                "AutoApproveGate must approve even with null channel/principal/session/agent");
        assertEquals("auto", d.getApprover());
    }

    @Test
    void nullToolNameDoesNotThrow() {
        IApprovalGate gate = AutoApproveGate.autoApprove();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.STANDARD, null, ChannelKind.DM, Principal.operator(), "s1", "agentA");
        assertTrue(d.isApproved(),
                "AutoApproveGate must approve even with null toolName");
    }

    @Test
    void singletonInstanceShared() {
        IApprovalGate a = AutoApproveGate.autoApprove();
        IApprovalGate b = AutoApproveGate.autoApprove();
        assertTrue(a == b, "AutoApproveGate.autoApprove() must return a shared singleton");
    }

    @Test
    void approvedDecisionCarriesNoDenialFields() {
        IApprovalGate gate = AutoApproveGate.autoApprove();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.RESTRICTED, "network.fetch", ChannelKind.GROUP, Principal.user(), "s1", "a");
        assertNull(d.getDenialKind(),
                "An approved decision must not carry a denial kind");
        assertNull(d.getReason(),
                "An approved decision must not carry a denial reason");
    }

    /**
     * Sanity check: a functional (non-default) gate producing a real denial is
     * observable, confirming the {@link IApprovalGate} contract surface is not
     * hollow — the dispatch path (Phase 2) has a real decision to act on.
     */
    @Test
    void functionalGateDenialIsObservableThroughContract() {
        IApprovalGate denyingGate = (level, toolName, channel, principal, sessionId, agentName) -> {
            if (level == SecurityLevel.RESTRICTED) {
                return ApprovalDecision.denyHumanRejected("restricted by test gate");
            }
            if (level == SecurityLevel.ELEVATED) {
                return ApprovalDecision.denyTimeout("approval timed out");
            }
            return ApprovalDecision.approve("test-approver");
        };

        ApprovalDecision restricted = denyingGate.requestApproval(
                SecurityLevel.RESTRICTED, "shell.exec", ChannelKind.WEBUI, Principal.user(), "s1", "a");
        assertTrue(restricted.isDenied());
        assertEquals(ApprovalDenialKind.HUMAN_REJECTED, restricted.getDenialKind());
        assertEquals("restricted by test gate", restricted.getReason());

        ApprovalDecision elevated = denyingGate.requestApproval(
                SecurityLevel.ELEVATED, "fs.write", ChannelKind.WEBUI, Principal.user(), "s1", "a");
        assertTrue(elevated.isDenied());
        assertEquals(ApprovalDenialKind.TIMEOUT, elevated.getDenialKind());

        ApprovalDecision standard = denyingGate.requestApproval(
                SecurityLevel.STANDARD, "fs.read", ChannelKind.WEBUI, Principal.user(), "s1", "a");
        assertTrue(standard.isApproved());
        assertEquals("test-approver", standard.getApprover());
    }
}
