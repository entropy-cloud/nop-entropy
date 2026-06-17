package io.nop.ai.agent.team;

/**
 * Action verb for the foundational Team ACL permission matrix
 * (vision §5.1 {@code AclAction}). Each {@code (toolName, action)} tuple
 * that a team tool executor asks the {@link ITeamAclChecker} to adjudicate
 * maps to exactly one {@code TeamAclAction}; the checker then decides
 * whether the caller's {@link MemberRole} grants that action.
 *
 * <h2>Semantics (vision §5.1 default ACL matrix)</h2>
 * <ul>
 *   <li>{@link #READ} — observe team state (e.g. {@code team-status}). The
 *       shipped default grants READ to both {@link MemberRole#LEAD} and
 *       {@link MemberRole#MEMBER}.</li>
 *   <li>{@link #WRITE} — produce team artefacts (e.g.
 *       {@code team-send-message}, {@code team-task-create}). Granted to
 *       LEAD and MEMBER.</li>
 *   <li>{@link #EXECUTE} — perform a collaborative state transition on a
 *       team artefact the caller has standing over (e.g.
 *       {@code team-task-update claim / complete}, or abandon a task the
 *       caller has already claimed). Granted to LEAD and MEMBER.</li>
 *   <li>{@link #ADMIN} — team-management operation whose effect shapes the
 *       whole team's work allocation (e.g. abandon a task that no one has
 *       claimed — removing unstarted work from the task pool). Granted to
 *       LEAD only.</li>
 * </ul>
 *
 * <p>The {@link DefaultTeamAclChecker} resolves each (toolName, action)
 * request to a {@code TeamAclAction} via a static map, then applies the
 * default role matrix: LEAD is treated as holding ADMIN (passes all four
 * actions); MEMBER passes READ / WRITE / EXECUTE but is denied ADMIN.
 *
 * <p>This enum is the foundational slice of vision §5.1's {@code AclAction}
 * model. The complete {@code AclResource} × {@code AclAction} matrix
 * (SESSION / PLAN / TOOL_EXECUTION / FILE_SCOPE / MESSAGE_CHANNEL) and
 * permission derivation are explicit successors (plan 228 Non-Goals).
 *
 * <p>See plan 228 (L4-team-acl-enforcement), vision §5.1.
 */
public enum TeamAclAction {
    READ,
    WRITE,
    EXECUTE,
    ADMIN
}
