package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.TeamMemberModel;
import io.nop.ai.agent.model.TeamModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan 231 (L4-team-auto-binding): converts the mutable XDSL-generated
 * {@link TeamModel} / {@link TeamMemberModel} (loaded from {@code .agent.xml}'s
 * {@code <team>} element) into the immutable {@link TeamSpec} /
 * {@link TeamMemberSpec} value objects consumed by
 * {@link ITeamManager#createTeam(TeamSpec)}.
 *
 * <p>This bridges the declarative configuration surface (mutable XDSL beans,
 * the module's established codegen pattern — mirrors
 * {@code AgentPermissionModel}) and the foundational team-manager contract
 * (immutable POJOs, plan 223). The conversion runs at engine consume-time
 * (after {@code loadAgentModel}, before {@code createTeam}), never at load
 * time.
 *
 * <h2>Lead-in-roster guarantee (Design Decision #3)</h2>
 * {@link InMemoryTeamManager#createTeam} initialises the member map from the
 * spec's {@code memberSpecs}, and {@link ITeamManager#bindMemberSession}
 * requires the binding target to be in that roster. For the lead's
 * self-binding to succeed, {@code leadAgentName} must appear in
 * {@code memberSpecs} with {@link MemberRole#LEAD}. If the {@code <team>}
 * roster does not explicitly list the lead, this converter auto-registers
 * the lead as a {@code LEAD} member (matching the manager's established
 * convention).
 *
 * <p>See plan 231 and vision §8.1.
 */
public final class TeamModelConverter {

    private TeamModelConverter() {
    }

    /**
     * Convert a mutable {@link TeamModel} into an immutable {@link TeamSpec}.
     *
     * <p>The lead ({@code leadAgentName}) is guaranteed to appear in the
     * resulting {@code memberSpecs} with {@link MemberRole#LEAD}: if the
     * roster already declares a member with the lead's name, its role is
     * normalised to {@code LEAD}; otherwise a synthetic {@code LEAD} member
     * is appended. The synthetic member's {@code agentModel} defaults to the
     * lead's own agent name (best-effort — there is no separate lead agent
     * model field in the {@code <team>} element).
     *
     * @param model     the mutable XDSL team model (non-null)
     * @param agentName the lead agent's own name, used as the synthetic
     *                  member's {@code agentModel} when the lead is not in
     *                  the roster (non-null)
     * @return the immutable team specification
     * @throws NopAiAgentException if {@code teamName} or {@code leadAgentName}
     *         is missing (fail-fast, Minimum Rules #24 — no silent default)
     */
    public static TeamSpec toTeamSpec(TeamModel model, String agentName) {
        if (model == null) {
            throw new NopAiAgentException(
                    "TeamModelConverter.toTeamSpec: model must not be null");
        }
        String teamName = model.getTeamName();
        if (teamName == null || teamName.isEmpty()) {
            throw new NopAiAgentException(
                    "TeamModelConverter.toTeamSpec: <team teamName> is required but missing");
        }
        String leadAgentName = model.getLeadAgentName();
        if (leadAgentName == null || leadAgentName.isEmpty()) {
            throw new NopAiAgentException(
                    "TeamModelConverter.toTeamSpec: <team leadAgentName> is required but missing");
        }

        List<TeamMemberSpec> memberSpecs = toMemberSpecs(model.getMembers());

        // Design Decision #3: ensure the lead appears in the roster with
        // role=LEAD so the manager accepts the lead's self-binding. If a
        // roster entry already exists for the lead, normalise its role to
        // LEAD; otherwise append a synthetic LEAD member whose agentModel
        // is the lead agent's own name.
        boolean leadInRoster = false;
        List<TeamMemberSpec> normalized = new ArrayList<>(memberSpecs.size() + 1);
        for (TeamMemberSpec ms : memberSpecs) {
            if (leadAgentName.equals(ms.getMemberName())) {
                normalized.add(new TeamMemberSpec(ms.getMemberName(),
                        ms.getAgentModel(), MemberRole.LEAD));
                leadInRoster = true;
            } else {
                normalized.add(ms);
            }
        }
        if (!leadInRoster) {
            normalized.add(new TeamMemberSpec(leadAgentName, agentName, MemberRole.LEAD));
        }

        int maxParallel = model.getMaxParallelMembers() == null ? 0 : model.getMaxParallelMembers();
        return new TeamSpec(teamName, model.getDescription(), leadAgentName, normalized, maxParallel);
    }

    /**
     * Convert a single mutable {@link TeamMemberModel} into an immutable
     * {@link TeamMemberSpec}.
     *
     * @param model the mutable member model (non-null)
     * @return the immutable member specification
     * @throws NopAiAgentException if {@code name} or {@code agentModel} is
     *         missing (fail-fast, Minimum Rules #24)
     */
    public static TeamMemberSpec toMemberSpec(TeamMemberModel model) {
        if (model == null) {
            throw new NopAiAgentException(
                    "TeamModelConverter.toMemberSpec: model must not be null");
        }
        String memberName = model.getName();
        if (memberName == null || memberName.isEmpty()) {
            throw new NopAiAgentException(
                    "TeamModelConverter.toMemberSpec: <member name> is required but missing");
        }
        String agentModel = model.getAgentModel();
        if (agentModel == null || agentModel.isEmpty()) {
            throw new NopAiAgentException(
                    "TeamModelConverter.toMemberSpec: <member agentModel> is required but missing "
                            + "(member=" + memberName + ")");
        }
        return new TeamMemberSpec(memberName, agentModel, toRole(model.getRole()));
    }

    private static List<TeamMemberSpec> toMemberSpecs(List<TeamMemberModel> members) {
        List<TeamMemberSpec> result = new ArrayList<>();
        if (members != null) {
            for (TeamMemberModel m : members) {
                result.add(toMemberSpec(m));
            }
        }
        return result;
    }

    private static MemberRole toRole(MemberRole role) {
        // role is optional in <member>; absent (null) defaults to MEMBER.
        return role == null ? MemberRole.MEMBER : role;
    }
}
