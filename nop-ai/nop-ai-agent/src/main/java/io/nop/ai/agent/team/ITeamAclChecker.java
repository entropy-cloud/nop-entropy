package io.nop.ai.agent.team;

/**
 * Team-dimension ACL checker (vision §5.1). Adjudicates, for each team-tool
 * operation requested by a caller, whether the caller's {@link MemberRole}
 * in the team grants the {@link TeamAclAction} that the operation requires.
 *
 * <p>The checker is consulted by the 4 team tool executors
 * ({@code team-send-message} / {@code team-status} / {@code team-task-create}
 * / {@code team-task-update}) <strong>after</strong> the caller's team has
 * been resolved and <strong>before</strong> the actual store / messenger
 * operation is performed. A deny decision does NOT raise an exception —
 * the executor converts it to an honest strategy-feedback result
 * (status {@code "success"} + JSON body describing the denial) so the ReAct
 * loop can react to the policy feedback rather than abort as if it were a
 * technical fault.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@code teamId} — the runtime team identity (the team the caller
 *       claims to operate on, as resolved by
 *       {@code teamManager.getTeamBySession}).</li>
 *   <li>{@code callerSessionId} — the persistent session identity of the
 *       caller (the agent invoking the tool). The functional checker
 *       reverse-resolves this to a {@link TeamMember} and reads its
 *       {@link MemberRole}.</li>
 *   <li>{@code toolName} — the canonical tool name
 *       ({@code "team-send-message"} / {@code "team-status"} /
 *       {@code "team-task-create"} / {@code "team-task-update"}).</li>
 *   <li>{@code action} — the operation verb:
 *       {@code "send"} / {@code "view"} / {@code "create"} /
 *       {@code "claim"} / {@code "complete"} / {@code "abandon-claimed"} /
 *       {@code "abandon-unclaimed"}.</li>
 * </ul>
 *
 * <h2>Shipped defaults</h2>
 * <ul>
 *   <li>{@link NoOpTeamAclChecker} — always returns {@code allow(null)}.
 *       This is the engine default so integrators see zero behaviour
 *       regression unless a functional checker is explicitly wired via
 *       {@code DefaultAgentEngine.setTeamAclChecker}. The NoOp is "ACL not
 *       enabled = no extra restriction", not "ACL not enabled = deny all"
 *       (裁定 4).</li>
 *   <li>{@link DefaultTeamAclChecker} — functional implementation. Holds
 *       an {@link ITeamManager} reference, resolves the caller's role via
 *       {@link ITeamManager#getTeam} + iterating the member map, looks up
 *       the required {@link TeamAclAction} in the static §5.1 default
 *       matrix, and decides LEAD(=ADMIN all-pass) vs MEMBER(R+W+E pass,
 *       ADMIN deny).</li>
 * </ul>
 *
 * <p>This interface is the foundational slice of vision §5.1's team ACL
 * model. {@code TeamSpec} {@code permissions} override, permission
 * derivation (child-Actor ACL inheritance), the full {@code AclResource}
 * enum, and DB-backed {@code TeamAclEntry} persistence are explicit
 * successors (plan 228 Non-Goals).
 *
 * <p>See plan 228 (L4-team-acl-enforcement), vision §5.1.
 */
public interface ITeamAclChecker {

    /**
     * Adjudicate whether the caller ({@code callerSessionId}) is permitted
     * to perform {@code action} on tool {@code toolName} within the team
     * identified by {@code teamId}.
     *
     * @param teamId          the runtime team identity (non-null)
     * @param callerSessionId the persistent session identity of the caller
     *                        (non-null)
     * @param toolName        the canonical tool name (non-null)
     * @param action          the operation verb (non-null)
     * @return an immutable {@link TeamAclDecision}; never {@code null}
     *         (Minimum Rules #24 — a deny is an explicit decision object,
     *         not a swallowed null)
     */
    TeamAclDecision checkAccess(String teamId, String callerSessionId,
                                String toolName, String action);
}
