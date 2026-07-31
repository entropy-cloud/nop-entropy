package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.TeamMemberRefModel;
import io.nop.ai.agent.model.TeamModel;
import io.nop.ai.agent.runtime.AgentActor;
import io.nop.ai.agent.team.ITeamManager;
import io.nop.ai.agent.team.NoOpTeamManager;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMember;
import io.nop.ai.agent.team.TeamModelConverter;
import io.nop.ai.agent.team.TeamSpec;
import io.nop.ai.agent.team.TeamStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Declarative team/member auto-binding for the engine (extracted from
 * {@link DefaultAgentEngine}, MA4.2-05): pre-checks team declarations,
 * binds the lead and member sessions idempotently, and resolves the
 * Actor association tag when an ActorRuntime is configured.
 */
public class AgentTeamBinder {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEngine.class);
    private final DefaultAgentEngineConfig config;

    public AgentTeamBinder(DefaultAgentEngineConfig config) {
        this.config = config;
    }

    // ---- moved verbatim from DefaultAgentEngine (MA4.2-05 split) ----
    public void precheckTeamDeclarations(AgentModel agentModel) {
        boolean hasTeamDecl = agentModel.getTeam() != null;
        boolean hasMemberDecl = agentModel.getTeamMember() != null;
        if ((hasTeamDecl || hasMemberDecl) && config.getTeamManager() instanceof NoOpTeamManager) {
            throw new NopAiAgentException(
                    "Agent declares <team>/<team-member> but no functional ITeamManager "
                            + "is wired; call setTeamManager(InMemoryTeamManager/DbTeamManager) "
                            + "to enable declarative team binding. agentName=" + agentModel.getName());
        }
    }

    /**
     * is present, this method touches no teamManager state (zero regression).
     */
    public void autoBindTeam(AgentModel agentModel, String sessionId, String agentName) {
        TeamModel teamDecl = agentModel.getTeam();
        if (teamDecl != null) {
            autoBindLead(teamDecl, sessionId, agentName);
        }
        TeamMemberRefModel memberDecl = agentModel.getTeamMember();
        if (memberDecl != null) {
            autoBindMember(memberDecl, sessionId);
        }
    }

    /**
     * (Design Decision #8 — No Silent No-Op).
     */
    public void autoBindLead(TeamModel teamDecl, String sessionId, String agentName) {
        String actorId = resolveActorId(sessionId);
        String leadAgentName = teamDecl.getLeadAgentName();

        // Idempotent create: probe the session index first so resume/restore
        // do not re-create the team (createTeam generates a fresh UUID and is
        // not itself idempotent).
        java.util.Optional<Team> existing = config.getTeamManager().getTeamBySession(sessionId);
        String teamId;
        if (existing.isPresent()) {
            teamId = existing.get().getTeamId();
        } else {
            TeamSpec spec = TeamModelConverter.toTeamSpec(teamDecl, agentName);
            teamId = config.getTeamManager().createTeam(spec).getTeamId();
        }

        boolean bound = config.getTeamManager().bindMemberSession(teamId, leadAgentName, sessionId, actorId);
        if (!bound) {
            throw new NopAiAgentException(
                    "Auto-bind failed: lead member '" + leadAgentName
                            + "' could not be bound to team '" + teamId
                            + "' (not in roster or team not in a bindable state). sessionId="
                            + sessionId);
        }
    }
    public void autoBindMember(TeamMemberRefModel memberDecl, String sessionId) {
        String actorId = resolveActorId(sessionId);
        String teamName = memberDecl.getTeamName();
        String memberName = memberDecl.getMemberName();

        // getActiveTeams() returns CREATED+ACTIVE; an explicit ACTIVE filter
        // is required so members only bind to an activated team.
        Team matched = null;
        int activeCount = 0;
        for (Team team : config.getTeamManager().getActiveTeams()) {
            if (teamName.equals(team.getSpec().getTeamName())
                    && team.getStatus() == TeamStatus.ACTIVE) {
                activeCount++;
                if (matched == null) {
                    matched = team;
                }
            }
        }
        if (matched == null) {
            throw new NopAiAgentException(
                    "Auto-bind failed: member declares <team-member teamName='" + teamName
                            + "'> but no ACTIVE team with that name was found "
                            + "(ensure the lead agent has executed and bound/activated the team). "
                            + "sessionId=" + sessionId);
        }
        if (activeCount > 1) {
            LOG.warn("Auto-bind: multiple ACTIVE teams named '{}' found; binding to the first "
                    + "(teamId={}). Cross-process teamName uniqueness arbitration is a successor (Non-Goal).",
                    teamName, matched.getTeamId());
        }

        // Idempotent: skip the bind if the member is already bound.
        java.util.Optional<TeamMember> already = config.getTeamManager().getMember(matched.getTeamId(), memberName);
        if (already.isPresent() && already.get().isBound()) {
            return;
        }

        boolean bound = config.getTeamManager().bindMemberSession(matched.getTeamId(), memberName, sessionId, actorId);
        if (!bound) {
            throw new NopAiAgentException(
                    "Auto-bind failed: member '" + memberName
                            + "' declares <team-member> but is not in the lead's team roster, "
                            + "or the team is not in a bindable state. teamName=" + teamName
                            + ", sessionId=" + sessionId);
        }
    }

    /**
     * runtime is configured (Design Decision #7).
     */
    public String resolveActorId(String sessionId) {
        if (config.getActorRuntime().isEnabled()) {
            java.util.Optional<AgentActor> actor = config.getActorRuntime().getActorBySession(sessionId);
            if (actor.isPresent()) {
                return actor.get().getActorId();
            }
        }
        return sessionId;
    }
}

