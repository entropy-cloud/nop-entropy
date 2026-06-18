package io.nop.ai.agent.team;

import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;

/**
 * Pluggable extension point that <b>spawns</b> a member agent for a team task
 * when the team has no already-bound member to delegate to (plan 237 /
 * {@code L4-auto-spawn-member-agent}).
 *
 * <p>The {@link io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon}
 * resolves a bound member for each ready task via its {@code resolveBoundMember}
 * helper (prefer MEMBER role, fallback any bound — same strategy as plan 233
 * {@code TeamTaskFlowOrchestrator.resolveMember}). When that resolution returns
 * {@code null} (no bound member for the team), the daemon consults the
 * configured {@code IMemberSpawner}: a <b>functional</b> spawner (e.g.
 * {@link DefaultMemberSpawner}) materialises a fresh member-agent execution
 * based on the team's declarative {@link TeamMemberSpec#getAgentModel()} and
 * returns a {@link SpawnMemberResult} that the daemon dispatches identically
 * to a bound member (same {@code IAgentEngine.execute} + complete/abandon
 * semantics, design 裁定 4). A {@link NoOpMemberSpawner} (shipped default)
 * returns an explicit {@link SpawnMemberResult.Status#NO_SPAWN} result, so the
 * daemon falls back to its pre-spawn behaviour (honest abandon,
 * {@code UNBOUND_MEMBER} outcome — zero regression).
 *
 * <p>This mirrors the established Layer 4 extension-point pattern in this
 * module: {@code ITeamAclChecker}/{@code NoOpTeamAclChecker}/{@code DefaultTeamAclChecker}
 * (plan 228), {@code IResourceGuard}/{@code NoOpResourceGuard}/{@code DefaultResourceGuard}
 * (plan 234), {@code IFencingTokenService}/{@code NoOpFencingTokenService}/{@code DefaultFencingTokenService}
 * (plan 235). The NoOp shipped default guarantees zero behaviour regression;
 * the functional implementation is opt-in via setter/constructor injection at
 * the consumer (the daemon — see design 裁定 5).
 *
 * <h2>Spawn target resolution (design 裁定 2)</h2>
 * <p>The functional spawner resolves a single team-level spawn target from the
 * team's declarative {@link TeamSpec#getMemberSpecs()}: prefer the first
 * MEMBER-role spec, fallback any memberSpec (symmetric to the daemon's
 * bound-member resolution strategy). {@link TeamMemberSpec#getAgentModel()}
 * (non-null by construction) provides the agent configuration name to spawn.
 * A team with no memberSpecs = no spawn target = honest {@code NO_SPAWN} (the
 * spawner cannot materialise a member that was never declared, Minimum Rules
 * #24).
 *
 * <h2>Honest contract (No Silent No-Op #24)</h2>
 * <ul>
 *   <li>{@link SpawnMemberResult.Status#DISPATCHED} — the spawner spawned a
 *       member agent and executed the task; the wrapped
 *       {@link io.nop.ai.agent.engine.AgentExecutionResult} reports the
 *       outcome of that execution.</li>
 *   <li>{@link SpawnMemberResult.Status#NO_SPAWN} — the spawner honestly
 *       declined to spawn (no memberSpec / no resolvable target). The daemon
 *       treats this as {@code UNBOUND_MEMBER} (abandon).</li>
 *   <li>{@link SpawnMemberResult.Status#SPAWN_FAILED} — the spawner attempted
 *       to spawn but the execution threw / returned a non-completed terminal
 *       status. The daemon treats this as {@code DISPATCH_FAILED} (abandon,
 *       identical to a bound-member dispatch failure).</li>
 * </ul>
 * The spawner never returns {@code null} and never silently swallows a spawn
 * failure: every outcome is an explicit {@link SpawnMemberResult} object.
 *
 * <h2>Wiring target (design 裁定 5)</h2>
 * <p>The spawner is injected into {@code TeamTaskSchedulerDaemon} (the
 * consumer), not into {@code DefaultAgentEngine}. This mirrors the
 * {@code IResourceGuard}→{@code InMemoryTeamManager} wire-at-consumer
 * convention: the spawner's only consumer is the daemon's dispatch path, so
 * the daemon owns its injection. NoOp default is null-safe (a {@code null}
 * spawner or an explicit {@link NoOpMemberSpawner} produces identical
 * behaviour).
 *
 * <p>See plan 237 ({@code L4-auto-spawn-member-agent}).
 */
public interface IMemberSpawner {

    /**
     * Attempt to spawn a member agent for the given team task and execute it
     * synchronously.
     *
     * <p><b>Honest contract</b> (Minimum Rules #24): this method never returns
     * {@code null} and never silently swallows a spawn attempt. Every outcome
     * is encoded as an explicit {@link SpawnMemberResult}:
     * <ul>
     *   <li>{@link SpawnMemberResult.Status#DISPATCHED} — spawn succeeded and
     *       the wrapped {@link io.nop.ai.agent.engine.AgentExecutionResult}
     *       reflects the execution outcome.</li>
     *   <li>{@link SpawnMemberResult.Status#NO_SPAWN} — the spawner honestly
     *       declined (e.g. no declarative memberSpec to spawn from).</li>
     *   <li>{@link SpawnMemberResult.Status#SPAWN_FAILED} — the spawner
     *       attempted to spawn but the execution failed (threw / returned a
     *       non-completed terminal status).</li>
     * </ul>
     *
     * @param request the spawn request carrying the team snapshot, the task
     *                to execute, and the daemon session id (non-null)
     * @return a non-null {@link SpawnMemberResult}; never {@code null}
     */
    SpawnMemberResult spawnMember(SpawnMemberRequest request);
}
