package io.nop.ai.agent.team;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Shipped no-op default for {@link ITeamManager}.
 *
 * <p>All <strong>write</strong> operations ({@link #createTeam},
 * {@link #disbandTeam}, {@link #addMember}, {@link #removeMember},
 * {@link #bindMemberSession}) throw {@link UnsupportedOperationException}
 * — a fast failure that signals "team management is not enabled", not a
 * silent success. This honours Minimum Rules #24 (No Silent No-Op): a
 * caller that depends on team lifecycle would otherwise mistake a silent
 * null/false for a real effect.
 *
 * <p>All <strong>read</strong> operations ({@link #getTeam},
 * {@link #getTeamBySession}, {@link #getActiveTeams}, {@link #getMember})
 * return empty results, consistent with the "no teams exist" semantics.
 *
 * <p>The engine uses this default out-of-the-box (via
 * {@code DefaultAgentEngine.teamManager}), so integrators see zero
 * behaviour regression unless they explicitly wire a functional manager
 * (e.g. {@link InMemoryTeamManager}) via
 * {@code DefaultAgentEngine.setTeamManager(...)}.
 *
 * <p>See plan 223 (L4-8-team-manager) and Minimum Rules #24.
 */
public final class NoOpTeamManager implements ITeamManager {

    private static final NoOpTeamManager INSTANCE = new NoOpTeamManager();

    private NoOpTeamManager() {
    }

    public static NoOpTeamManager noOp() {
        return INSTANCE;
    }

    private static UnsupportedOperationException notEnabled() {
        return new UnsupportedOperationException(
                "NoOpTeamManager: team management is not enabled "
                        + "(wire InMemoryTeamManager via DefaultAgentEngine.setTeamManager to enable)");
    }

    @Override
    public Team createTeam(TeamSpec spec) {
        throw notEnabled();
    }

    @Override
    public Optional<Team> getTeam(String teamId) {
        return Optional.empty();
    }

    @Override
    public Optional<Team> getTeamBySession(String sessionId) {
        return Optional.empty();
    }

    @Override
    public Team disbandTeam(String teamId) {
        throw notEnabled();
    }

    @Override
    public Collection<Team> getActiveTeams() {
        return Collections.emptyList();
    }

    @Override
    public TeamMember addMember(String teamId, TeamMemberSpec memberSpec) {
        throw notEnabled();
    }

    @Override
    public boolean removeMember(String teamId, String memberName) {
        throw notEnabled();
    }

    @Override
    public boolean bindMemberSession(String teamId, String memberName,
                                     String sessionId, String actorId) {
        throw notEnabled();
    }

    @Override
    public Optional<TeamMember> getMember(String teamId, String memberName) {
        return Optional.empty();
    }
}
