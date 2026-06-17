package io.nop.ai.agent.team;

import java.util.Objects;

/**
 * Immutable result of an {@link ITeamAclChecker#checkAccess} adjudication.
 * Carries three pieces of information:
 * <ul>
 *   <li>{@code allowed} — whether the requested (toolName, action) is
 *       permitted for the caller.</li>
 *   <li>{@code reason} — when {@code allowed == false}, a human-readable
 *       explanation suitable for inclusion in an honest denial result
 *       returned to the LLM (e.g. "role MEMBER lacks ADMIN for
 *       team-task-update/abandon-unclaimed"); {@code null} when allowed
 *       (no reason needed).</li>
 *   <li>{@code resolvedRole} — the {@link MemberRole} the checker resolved
 *       for the caller, or {@code null} when the caller could not be
 *       resolved as a member of the team (e.g. unknown team / unknown
 *       session). Lets the caller (and tests) distinguish "denied because
 *       not a member" from "denied because role lacks action".</li>
 * </ul>
 *
 * <p>Use the {@link #allow(MemberRole)} / {@link #deny(MemberRole, String)}
 * factories; the constructor is private so the {@code allowed ↔ reason}
 * invariant (allow ⇒ reason == null; deny ⇒ reason != null) is enforced.
 *
 * <p>See plan 228 (L4-team-acl-enforcement).
 */
public final class TeamAclDecision {

    private final boolean allowed;
    private final String reason;
    private final MemberRole resolvedRole;

    private TeamAclDecision(boolean allowed, String reason, MemberRole resolvedRole) {
        this.allowed = allowed;
        this.reason = reason;
        this.resolvedRole = resolvedRole;
    }

    /**
     * Build an allow decision. {@code resolvedRole} may be {@code null}
     * (e.g. {@link NoOpTeamAclChecker} allow has no role information) or
     * the actual role resolved for the caller.
     *
     * @param resolvedRole the role resolved for the caller, or {@code null}
     *                     when no role information is available
     * @return an immutable allow decision (reason is null)
     */
    public static TeamAclDecision allow(MemberRole resolvedRole) {
        return new TeamAclDecision(true, null, resolvedRole);
    }

    /**
     * Build a deny decision. {@code reason} must be non-null and should be
     * suitable for returning to the LLM as an honest strategy feedback.
     * {@code resolvedRole} may be {@code null} (caller not a member of the
     * team) or the actual role that lacked the required action.
     *
     * @param resolvedRole the role resolved for the caller, or {@code null}
     *                     when the caller is not a member of the team
     * @param reason       human-readable denial reason (non-null)
     * @return an immutable deny decision
     */
    public static TeamAclDecision deny(MemberRole resolvedRole, String reason) {
        return new TeamAclDecision(false, Objects.requireNonNull(reason, "reason"), resolvedRole);
    }

    /**
     * @return {@code true} if the requested action is permitted.
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * @return the denial reason, or {@code null} when {@link #isAllowed()}.
     */
    public String getReason() {
        return reason;
    }

    /**
     * @return the {@link MemberRole} resolved for the caller, or
     *         {@code null} when no role was resolved (unknown team /
     *         unknown session / NoOp checker).
     */
    public MemberRole getResolvedRole() {
        return resolvedRole;
    }

    @Override
    public String toString() {
        return "TeamAclDecision{allowed=" + allowed
                + ", reason=" + (reason != null ? "'" + reason + "'" : "null")
                + ", resolvedRole=" + resolvedRole + '}';
    }
}
