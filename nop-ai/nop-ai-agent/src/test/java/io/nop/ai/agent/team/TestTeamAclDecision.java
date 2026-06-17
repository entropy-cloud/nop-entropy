package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link TeamAclDecision} (plan 228 Phase 1).
 *
 * <p>Verifies the immutable data object + factory contract:
 * <ul>
 *   <li>{@link TeamAclDecision#allow(MemberRole)} produces an allowed
 *       decision with {@code reason == null}.</li>
 *   <li>{@link TeamAclDecision#deny(MemberRole, String)} produces a denied
 *       decision with a non-null reason.</li>
 *   <li>Deny factory rejects a null reason (defensive contract).</li>
 *   <li>{@code resolvedRole} is preserved verbatim (including null for
 *       unknown-team / unknown-session / NoOp).</li>
 * </ul>
 */
public class TestTeamAclDecision {

    @Test
    void allowHasAllowedTrueAndNullReason() {
        TeamAclDecision d = TeamAclDecision.allow(MemberRole.LEAD);

        assertTrue(d.isAllowed());
        assertNull(d.getReason(), "allow decision must have null reason");
        assertEquals(MemberRole.LEAD, d.getResolvedRole());
    }

    @Test
    void allowPreservesNullResolvedRole() {
        TeamAclDecision d = TeamAclDecision.allow(null);

        assertTrue(d.isAllowed());
        assertNull(d.getReason());
        assertNull(d.getResolvedRole(), "NoOp allow carries null resolvedRole");
    }

    @Test
    void denyHasAllowedFalseAndNonNullReason() {
        TeamAclDecision d = TeamAclDecision.deny(MemberRole.MEMBER,
                "role MEMBER lacks ADMIN for team-task-update/abandon-unclaimed");

        assertFalse(d.isAllowed());
        assertNotNull(d.getReason());
        assertTrue(d.getReason().contains("ADMIN"));
        assertEquals(MemberRole.MEMBER, d.getResolvedRole());
    }

    @Test
    void denyPreservesNullResolvedRoleForUnknownCaller() {
        TeamAclDecision d = TeamAclDecision.deny(null, "caller session is not a member of team");

        assertFalse(d.isAllowed());
        assertNotNull(d.getReason());
        assertNull(d.getResolvedRole());
    }

    @Test
    void denyRejectsNullReason() {
        assertThrows(NullPointerException.class,
                () -> TeamAclDecision.deny(MemberRole.MEMBER, null),
                "deny factory must reject a null reason");
    }

    @Test
    void toStringReflectsState() {
        TeamAclDecision allow = TeamAclDecision.allow(MemberRole.LEAD);
        TeamAclDecision deny = TeamAclDecision.deny(null, "not a member");

        assertTrue(allow.toString().contains("allowed=true"));
        assertTrue(allow.toString().contains("resolvedRole=LEAD"));
        assertTrue(deny.toString().contains("allowed=false"));
        assertTrue(deny.toString().contains("not a member"));
    }
}
