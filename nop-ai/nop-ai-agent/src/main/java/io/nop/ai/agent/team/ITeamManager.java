package io.nop.ai.agent.team;

import java.util.Collection;
import java.util.Optional;

/**
 * Opt-in team lifecycle manager that creates, queries, mutates, and
 * dissolves agent teams. When the shipped {@link NoOpTeamManager} default
 * is wired, all write operations throw {@link UnsupportedOperationException}
 * (fast failure, not silent success — Minimum Rules #24) and all read
 * operations return empty results, so the engine sees zero behaviour
 * regression. When a functional implementation (e.g.
 * {@link InMemoryTeamManager}) is wired, integrators can manage team
 * lifecycles programmatically.
 *
 * <h2>Opt-in contract</h2>
 * Unlike {@code IActorRuntime} (which exposes {@code isEnabled()}), the
 * TeamManager has no engine-driven gate: the engine does not call these
 * methods on its execution path in the foundational slice. Team creation
 * and member binding are driven by integrators/tools (successor team
 * tools). The NoOp default is therefore a defensive guard: any direct
 * caller bypassing integration gets a fast failure instead of a silent
 * no-op.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #createTeam(TeamSpec)} — create a team from a spec, return a
 *       {@link Team} instance with a fresh UUID teamId. Initial status is
 *       {@link TeamStatus#CREATED}.</li>
 *   <li>{@link #addMember} — add a member to an existing team. Throws if
 *       the team does not exist, is disbanded, or already has a member
 *       with the same {@code memberName}.</li>
 *   <li>{@link #bindMemberSession} — bind a member to a live session/actor.
 *       The first successful binding transitions the team to
 *       {@link TeamStatus#ACTIVE}.</li>
 *   <li>{@link #getTeam} / {@link #getTeamBySession} / {@link #getActiveTeams}
 *       / {@link #getMember} — read-only queries returning snapshots.</li>
 *   <li>{@link #removeMember} — remove a member from a team.</li>
 *   <li>{@link #disbandTeam} — dissolve a team (status →
 *       {@link TeamStatus#DISBANDED}); the team remains queryable for
 *       history/audit.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent calls from multiple threads.
 * {@link InMemoryTeamManager} achieves this via {@code ConcurrentHashMap}
 * dual indices.
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §4.2 / §8.
 */
public interface ITeamManager {

    /**
     * Create a team from a specification. The manager generates a fresh
     * UUID teamId, initialises the member map from the spec's
     * {@link TeamSpec#getMemberSpecs()} (each member starts unbound), sets
     * status to {@link TeamStatus#CREATED}, and registers the team.
     *
     * @param spec the team specification (non-null)
     * @return the created {@link Team} instance with a fresh teamId
     */
    Team createTeam(TeamSpec spec);

    /**
     * Look up a team by its runtime identity.
     *
     * @param teamId the UUID team identity
     * @return the team, or empty if no team with this id is registered
     */
    Optional<Team> getTeam(String teamId);

    /**
     * Reverse-lookup the team that owns the member bound to the given
     * session.
     *
     * @param sessionId the persistent session identity
     * @return the owning team, or empty if no member is bound to this session
     */
    Optional<Team> getTeamBySession(String sessionId);

    /**
     * Dissolve a team: transition status to {@link TeamStatus#DISBANDED},
     * record the disband timestamp, and return the final team state. The
     * team remains queryable after disband (history/audit).
     *
     * @param teamId the UUID team identity
     * @return the final {@link Team} state
     * @throws io.nop.ai.agent.engine.NopAiAgentException if no team with this
     *         id is registered
     */
    Team disbandTeam(String teamId);

    /**
     * @return a snapshot collection of all currently non-disbanded teams
     *         (status {@link TeamStatus#CREATED} or {@link TeamStatus#ACTIVE}).
     *         Never null; empty when no active teams exist.
     */
    Collection<Team> getActiveTeams();

    /**
     * Add a member to an existing team.
     *
     * @param teamId     the UUID team identity
     * @param memberSpec the member specification (non-null)
     * @return the created {@link TeamMember} (unbound)
     * @throws io.nop.ai.agent.engine.NopAiAgentException if the team does not
     *         exist, is disbanded, or already has a member with the same
     *         {@code memberName}
     */
    TeamMember addMember(String teamId, TeamMemberSpec memberSpec);

    /**
     * Remove a member from a team. If the member had a bound session, the
     * session index is cleaned up too.
     *
     * @param teamId     the UUID team identity
     * @param memberName the member identifier within the team
     * @return {@code true} if a member was found and removed;
     *         {@code false} if the team or member does not exist
     */
    boolean removeMember(String teamId, String memberName);

    /**
     * Bind a member to a live runtime session/actor. The first successful
     * binding in a team transitions the team status from
     * {@link TeamStatus#CREATED} to {@link TeamStatus#ACTIVE}.
     *
     * @param teamId     the UUID team identity
     * @param memberName the member identifier within the team
     * @param sessionId  the persistent session identity to bind (non-null)
     * @param actorId    the runtime Actor identity to bind (non-null)
     * @return {@code true} if the binding succeeded;
     *         {@code false} if the team or member does not exist
     */
    boolean bindMemberSession(String teamId, String memberName,
                              String sessionId, String actorId);

    /**
     * Look up a single member of a team.
     *
     * @param teamId     the UUID team identity
     * @param memberName the member identifier within the team
     * @return the member, or empty if the team or member does not exist
     */
    Optional<TeamMember> getMember(String teamId, String memberName);
}
