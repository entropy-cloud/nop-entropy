package io.nop.ai.agent.team;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Functional {@link ITeamAclChecker} that enforces the vision §5.1 default
 * role-permission matrix against the 4 team-tool operations.
 *
 * <h2>Adjudication flow (per {@code checkAccess} call)</h2>
 * <ol>
 *   <li>{@link ITeamManager#getTeam(String)} fetch the team snapshot.
 *       Empty → {@code deny(null, "team not found: ...")}.</li>
 *   <li>Iterate {@link Team#getMembers()} matching by
 *       {@code member.getSessionId().equals(callerSessionId)}. No match →
 *       {@code deny(null, "caller session is not a member of team ...")}.</li>
 *   <li>Read {@link TeamMember#getRole()}.</li>
 *   <li>Resolve the required {@link TeamAclAction} from the static
 *       {@link #REQUIRED_ACTIONS} map keyed by {@code (toolName, action)}.
 *       Unknown tuple → {@code deny(role, "unknown tool/action: ...")}
 *       (fail closed — never silently allow an unmapped operation,
 *       Minimum Rules #24).</li>
 *   <li>Apply the §5.1 default role matrix:
 *       <ul>
 *         <li>{@link MemberRole#LEAD} is treated as ADMIN — passes all
 *             actions unconditionally.</li>
 *         <li>{@link MemberRole#MEMBER} passes READ / WRITE / EXECUTE; an
 *             ADMIN-required operation is denied with a reason citing the
 *             required action.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>§5.1 default matrix</h2>
 * <pre>
 *  toolName           | action            | Required Action | LEAD | MEMBER |
 *  -------------------|-------------------|-----------------|------|--------|
 *  team-send-message  | send              | WRITE           |  ✓   |   ✓    |
 *  team-status        | view              | READ            |  ✓   |   ✓    |
 *  team-task-create   | create            | WRITE           |  ✓   |   ✓    |
 *  team-task-update   | claim             | EXECUTE         |  ✓   |   ✓    |
 *  team-task-update   | complete          | EXECUTE         |  ✓   |   ✓    |
 *  team-task-update   | abandon-claimed   | EXECUTE         |  ✓   |   ✓    |
 *  team-task-update   | abandon-unclaimed | ADMIN           |  ✓   |   ✗    |
 * </pre>
 *
 * <p>The only operation a MEMBER is denied is {@code abandon-unclaimed}
 * (abandon a CREATED task that no one has claimed — removing unstarted work
 * from the task pool is a team-management decision, Design Decision §2).
 *
 * <p>This checker holds a reference to {@link ITeamManager} so it can
 * resolve the caller's role from the live team snapshot on every call —
 * no state caching, no synchronisation concerns. The {@code (toolName,
 * action) → TeamAclAction} map is static and immutable.
 *
 * <p>See plan 228 (L4-team-acl-enforcement), Design Decisions §1 / §2 / §3,
 * vision §5.1.
 */
public final class DefaultTeamAclChecker implements ITeamAclChecker {

    private static final String KEY_SEP = "\u0001";

    /**
     * Static immutable map of {@code (toolName, action) → TeamAclAction}.
     * Encodes the §5.1 default matrix. An unknown tuple is treated as a
     * fail-closed deny — never silently allowed.
     */
    private static final Map<String, TeamAclAction> REQUIRED_ACTIONS = buildRequiredActions();

    private static Map<String, TeamAclAction> buildRequiredActions() {
        Map<String, TeamAclAction> m = new HashMap<>();
        m.put(key("team-send-message", "send"), TeamAclAction.WRITE);
        m.put(key("team-status", "view"), TeamAclAction.READ);
        m.put(key("team-task-create", "create"), TeamAclAction.WRITE);
        m.put(key("team-task-update", "claim"), TeamAclAction.EXECUTE);
        m.put(key("team-task-update", "complete"), TeamAclAction.EXECUTE);
        m.put(key("team-task-update", "abandon-claimed"), TeamAclAction.EXECUTE);
        m.put(key("team-task-update", "abandon-unclaimed"), TeamAclAction.ADMIN);
        return Map.copyOf(m);
    }

    private static String key(String toolName, String action) {
        return toolName + KEY_SEP + action;
    }

    private final ITeamManager teamManager;

    /**
     * Construct a functional ACL checker backed by the given team manager.
     *
     * @param teamManager the team manager used to resolve the caller's role
     *                    (non-null)
     */
    public DefaultTeamAclChecker(ITeamManager teamManager) {
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager");
    }

    @Override
    public TeamAclDecision checkAccess(String teamId, String callerSessionId,
                                       String toolName, String action) {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(callerSessionId, "callerSessionId");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(action, "action");

        Optional<Team> teamOpt = teamManager.getTeam(teamId);
        if (teamOpt.isEmpty()) {
            return TeamAclDecision.deny(null,
                    "team not found: teamId=" + teamId);
        }
        Team team = teamOpt.get();

        TeamMember caller = null;
        for (TeamMember member : team.getMembers().values()) {
            if (callerSessionId.equals(member.getSessionId())) {
                caller = member;
                break;
            }
        }
        if (caller == null) {
            return TeamAclDecision.deny(null,
                    "caller session '" + callerSessionId
                            + "' is not a member of team " + team.getSpec().getTeamName()
                            + " (teamId=" + teamId + ")");
        }

        MemberRole role = caller.getRole();

        TeamAclAction required = REQUIRED_ACTIONS.get(key(toolName, action));
        if (required == null) {
            // Fail closed: an unknown (toolName, action) tuple must never be
            // silently allowed. This protects against an executor passing an
            // action string the matrix does not cover (Minimum Rules #24).
            return TeamAclDecision.deny(role,
                    "unknown tool/action pair: toolName=" + toolName
                            + ", action=" + action
                            + " (no required TeamAclAction defined)");
        }

        if (roleGrants(role, required)) {
            return TeamAclDecision.allow(role);
        }
        return TeamAclDecision.deny(role,
                "role " + role + " lacks " + required
                        + " for " + toolName + "/" + action
                        + " (abandon-unclaimed is ADMIN-only: only LEAD may remove unclaimed tasks from the pool)");
    }

    /**
     * §5.1 default role matrix: LEAD = ADMIN (passes everything); MEMBER
     * passes READ / WRITE / EXECUTE but not ADMIN.
     */
    private static boolean roleGrants(MemberRole role, TeamAclAction required) {
        if (role == MemberRole.LEAD) {
            return true;
        }
        // MEMBER
        return required != TeamAclAction.ADMIN;
    }
}
