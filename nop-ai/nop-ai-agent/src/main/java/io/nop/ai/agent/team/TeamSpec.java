package io.nop.ai.agent.team;

import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration of an agent team. A {@code TeamSpec} is the input
 * to {@link ITeamManager#createTeam(TeamSpec)}; the manager turns it into a
 * live {@link Team} instance.
 *
 * <p>Originally a programmatic data object in the foundational TeamManager
 * slice (plan 223). As of plan 231 (L4-team-auto-binding) it is also
 * populated declaratively: the lead agent's {@code .agent.xml} may declare a
 * {@code <team>} element (schema in {@code agent.xdef}), which the engine
 * converts into a {@code TeamSpec} via {@link TeamModelConverter} and feeds
 * to {@link ITeamManager#createTeam(TeamSpec)} at the three execution entry
 * points. The richer fields listed in vision §8.1 ({@code kind}/
 * {@code category}/{@code prompt}/{@code permissions}/
 * {@code maxWallClockMinutes}/{@code maxMessagesPerRun}) remain successors.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code teamName} — human-readable team name (non-null).</li>
 *   <li>{@code description} — optional human-readable description (may be
 *       null or blank).</li>
 *   <li>{@code leadAgentName} — the member name designated as the team lead
 *       (non-null). The lead's role is recorded as {@link MemberRole#LEAD}
 *       when the manager initialises members from this spec.</li>
 *   <li>{@code memberSpecs} — the list of initial member specifications
 *       (non-null, defensively copied on construction). May be empty; the
 *       manager also accepts {@code addMember} calls after creation.</li>
 *   <li>{@code maxParallelMembers} — capacity hint / per-team concurrent-binding
 *       limit. A value {@code <= 0} means <em>unlimited</em>. This field is
 *       enforced as the {@code TEAM_PARALLEL_BOUND_MEMBERS} quota dimension at
 *       {@code bindMemberSession} when a functional
 *       {@link io.nop.ai.agent.quota.IResourceGuard} is wired into the
 *       {@link ITeamManager} (plan 234); with the shipped
 *       {@link io.nop.ai.agent.quota.NoOpResourceGuard} default it remains an
 *       unenforced hint (zero regression).</li>
 * </ul>
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §8.1.
 */
public final class TeamSpec {

    private final String teamName;
    private final String description;
    private final String leadAgentName;
    private final List<TeamMemberSpec> memberSpecs;
    private final int maxParallelMembers;

    /**
     * Construct an immutable team specification.
     *
     * @param teamName          human-readable team name (non-null)
     * @param description       optional description (may be null)
     * @param leadAgentName     the member name designated as the team lead (non-null)
     * @param memberSpecs       the list of initial member specifications (non-null;
     *                          defensively copied)
     * @param maxParallelMembers capacity hint / per-team concurrent-binding limit;
     *                          {@code <= 0} means unlimited (enforced at
     *                          {@code bindMemberSession} when a functional
     *                          {@link io.nop.ai.agent.quota.IResourceGuard} is
     *                          wired, plan 234)
     */
    public TeamSpec(String teamName, String description, String leadAgentName,
                    List<TeamMemberSpec> memberSpecs, int maxParallelMembers) {
        this.teamName = Objects.requireNonNull(teamName, "teamName");
        this.description = description;
        this.leadAgentName = Objects.requireNonNull(leadAgentName, "leadAgentName");
        Objects.requireNonNull(memberSpecs, "memberSpecs");
        this.memberSpecs = List.copyOf(memberSpecs);
        this.maxParallelMembers = maxParallelMembers;
    }

    /**
     * @return the human-readable team name.
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * @return the optional human-readable description (may be null).
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the member name designated as the team lead.
     */
    public String getLeadAgentName() {
        return leadAgentName;
    }

    /**
     * @return an unmodifiable view of the initial member specifications.
     */
    public List<TeamMemberSpec> getMemberSpecs() {
        return memberSpecs;
    }

    /**
     * @return the capacity hint / per-team concurrent-binding limit. A value
     *         {@code <= 0} means unlimited. Enforced as the
     *         {@code TEAM_PARALLEL_BOUND_MEMBERS} quota dimension at
     *         {@code bindMemberSession} when a functional
     *         {@link io.nop.ai.agent.quota.IResourceGuard} is wired into the
     *         {@link ITeamManager} (plan 234); unenforced with the shipped
     *         {@link io.nop.ai.agent.quota.NoOpResourceGuard} default.
     */
    public int getMaxParallelMembers() {
        return maxParallelMembers;
    }

    @Override
    public String toString() {
        return "TeamSpec{teamName='" + teamName + "', leadAgentName='" + leadAgentName
                + "', memberCount=" + memberSpecs.size()
                + ", maxParallelMembers=" + maxParallelMembers + '}';
    }
}
