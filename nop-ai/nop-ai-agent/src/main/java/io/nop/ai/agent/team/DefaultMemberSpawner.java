package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Functional {@link IMemberSpawner} that materialises a member agent for an
 * unbound-member team task by spawning a fresh execution based on the team's
 * declarative {@link TeamMemberSpec#getAgentModel()} (plan 237 /
 * {@code L4-auto-spawn-member-agent}).
 *
 * <h2>Spawn flow (per {@link #spawnMember} call)</h2>
 * <ol>
 *   <li><b>Resolve spawn target</b> (design 裁定 2): from the team's
 *       {@link TeamSpec#getMemberSpecs()}, prefer the first MEMBER-role spec,
 *       fallback any memberSpec (symmetric to the daemon's bound-member
 *       resolution strategy). {@link TeamMemberSpec#getAgentModel()} (non-null
 *       by construction) provides the agent configuration name to spawn. No
 *       memberSpec = honest {@link SpawnMemberResult.Status#NO_SPAWN} (cannot
 *       materialise a member that was never declared, Minimum Rules #24).</li>
 *   <li><b>Spawn (execute)</b> (design 裁定 4): construct an
 *       {@link AgentMessageRequest} whose {@code agentName} is the resolved
 *       {@code agentModel}, whose {@code userMessage} is built from the task's
 *       subject + description, whose {@code sessionId} is a fresh
 *       {@code "spawned-" + UUID} (so each spawn is an independent execution
 *       — per-task spawn, session reuse is a Non-Goal successor), and whose
 *       metadata carries {@code teamTaskId}/{@code teamId}/{@code daemonSessionId}
 *       for audit (identical metadata shape to a bound-member dispatch).
 *       Submit to {@link IAgentEngine#execute} and synchronous-join (same
 *       {@code execute(request).join()} semantics as the daemon's bound-member
 *       dispatch and plan 233 {@code MemberAgentTaskStep}).</li>
 *   <li><b>Honest outcome mapping</b> (design 裁定 4):
 *     <ul>
 *       <li>{@code execute} returned a result → {@link SpawnMemberResult#dispatched}
 *           with the wrapped {@link AgentExecutionResult} (the daemon decides
 *           complete vs. abandon from its status, identical to a bound-member
 *           dispatch).</li>
 *       <li>{@code execute} threw → {@link SpawnMemberResult#spawnFailed}
 *           with the cause's message (the daemon folds this into
 *           {@code DISPATCH_FAILED}, honest abandon).</li>
 *     </ul>
 *     The spawner itself does <b>not</b> interpret
 *     {@link AgentExecutionResult#getStatus()} — that is the daemon's job
 *     (so the daemon's complete/abandon logic for bound and spawned paths is
 *     unified). The spawner only distinguishes "got an execution result"
 *     (DISPATCHED) from "the execution itself failed to produce a result"
 *     (SPAWN_FAILED).</li>
 * </ol>
 *
 * <h2>Honest contract (No Silent No-Op #24)</h2>
 * The functional spawner never returns {@code null} and never silently
 * swallows a spawn attempt. Every outcome is an explicit
 * {@link SpawnMemberResult}:
 * <ul>
 *   <li>No declarative memberSpec → explicit {@code NO_SPAWN} with reason.</li>
 *   <li>Execution succeeded (returned a result, regardless of completed/failed
 *       status) → explicit {@code DISPATCHED} with the wrapped result.</li>
 *   <li>Execution threw → explicit {@code SPAWN_FAILED} with the cause.</li>
 * </ul>
 *
 * <h2>Bound-member priority (design 裁定 3)</h2>
 * The spawner is only consulted when the daemon's {@code resolveBoundMember}
 * returns {@code null} (no bound member). A team with a bound member never
 * reaches the spawner, so the spawner's spawn-on-demand behaviour is purely a
 * fallback that fills the "no bound member" gap. The spawner itself does not
 * check the bound roster (the daemon already did) — it trusts the daemon's
 * null-bound signal and proceeds to spawn.
 *
 * <h2>Team-level single member strategy (design 裁定 2)</h2>
 * The daemon resolves one bound member per team per scan (not per-task
 * routing). The spawner mirrors this: when {@link SpawnMemberRequest#getTarget()}
 * is {@code null} (the daemon and pre-244 orchestrator spawn path), it
 * resolves one declarative member per team (prefer MEMBER role, fallback
 * any spec) and spawns that single member for every task the daemon hands
 * it within a scan. When {@link SpawnMemberRequest#getTarget()} is non-null
 * (plan 244 / L4-multi-member-per-task-routing, design 裁定 6), the spawner
 * spawns that specific pre-resolved target directly — the multi-member
 * fan-out routing layer has already chosen it from the public memberSpecs.
 * The {@link IMemberSpawner#spawnMember} interface signature is unchanged;
 * the additive target field is the only extension (zero regression for the
 * daemon path). Per-task multi-member routing is delivered by the
 * orchestrator's fan-out step calling {@code spawnMember} once per target;
 * per-task member routing inside the daemon is a Non-Goal successor.
 *
 * <p>This spawner holds an {@link IAgentEngine} reference (constructor-injected)
 * so it can submit the spawned execution. It does <b>not</b> hold the daemon,
 * task store, or team manager — the daemon owns the complete/abandon state
 * transitions after the spawner returns.
 *
 * <p>See plan 237 ({@code L4-auto-spawn-member-agent}), design 裁定 1 / 2 / 3 / 4.
 */
public final class DefaultMemberSpawner implements IMemberSpawner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMemberSpawner.class);

    private final IAgentEngine agentEngine;

    /**
     * Construct a functional spawner backed by the given agent engine.
     *
     * @param agentEngine the engine used to spawn (execute) the member agent
     *                    (non-null)
     */
    public DefaultMemberSpawner(IAgentEngine agentEngine) {
        this.agentEngine = Objects.requireNonNull(agentEngine, "agentEngine");
    }

    @Override
    public SpawnMemberResult spawnMember(SpawnMemberRequest request) {
        Objects.requireNonNull(request, "request");
        Team team = request.getTeam();
        io.nop.ai.agent.team.TeamTask task = request.getTask();

        // --- Decision 2: resolve a spawn target. Plan 244 (design 裁定 6)
        // adds an optional additive `target` field to SpawnMemberRequest:
        //   * request.target != null → spawn THIS specific pre-resolved
        //     target (the fan-out routing layer already chose it from the
        //     public memberSpecs; this is the multi-member fan-out path).
        //   * request.target == null → self-resolve via the original
        //     team-level single-target strategy (prefer MEMBER role,
        //     fallback any spec). This is the daemon path's behaviour,
        //     line-for-line unchanged (zero regression for plans 236/237/243).
        TeamMemberSpec target = request.getTarget() != null
                ? request.getTarget()
                : resolveSpawnTarget(team);
        if (target == null) {
            // Honest NO_SPAWN: the team declared no members to spawn from.
            // Cannot materialise a member that was never declared.
            // (Minimum Rules #24 — explicit no-spawn, not a silent null.)
            String teamName = team.getSpec() != null ? team.getSpec().getTeamName() : team.getTeamId();
            return SpawnMemberResult.noSpawn(
                    "DefaultMemberSpawner: team '" + teamName
                            + "' has no declarative memberSpec to spawn from");
        }
        String agentModel = target.getAgentModel();
        // TeamMemberSpec.agentModel is non-null by construction; defensive.
        if (agentModel == null || agentModel.isBlank()) {
            return SpawnMemberResult.noSpawn(
                    "DefaultMemberSpawner: resolved memberSpec '" + target.getMemberName()
                            + "' has null/blank agentModel — cannot spawn");
        }

        // --- Decision 4: spawn (execute) synchronously with the same
        // execute(request).join() semantics as the daemon's bound-member
        // dispatch. Per-task spawn: a fresh session id so each spawn is an
        // independent execution (session reuse is a Non-Goal successor).
        String spawnedSessionId = "spawned-" + UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("teamTaskId", task.getTaskId());
        metadata.put("teamId", task.getTeamId());
        metadata.put("daemonSessionId", request.getDaemonSessionId());
        metadata.put("spawnedFromMemberSpec", target.getMemberName());

        AgentMessageRequest execRequest = new AgentMessageRequest(
                agentModel, buildPrompt(task), spawnedSessionId, metadata);

        AgentExecutionResult result;
        try {
            CompletableFuture<AgentExecutionResult> future = agentEngine.execute(execRequest);
            result = future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOG.warn("DefaultMemberSpawner: spawned agent threw for taskId={}, agentModel={} — SPAWN_FAILED",
                    task.getTaskId(), agentModel, cause);
            return SpawnMemberResult.spawnFailed(
                    "spawned agent threw: " + cause.toString());
        } catch (RuntimeException e) {
            // join() may surface a NopAiAgentException directly (not wrapped
            // in CompletionException) if execute(...) completed exceptionally
            // before the future was returned. Treat identically.
            LOG.warn("DefaultMemberSpawner: spawned agent threw for taskId={}, agentModel={} — SPAWN_FAILED",
                    task.getTaskId(), agentModel, e);
            return SpawnMemberResult.spawnFailed("spawned agent threw: " + e.toString());
        }

        // The wrapped result is returned even if its status is non-completed —
        // the daemon's complete/abandon logic (which inspects status) is
        // shared between the bound-member and spawned-member paths (decision 4).
        // The spawner does NOT interpret status here.
        if (result == null) {
            // Defensive: a well-behaved engine never returns null from a
            // completed future, but treat a null result as SPAWN_FAILED
            // (honest) rather than NPE.
            return SpawnMemberResult.spawnFailed(
                    "spawned agent returned null AgentExecutionResult");
        }
        return SpawnMemberResult.dispatched(result, agentModel, spawnedSessionId);
    }

    /**
     * Resolve a single team-level spawn target from the team's declarative
     * memberSpecs (design 裁定 2): prefer the first MEMBER-role spec, fallback
     * any memberSpec. Returns {@code null} if the team has no memberSpecs
     * (caller reports honest NO_SPAWN).
     *
     * <p>Symmetric to the daemon's {@code resolveBoundMember} strategy
     * (prefer MEMBER role, fallback any bound) but operating on declarative
     * specs instead of the bound roster.
     */
    private TeamMemberSpec resolveSpawnTarget(Team team) {
        TeamSpec spec = team.getSpec();
        if (spec == null) {
            return null;
        }
        TeamMemberSpec fallback = null;
        for (TeamMemberSpec ms : spec.getMemberSpecs()) {
            if (ms.getRole() == MemberRole.MEMBER) {
                return ms;
            }
            if (fallback == null) {
                fallback = ms;
            }
        }
        return fallback;
    }

    /**
     * Build the spawned agent's prompt from the task's subject + description.
     * Identical shape to the daemon's {@code buildPrompt} so a spawned
     * execution and a bound-member dispatch receive the same prompt for the
     * same task (decision 4 — same execution semantics).
     */
    private String buildPrompt(io.nop.ai.agent.team.TeamTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Execute team task: ").append(task.getSubject());
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            sb.append("\n").append(task.getDescription());
        }
        return sb.toString();
    }
}
