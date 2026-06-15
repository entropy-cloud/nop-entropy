package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 199 focused verification of {@link DefaultApprovalGate} (design §4.8 /
 * §6.1). Verifies the RESTRICTED defense-in-depth deny and the
 * STANDARD/ELEVATED auto-approve behaviour.
 *
 * <ol>
 *   <li><b>RESTRICTED denied</b>: RESTRICTED level operations are denied
 *       (defense-in-depth).</li>
 *   <li><b>STANDARD approved</b>: STANDARD level operations are
 *       auto-approved.</li>
 *   <li><b>ELEVATED approved</b>: ELEVATED level operations are
 *       auto-approved (confirmation ≠ human approval).</li>
 *   <li><b>deny carries correct kind + reason</b>: the RESTRICTED denial
 *       carries {@link ApprovalDenialKind#OTHER} and a non-null reason.</li>
 *   <li><b>approve carries correct approver</b>: the approval carries
 *       approver = {@value DefaultApprovalGate#APPROVER}.</li>
 *   <li><b>null-safety</b>: null channel/principal/session/agent do not
 *       throw.</li>
 * </ol>
 */
public class TestDefaultApprovalGate {

    @Test
    void restrictedLevelIsDenied() {
        IApprovalGate gate = new DefaultApprovalGate();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.RESTRICTED, "shell.exec", ChannelKind.WEBUI, Principal.user(), "s1", "agentA");
        assertTrue(d.isDenied(),
                "DefaultApprovalGate must DENY RESTRICTED (defense-in-depth)");
        assertFalse(d.isApproved(),
                "DefaultApprovalGate must not approve RESTRICTED");
    }

    @Test
    void standardLevelIsApproved() {
        IApprovalGate gate = new DefaultApprovalGate();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.STANDARD, "fs.read", ChannelKind.API, Principal.user(), "s1", "agentA");
        assertTrue(d.isApproved(),
                "DefaultApprovalGate must approve STANDARD");
        assertEquals(DefaultApprovalGate.APPROVER, d.getApprover(),
                "Approver for STANDARD must be 'default'");
    }

    @Test
    void elevatedLevelIsApproved() {
        IApprovalGate gate = new DefaultApprovalGate();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.ELEVATED, "fs.write", ChannelKind.DM, Principal.operator(), "s1", "agentA");
        assertTrue(d.isApproved(),
                "DefaultApprovalGate must approve ELEVATED (confirmation ≠ human approval)");
        assertEquals(DefaultApprovalGate.APPROVER, d.getApprover(),
                "Approver for ELEVATED must be 'default'");
    }

    @Test
    void restrictedDenyCarriesCorrectKindAndReason() {
        IApprovalGate gate = new DefaultApprovalGate();
        ApprovalDecision d = gate.requestApproval(
                SecurityLevel.RESTRICTED, "network.fetch", ChannelKind.GROUP, Principal.user(), "s1", "a");
        assertEquals(ApprovalDenialKind.OTHER, d.getDenialKind(),
                "RESTRICTED denial must carry kind OTHER");
        assertNotNull(d.getReason(),
                "RESTRICTED denial must carry a non-null reason");
        assertTrue(d.getReason().contains("RESTRICTED"),
                "RESTRICTED denial reason must mention RESTRICTED");
    }

    @Test
    void approvedDecisionCarriesCorrectApprover() {
        IApprovalGate gate = new DefaultApprovalGate();
        for (SecurityLevel level : new SecurityLevel[]{SecurityLevel.STANDARD, SecurityLevel.ELEVATED}) {
            ApprovalDecision d = gate.requestApproval(
                    level, "fs.read", ChannelKind.WEBUI, Principal.user(), "s1", "a");
            assertEquals(DefaultApprovalGate.APPROVER, d.getApprover(),
                    "Approver for " + level + " must be 'default'");
            assertNull(d.getDenialKind(),
                    "Approved decision must not carry a denial kind");
            assertNull(d.getReason(),
                    "Approved decision must not carry a denial reason");
        }
    }

    @Test
    void nullChannelAndPrincipalDoNotThrow() {
        IApprovalGate gate = new DefaultApprovalGate();
        ApprovalDecision dRestricted = gate.requestApproval(
                SecurityLevel.RESTRICTED, "shell.exec", null, null, null, null);
        assertTrue(dRestricted.isDenied(),
                "RESTRICTED must be denied even with null context");

        ApprovalDecision dStandard = gate.requestApproval(
                SecurityLevel.STANDARD, "fs.read", null, null, null, null);
        assertTrue(dStandard.isApproved(),
                "STANDARD must be approved even with null context");
    }
}
