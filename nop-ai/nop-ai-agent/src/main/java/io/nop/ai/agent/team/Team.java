package io.nop.ai.agent.team;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

/**
 * Runtime instance of an agent team, created by
 * {@link ITeamManager#createTeam(TeamSpec)}.
 *
 * <h2>Identity model (immutable)</h2>
 * <ul>
 *   <li>{@code teamId} — UUID generated at creation time by the manager.</li>
 *   <li>{@code spec} — the immutable {@link TeamSpec} this team was created
 *       from.</li>
 *   <li>{@code createdAt} — wall-clock timestamp (millis) at creation.</li>
 * </ul>
 *
 * <h2>Runtime state (mutable, guarded by the owning ITeamManager)</h2>
 * <ul>
 *   <li>{@code members} — the live member map (memberName → TeamMember),
 *       statically typed as {@link ConcurrentMap} so the thread-safety
 *       contract is expressed in the type system. The map itself is the one
 *       owned by the manager; the manager serialises access via
 *       {@code ConcurrentHashMap}. Callers must not mutate this map
 *       directly.</li>
 *   <li>{@code status} — the {@link TeamStatus}; transitions follow the
 *       state machine documented on the enum.</li>
 *   <li>{@code disbandedAt} — wall-clock timestamp (millis) recorded when
 *       the team is disbanded; {@code 0} until then.</li>
 * </ul>
 *
 * <p><b>Thread-safety contract</b>: {@code teamId}, {@code spec}, and
 * {@code createdAt} are immutable after construction. {@code members},
 * {@code status}, and {@code disbandedAt} are non-final and are mutated
 * only by the owning {@link ITeamManager} implementation (which serialises
 * mutations via its own {@code ConcurrentHashMap}). The {@code members}
 * field is statically typed as {@link ConcurrentMap} to make this
 * thread-safety contract visible in the type system — every construction
 * site supplies a {@code ConcurrentHashMap} (a {@code ConcurrentMap}
 * subtype). This mirrors the {@code AgentActor} volatile-field pattern:
 * the data object itself does not synchronise; thread safety is the
 * manager's responsibility. In particular, {@code status} and
 * {@code disbandedAt} are read by query methods after the manager has
 * published a consistent snapshot.
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §8.
 */
public final class Team {

    private final String teamId;
    private final TeamSpec spec;
    private final long createdAt;

    private final ConcurrentMap<String, TeamMember> members;
    private TeamStatus status;
    private long disbandedAt;

    /**
     * Construct a runtime team instance.
     *
     * @param teamId    UUID-generated runtime identity (non-null)
     * @param spec      the immutable team specification (non-null)
     * @param members   the live member map owned by the manager (non-null;
     *                  typed {@link ConcurrentMap} to express the
     *                  thread-safety contract in the type system; callers
     *                  must not mutate directly)
     * @param status    the initial status (non-null)
     * @param createdAt wall-clock timestamp (millis) at creation
     */
    public Team(String teamId, TeamSpec spec, ConcurrentMap<String, TeamMember> members,
                TeamStatus status, long createdAt) {
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.members = Objects.requireNonNull(members, "members");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = createdAt;
    }

    /**
     * @return the UUID-generated runtime identity.
     */
    public String getTeamId() {
        return teamId;
    }

    /**
     * @return the immutable team specification this team was created from.
     */
    public TeamSpec getSpec() {
        return spec;
    }

    /**
     * @return the live member map (memberName → TeamMember), statically
     *         typed as {@link ConcurrentMap} to express the thread-safety
     *         contract. The map is the one owned by the manager; callers
     *         must treat it as read-only.
     */
    public ConcurrentMap<String, TeamMember> getMembers() {
        return members;
    }

    /**
     * @return the current lifecycle status.
     */
    public TeamStatus getStatus() {
        return status;
    }

    /**
     * Transition the team's status. No validation of transition legality is
     * performed — the owning {@link ITeamManager} is responsible for
     * following the state machine documented in {@link TeamStatus}.
     *
     * @param status the target status (must not be null)
     */
    public void setStatus(TeamStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * @return wall-clock timestamp (millis) at creation.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * @return wall-clock timestamp (millis) when the team was disbanded, or
     *         {@code 0} if not disbanded.
     */
    public long getDisbandedAt() {
        return disbandedAt;
    }

    /**
     * Record the disband timestamp. Called by the owning
     * {@link ITeamManager} when transitioning to {@link TeamStatus#DISBANDED}.
     *
     * @param disbandedAt wall-clock timestamp (millis)
     */
    public void setDisbandedAt(long disbandedAt) {
        this.disbandedAt = disbandedAt;
    }

    @Override
    public String toString() {
        return "Team{teamId='" + teamId + "', teamName='" + spec.getTeamName()
                + "', status=" + status + ", memberCount=" + members.size()
                + ", createdAt=" + createdAt + '}';
    }
}
