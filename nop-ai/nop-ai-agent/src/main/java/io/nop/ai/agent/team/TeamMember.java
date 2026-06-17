package io.nop.ai.agent.team;

import java.util.Objects;

/**
 * Runtime instance of a single member inside a {@link Team}. Created from a
 * {@link TeamMemberSpec} at team-creation or {@code addMember} time, then
 * optionally bound to a live agent session/actor via
 * {@link #bind(String, String)}.
 *
 * <h2>Identity model (immutable)</h2>
 * <ul>
 *   <li>{@code memberName} — the unique member identifier within the team
 *       (from {@link TeamMemberSpec#getMemberName()}).</li>
 *   <li>{@code role} — the {@link MemberRole} (identity tag, not enforced).</li>
 *   <li>{@code joinedAt} — wall-clock timestamp (millis) when the member was
 *       added to the team.</li>
 * </ul>
 *
 * <h2>Runtime binding (mutable, guarded by the owning ITeamManager)</h2>
 * <ul>
 *   <li>{@code sessionId} — the persistent session identity this member is
 *       bound to, or {@code null} until {@link #bind} is called.</li>
 *   <li>{@code actorId} — the runtime Actor identity (UUID) this member is
 *       bound to, or {@code null} until {@link #bind} is called.</li>
 * </ul>
 *
 * <p><b>Thread-safety contract</b>: {@code memberName}, {@code role}, and
 * {@code joinedAt} are immutable after construction. {@code sessionId} and
 * {@code actorId} are non-final and are mutated only by the owning
 * {@link ITeamManager} implementation (which serialises mutations via its
 * own {@code ConcurrentHashMap} / lock). This mirrors the
 * {@code AgentActor} volatile-field pattern: the data object itself does
 * not synchronise; thread safety is the manager's responsibility.
 *
 * <p>See plan 223 (L4-8-team-manager) and vision §8.
 */
public final class TeamMember {

    private final String memberName;
    private final MemberRole role;
    private final long joinedAt;

    private String sessionId;
    private String actorId;

    /**
     * Construct an unbound runtime member.
     *
     * @param memberName unique member identifier within the team (non-null)
     * @param role       the member role (non-null)
     * @param joinedAt   wall-clock timestamp (millis) when the member joined
     */
    public TeamMember(String memberName, MemberRole role, long joinedAt) {
        this.memberName = Objects.requireNonNull(memberName, "memberName");
        this.role = Objects.requireNonNull(role, "role");
        this.joinedAt = joinedAt;
    }

    /**
     * Construct a runtime member from a spec. The member starts unbound
     * ({@code sessionId}/{@code actorId} = null).
     *
     * @param spec     the member specification (non-null)
     * @param joinedAt wall-clock timestamp (millis) when the member joined
     */
    public TeamMember(TeamMemberSpec spec, long joinedAt) {
        this(Objects.requireNonNull(spec, "spec").getMemberName(),
                spec.getRole(), joinedAt);
    }

    /**
     * @return the unique member identifier within the team.
     */
    public String getMemberName() {
        return memberName;
    }

    /**
     * @return the member role (identity tag; ACL enforcement is a successor).
     */
    public MemberRole getRole() {
        return role;
    }

    /**
     * @return wall-clock timestamp (millis) when the member joined the team.
     */
    public long getJoinedAt() {
        return joinedAt;
    }

    /**
     * @return the persistent session identity this member is bound to, or
     *         {@code null} if not yet bound.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * @return the runtime Actor identity (UUID) this member is bound to, or
     *         {@code null} if not yet bound.
     */
    public String getActorId() {
        return actorId;
    }

    /**
     * Bind this member to a live agent session/actor. Called by the owning
     * {@link ITeamManager} (which serialises access). Both arguments must be
     * non-null; rebinding an already-bound member requires the manager to
     * clear the prior binding first (the manager enforces this).
     *
     * @param sessionId the persistent session identity (non-null)
     * @param actorId   the runtime Actor identity (non-null)
     */
    public void bind(String sessionId, String actorId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.actorId = Objects.requireNonNull(actorId, "actorId");
    }

    /**
     * @return {@code true} if this member has a bound session/actor.
     */
    public boolean isBound() {
        return sessionId != null;
    }

    @Override
    public String toString() {
        return "TeamMember{memberName='" + memberName + "', role=" + role
                + ", sessionId=" + sessionId + ", actorId=" + actorId
                + ", joinedAt=" + joinedAt + '}';
    }
}
