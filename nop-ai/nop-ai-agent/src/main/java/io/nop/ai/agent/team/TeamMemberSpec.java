package io.nop.ai.agent.team;

import java.util.Objects;

/**
 * Immutable specification of a single team member, part of a {@link TeamSpec}.
 *
 * <p>Originally a programmatic data object in the foundational TeamManager
 * slice (plan 223). As of plan 231 (L4-team-auto-binding) it is also
 * populated declaratively from the {@code <member>} entries of the lead
 * agent's {@code <team>} element via {@link TeamModelConverter}.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code memberName} — the unique member identifier within the team
 *       (must be non-null, non-blank).</li>
 *   <li>{@code agentModel} — the agent configuration/model name this member
 *       binds to (e.g. the {@code agent.xml} name). Must be non-null.</li>
 *   <li>{@code role} — the {@link MemberRole} of this member.</li>
 * </ul>
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §8.1.
 */
public final class TeamMemberSpec {

    private final String memberName;
    private final String agentModel;
    private final MemberRole role;

    /**
     * Construct an immutable member specification.
     *
     * @param memberName unique member identifier within the team (non-null)
     * @param agentModel the agent configuration/model name (non-null)
     * @param role       the member role (non-null)
     */
    public TeamMemberSpec(String memberName, String agentModel, MemberRole role) {
        this.memberName = Objects.requireNonNull(memberName, "memberName");
        this.agentModel = Objects.requireNonNull(agentModel, "agentModel");
        this.role = Objects.requireNonNull(role, "role");
    }

    /**
     * @return the unique member identifier within the team.
     */
    public String getMemberName() {
        return memberName;
    }

    /**
     * @return the agent configuration/model name this member binds to.
     */
    public String getAgentModel() {
        return agentModel;
    }

    /**
     * @return the member role.
     */
    public MemberRole getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "TeamMemberSpec{memberName='" + memberName + "', agentModel='"
                + agentModel + "', role=" + role + '}';
    }
}
