package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.team.TeamMemberSpec;

import java.util.Objects;

/**
 * One member target in a {@link MemberDispatchPlan} produced by
 * {@link ITaskMemberRouter} (plan 244 / L4-multi-member-per-task-routing).
 *
 * <p>A dispatch target is <b>either</b> an already-bound member session
 * (the {@code bound} half) <b>or</b> a declarative {@link TeamMemberSpec}
 * that must be spawned at node run time (the {@code spawn} half). The
 * single dispatch-target type unifies both halves so a {@link MemberDispatchPlan}
 * can mix bound and spawn targets in one fan-out (design 裁定 2 — single
 * routing extension point covering both halves, avoiding a dual extension
 * point per half).
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code kind} — {@link Kind#BOUND} or {@link Kind#SPAWN}
 *       (non-null).</li>
 *   <li>{@code memberName} — the unique member identifier within the team
 *       (non-null).</li>
 *   <li>{@code sessionId} — when {@code kind == BOUND}, the already-bound
 *       member session id to delegate to (non-null). {@code null} when
 *       {@code kind == SPAWN}.</li>
 *   <li>{@code agentName} — when {@code kind == BOUND}, the resolved agent
 *       model name (may be {@code null} when the bound member's spec is
 *       absent — the engine treats a null agentName as "use the session's
 *       bound agent"). {@code null} when {@code kind == SPAWN}.</li>
 *   <li>{@code spawnTarget} — when {@code kind == SPAWN}, the declarative
 *       {@link TeamMemberSpec} whose {@code agentModel} drives the spawn
 *       (non-null). {@code null} when {@code kind == BOUND}.</li>
 * </ul>
 *
 * <p>Immutable. Use the {@link #bound} / {@link #spawn} factories.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing).
 */
public final class DispatchTarget {

    /**
     * Whether this dispatch target is an already-bound member (delegated via
     * the engine on its existing session) or a declarative memberSpec that
     * must be spawned at node run time.
     */
    public enum Kind {
        /**
         * Already-bound member: the orchestrator delegates the task to
         * {@code sessionId} via {@code IAgentEngine.execute} on its existing
         * session (no spawn).
         */
        BOUND,
        /**
         * Declarative memberSpec that must be spawned at node run time: the
         * orchestrator's spawn fan-out step calls {@code IMemberSpawner
         * .spawnMember} with a request carrying this {@code spawnTarget}
         * so the spawner spawns exactly this target (rather than re-resolving
         * one of its own).
         */
        SPAWN
    }

    private final Kind kind;
    private final String memberName;
    private final String sessionId;
    private final String agentName;
    private final TeamMemberSpec spawnTarget;

    private DispatchTarget(Kind kind, String memberName, String sessionId,
                           String agentName, TeamMemberSpec spawnTarget) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.memberName = Objects.requireNonNull(memberName, "memberName");
        if (kind == Kind.BOUND) {
            Objects.requireNonNull(sessionId, "sessionId for BOUND target");
        } else {
            Objects.requireNonNull(spawnTarget, "spawnTarget for SPAWN target");
        }
        this.sessionId = sessionId;
        this.agentName = agentName;
        this.spawnTarget = spawnTarget;
    }

    /**
     * Build a {@link Kind#BOUND} dispatch target — delegate to an
     * already-bound member session.
     *
     * @param memberName unique member identifier within the team (non-null)
     * @param sessionId  the already-bound member session id (non-null)
     * @param agentName  the resolved agent model name (may be null — engine
     *                   falls back to the session's bound agent)
     * @return an immutable BOUND dispatch target
     */
    public static DispatchTarget bound(String memberName, String sessionId, String agentName) {
        return new DispatchTarget(Kind.BOUND, memberName, sessionId, agentName, null);
    }

    /**
     * Build a {@link Kind#SPAWN} dispatch target — spawn the declarative
     * memberSpec at node run time.
     *
     * @param spawnTarget the declarative memberSpec whose agentModel drives
     *                    the spawn (non-null)
     * @return an immutable SPAWN dispatch target
     */
    public static DispatchTarget spawn(TeamMemberSpec spawnTarget) {
        Objects.requireNonNull(spawnTarget, "spawnTarget");
        return new DispatchTarget(Kind.SPAWN, spawnTarget.getMemberName(),
                null, null, spawnTarget);
    }

    /**
     * @return whether this target is bound or spawn.
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * @return the unique member identifier within the team.
     */
    public String getMemberName() {
        return memberName;
    }

    /**
     * @return the already-bound session id when {@code kind == BOUND};
     *         {@code null} when {@code kind == SPAWN}.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * @return the resolved agent model name when {@code kind == BOUND} (may
     *         be null); {@code null} when {@code kind == SPAWN}.
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * @return the declarative memberSpec to spawn when {@code kind == SPAWN};
     *         {@code null} when {@code kind == BOUND}.
     */
    public TeamMemberSpec getSpawnTarget() {
        return spawnTarget;
    }

    /**
     * @return {@code true} if this target is an already-bound member.
     */
    public boolean isBound() {
        return kind == Kind.BOUND;
    }

    /**
     * @return {@code true} if this target is a declarative spawn target.
     */
    public boolean isSpawn() {
        return kind == Kind.SPAWN;
    }

    @Override
    public String toString() {
        return "DispatchTarget{kind=" + kind + ", memberName='" + memberName + '\''
                + (sessionId != null ? ", sessionId='" + sessionId + '\'' : "")
                + (agentName != null ? ", agentName='" + agentName + '\'' : "")
                + (spawnTarget != null ? ", spawnTarget=" + spawnTarget : "")
                + '}';
    }
}
