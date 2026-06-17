package io.nop.ai.agent.team;

/**
 * Role of a member within an agent team. The foundational TeamManager slice
 * (plan 223 / L4-8-team-manager) uses this enum purely as an identity tag —
 * it does <strong>not</strong> enforce any permission. Team ACL enforcement
 * (role-based permission matrix, permission derivation, permission-check
 * interception) is an explicit successor (vision §5.1).
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>{@link #LEAD} — the team lead. Designates administrative intent
 *       (created from {@link TeamSpec#getLeadAgentName()}). In the
 *       foundational slice this is an identity tag only; ACL enforcement
 *       that grants LEAD administrative privileges is a successor.</li>
 *   <li>{@link #MEMBER} — a regular team member.</li>
 * </ul>
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §8.1.
 */
public enum MemberRole {
    /**
     * The team lead. Designates administrative intent. The foundational
     * TeamManager slice records this role but does not enforce any
     * permission derived from it (ACL enforcement is a successor).
     */
    LEAD,

    /**
     * A regular team member.
     */
    MEMBER
}
