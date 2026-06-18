package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;

/**
 * Pluggable extension point that decides <b>which member targets</b> a
 * single team-task graph node fans out to (plan 244 /
 * L4-multi-member-per-task-routing, design 裁定 2).
 *
 * <p>The router is consulted at <b>graph build time</b> (inside
 * {@code TeamTaskFlowOrchestrator.buildGraphForExecution}), once per team
 * task. It returns a {@link MemberDispatchPlan} describing the N dispatch
 * targets (bound +/or spawn) and the reduction strategy. The orchestrator
 * consumes the plan to select the node step:
 * <ul>
 *   <li>Single BOUND target → existing {@code MemberAgentTaskStep}
 *       (line-for-line zero-regression for plans 233/241).</li>
 *   <li>Single SPAWN target → existing {@code SpawnMemberAgentTaskStep}
 *       (line-for-line zero-regression for plans 238/243).</li>
 *   <li>Multiple targets → fan-out node step (N member futures composed
 *       under the plan's reduction strategy, single CLAIMED → COMPLETED
 *       transition when reduction succeeds).</li>
 *   <li>Empty plan → honest failure (the orchestrator throws, the task
 *       stays in CREATED; Minimum Rules #24).</li>
 * </ul>
 *
 * <h2>Shipped default</h2>
 * <p>{@link NoOpTaskMemberRouter} (alias {@link SingleTaskMemberRouter})
 * reproduces the pre-244 single-member behaviour exactly: for a team with
 * a bound member it returns a singleton BOUND plan (the existing
 * {@code resolveMember} result wrapped as a one-element list); for a team
 * with no bound member it returns a singleton SPAWN plan (the existing
 * {@code DefaultMemberSpawner.resolveSpawnTarget} "prefer MEMBER role /
 * fallback any spec" choice, re-implemented at the routing layer on the
 * public {@link io.nop.ai.agent.team.TeamSpec#getMemberSpecs()} data
 * rather than calling the spawner's private method); for a team with no
 * bound member and no memberSpecs it returns an <b>empty</b> plan (which
 * the orchestrator converts into the existing honest failure for the
 * unbound-no-specs case). This makes single-member teams behave
 * line-for-line identically to plans 233/238/241/243 — zero regression.
 *
 * <h2>Single routing extension point covering both halves (design 裁定 2)</h2>
 * <p>Routing the bound half (select N bound sessions) and the spawn half
 * (select N spawn target memberSpecs) is decided by a <b>single</b>
 * router. Each {@link DispatchTarget} in the returned plan is tagged BOUND
 * or SPAWN so the orchestrator's fan-out step knows which execution path
 * to take per target. This avoids a dual router (one per half) and the
 * associated wiring complexity.
 *
 * <h2>Build-time, non-executing</h2>
 * <p>The router MUST NOT execute any agent or call any spawner. Its job is
 * purely a build-time selection over public data (the team's bound roster
 * + declarative memberSpecs). The actual execution (engine.execute for
 * bound targets, spawnMember for spawn targets) happens at node run time
 * inside the fan-out step — preserving DAG dependency order (plan 238
 * 裁定 1, unchanged).
 *
 * <h2>Wire-at-consumer</h2>
 * <p>The router is injected into {@code TeamTaskFlowOrchestrator} (the
 * consumer) via a setter / constructor, null-safe → NoOp shipped default.
 * This mirrors the established Layer 4 extension-point wiring convention
 * ({@code IMemberSpawner}, {@code ITeamAclChecker}, {@code IResourceGuard},
 * {@code IFencingTokenService}, {@code IDaemonCoordinator} all follow the
 * same pattern).
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 2.
 */
public interface ITaskMemberRouter {

    /**
     * Decide which member targets a single team-task graph node fans out to.
     *
     * <p><b>Honest contract</b> (Minimum Rules #24):
     * <ul>
     *   <li>Return a non-null {@link MemberDispatchPlan}. An empty plan
     *       (zero targets) is a legitimate, honest "no dispatchable member"
     *       result — the orchestrator throws an honest failure and leaves
     *       the task in CREATED (never silently skipping the node).</li>
     *   <li>This method MUST NOT execute any agent or call any spawner.
     *       It is a build-time selection over public team data.</li>
     *   <li>This method MUST NOT return {@code null}. Return an empty plan
     *       for the "no member" case instead.</li>
     * </ul>
     *
     * @param team the live team snapshot (whose {@link Team#getMembers()}
     *             provides the bound roster and {@link io.nop.ai.agent.team.Team#getSpec()}
     *             provides the declarative memberSpecs) — non-null
     * @param task the team task to dispatch — non-null
     * @return a non-null dispatch plan (possibly empty when no member can
     *         be dispatched)
     */
    MemberDispatchPlan route(Team team, TeamTask task);
}
