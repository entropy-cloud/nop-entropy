package io.nop.ai.agent.team.scheduler;

import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamMemberSpec;
import io.nop.ai.agent.team.TeamTask;

import java.util.Objects;

/**
 * Immutable input to {@link io.nop.ai.agent.team.IMemberSpawner#spawnMember}.
 *
 * <p>Carries everything a functional spawner needs to resolve a spawn target
 * (the team snapshot, including its declarative {@link io.nop.ai.agent.team.TeamSpec}
 * memberSpecs) and to construct the execution request (the task whose
 * subject/description becomes the agent prompt). The daemon session id is
 * included for audit-trail metadata (design 裁定 4 — spawned execution carries
 * the same {@code teamTaskId}/{@code teamId}/{@code daemonSessionId} metadata
 * as a bound-member dispatch).
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code team} — the live team snapshot; its {@link io.nop.ai.agent.team.Team#getSpec()}
 *       provides the declarative memberSpecs the spawner resolves a target
 *       from (non-null).</li>
 *   <li>{@code task} — the team task to dispatch; its subject/description
 *       become the spawned agent's prompt (non-null).</li>
 *   <li>{@code daemonSessionId} — the daemon's session identity, recorded in
 *       the spawned execution's metadata for audit traceability (non-null,
 *       non-blank).</li>
 *   <li>{@code target} — <b>optional additive target memberSpec</b> (plan 244
 *       / L4-multi-member-per-task-routing, design 裁定 6). When non-null,
 *       the spawner spawns <b>this specific target</b> directly (the
 *       pre-resolved memberSpec from a fan-out routing decision) rather than
 *       re-resolving one of its own. When {@code null}, the spawner
 *       self-resolves a target via its own {@code resolveSpawnTarget}
 *       fallback (the daemon path's single-target behaviour — line-for-line
 *       zero-regression for plans 236/237/243). The field is purely
 *       additive: the original 3-arg constructor preserves {@code target =
 *       null}, so every existing call site (daemon, orchestrator spawn step
 *       pre-244) is unchanged.</li>
 * </ul>
 *
 * <p>See plan 237 ({@code L4-auto-spawn-member-agent}), design 裁定 4;
 * plan 244 ({@code L4-multi-member-per-task-routing}), design 裁定 6 for
 * the additive target field.
 */
public final class SpawnMemberRequest {

    private final Team team;
    private final TeamTask task;
    private final String daemonSessionId;
    private final TeamMemberSpec target;

    /**
     * Construct an immutable spawn request with no explicit target (the
     * spawner self-resolves a target). This is the original 3-arg
     * constructor; every existing call site (daemon, orchestrator spawn
     * step pre-244) uses it and is unchanged (zero regression).
     *
     * @param team            the live team snapshot (non-null)
     * @param task            the team task to dispatch (non-null)
     * @param daemonSessionId the daemon session identity for audit metadata
     *                        (non-null, non-blank)
     */
    public SpawnMemberRequest(Team team, TeamTask task, String daemonSessionId) {
        this(team, task, daemonSessionId, null);
    }

    /**
     * Construct an immutable spawn request with an explicit pre-resolved
     * target memberSpec (plan 244 / L4-multi-member-per-task-routing,
     * design 裁定 6). When {@code target} is non-null, the spawner spawns
     * <b>this specific target</b> directly rather than self-resolving one.
     * When {@code target} is null, the spawner self-resolves (identical to
     * the 3-arg constructor).
     *
     * @param team            the live team snapshot (non-null)
     * @param task            the team task to dispatch (non-null)
     * @param daemonSessionId the daemon session identity for audit metadata
     *                        (non-null, non-blank)
     * @param target          the pre-resolved target memberSpec to spawn
     *                        (may be null — the spawner self-resolves)
     */
    public SpawnMemberRequest(Team team, TeamTask task, String daemonSessionId,
                              TeamMemberSpec target) {
        this.team = Objects.requireNonNull(team, "team");
        this.task = Objects.requireNonNull(task, "task");
        Objects.requireNonNull(daemonSessionId, "daemonSessionId");
        if (daemonSessionId.isBlank()) {
            throw new IllegalArgumentException("daemonSessionId must not be blank");
        }
        this.daemonSessionId = daemonSessionId;
        this.target = target;
    }

    /**
     * @return the live team snapshot (whose {@link io.nop.ai.agent.team.Team#getSpec()}
     *         carries the declarative memberSpecs).
     */
    public Team getTeam() {
        return team;
    }

    /**
     * @return the team task whose subject/description become the spawned
     *         agent's prompt.
     */
    public TeamTask getTask() {
        return task;
    }

    /**
     * @return the daemon session identity recorded in the spawned execution's
     *         metadata for audit traceability.
     */
    public String getDaemonSessionId() {
        return daemonSessionId;
    }

    /**
     * @return the pre-resolved target memberSpec when set (plan 244); {@code null}
     *         when the spawner should self-resolve a target (the daemon and
     *         pre-244 orchestrator spawn path).
     */
    public TeamMemberSpec getTarget() {
        return target;
    }
}
