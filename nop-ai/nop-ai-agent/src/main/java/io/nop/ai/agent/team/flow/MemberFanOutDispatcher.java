package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Shared, nop-task-runtime-agnostic dispatcher that builds the per-target
 * member-execution futures for a {@link MemberDispatchPlan}, reduces them
 * under the plan's {@link IReductionStrategy}, and — on reduction success —
 * performs the single {@code completeTask} CAS under the caller's captured
 * tenant (plan 245 / daemon dispatch parity, design 裁定 1).
 *
 * <p>This is the single canonical implementation of the fan-out + reduce +
 * complete chain. It is consumed by BOTH:
 * <ul>
 *   <li>the nop-task fan-out step variants
 *       ({@link BoundMemberFanOutStep} / {@link SpawnMemberFanOutStep} /
 *       {@link MixedMemberFanOutStep}) — the programmatic orchestrator path
 *       (plans 244);</li>
 *   <li>{@code TeamTaskSchedulerDaemon} — the unattended dispatch path
 *       (plan 245).</li>
 * </ul>
 * Sharing one implementation eliminates the dual-parallel-code-path drift
 * risk that plan 245 design 裁定 5 explicitly rejects (Anti-Hollow #8/#22).
 * Pre-245 the reduce+complete chain lived in the package-private
 * {@code FanOutReduceComplete} and the per-target future builders were
 * duplicated across the three step variants; both are consolidated here.
 *
 * <h2>Execution model</h2>
 * <ol>
 *   <li><b>Build per-target futures</b>: for each {@link DispatchTarget} in
 *       the plan, a {@link CompletableFuture}<{@link MemberExecOutcome}> is
 *       constructed. BOUND targets submit directly to
 *       {@link IAgentEngine#execute} (the engine is already async, so no
 *       {@code supplyAsync} offload — design 裁定 7 of plan 244). SPAWN
 *       targets offload the synchronous {@code spawnMember} call to the
 *       dedicated spawn executor via {@code supplyAsync} (plan 243 design
 *       裁定 3) and re-apply the captured tenant inside the worker
 *       (plan 243 design 裁定 2).</li>
 *   <li><b>Reduce</b>: when every per-target future has settled, the plan's
 *       {@link IReductionStrategy} decides node success vs. failure over the
 *       collected {@link MemberExecOutcome} list (shipped default
 *       {@link AllMustSucceedReduction} requires every member to reach
 *       {@link AgentExecStatus#completed}).</li>
 *   <li><b>Complete once</b>: on reduction success the dispatcher re-applies
 *       the caller's captured tenant and performs the single
 *       {@code completeTask} CAS (CLAIMED → COMPLETED). The reduction +
 *       complete chain runs on whatever thread completed the last member
 *       future, which may have cleared its tenant context (a spawn worker's
 *       {@code finally}), so the tenant is re-applied explicitly and cleared
 *       in {@code finally}.</li>
 *   <li><b>Honest failure</b>: on reduction failure, reduction decline, or
 *       {@code completeTask} CAS loss, the returned future completes with
 *       {@link MemberDispatchOutcome#failed} and the task is LEFT IN CLAIMED
 *       (not abandoned — plan 245 design 裁定 3: the recovery model is plan
 *       240 reclaim, not terminal abandon; mirrors the orchestrator
 *       failure model line-for-line). No Silent No-Op #24.</li>
 * </ol>
 *
 * <h2>Does NOT claim</h2>
 * The caller MUST perform the synchronous CREATED → CLAIMED claim (and the
 * already-COMPLETED idempotent short-circuit) BEFORE invoking
 * {@link #dispatch}. The dispatcher assumes the task is already CLAIMED by
 * the caller's session id and only drives the CLAIMED → COMPLETED half of
 * the state machine (on success). This preserves DAG dependency order for
 * the orchestrator and the daemon's per-task claim semantics.
 *
 * <h2>Empty plan</h2>
 * The caller MUST pre-check the empty-plan case (plan 244 / 245 design: an
 * empty plan is an honest failure raised by the caller, not a hollow node).
 * The dispatcher defends anyway: an empty target list completes the returned
 * future with {@link MemberDispatchOutcome#failed} (never a vacuous success).
 *
 * <p>See plan 245 (daemon dispatch parity), design 裁定 1 / 3 / 4 / 5.
 */
public final class MemberFanOutDispatcher {

    private MemberFanOutDispatcher() {
    }

    /**
     * Build the per-target member futures for the given dispatch shape, reduce
     * them, and on success perform the single {@code completeTask} CAS under
     * the caller's captured tenant.
     *
     * <p><b>Async contract</b>: the returned future is ALREADY COMPLETE if
     * every underlying member future was already complete at construction
     * time (e.g. a bound-member plan whose {@link IAgentEngine#execute}
     * returned an already-completed future). This is what makes the daemon's
     * per-cycle dispatch non-blocking on already-complete futures (zero
     * regression for fast test engines) while remaining genuinely async for
     * real engines — the caller may inspect {@link CompletableFuture#isDone}
     * immediately to record synchronous outcomes, or attach a
     * {@code whenComplete} for async tracking.
     *
     * @param task            the claimed team task (non-null). MUST be the
     *                        task returned by {@code claimTask} (carrying the
     *                        claim epoch on {@link TeamTask#getClaimEpoch()},
     *                        plan 279 / AR-01) — the dispatcher binds this
     *                        epoch into the single {@code completeTask} CAS so
     *                        a stale in-flight dispatcher cannot complete a
     *                        task reclaimed and re-claimed under a new epoch.
     * @param team            the live team snapshot (non-null when the plan
     *                        contains any SPAWN target — the spawner needs it
     *                        to build the {@link SpawnMemberRequest}; may be
     *                        null for an all-BOUND plan)
     * @param targets         the dispatch targets (non-null; may be empty —
     *                        defended as honest failure, never vacuous
     *                        success). The caller pre-checks empty as a
     *                        build-time honest failure.
     * @param reductionStrategy the reduction strategy for the N futures (non-null)
     * @param agentEngine     the member-agent engine (non-null)
     * @param memberSpawner   the member spawner for SPAWN targets (non-null;
     *                        NoOp shipped default declines honestly)
     * @param taskStore       the task store (non-null; used for the single
     *                        {@code completeTask} CAS on success)
     * @param dispatchSessionId the session id that claimed the task and will
     *                        be recorded as its completer (non-null)
     * @param spawnExecutor   a dedicated executor independent of the
     *                        {@code commonPool} used to offload SPAWN
     *                        targets' synchronous {@code spawnMember}
     *                        (non-null when {@code targets} contains any
     *                        SPAWN target; may be null for an all-BOUND plan)
     * @param capturedTenant  the caller's tenant captured at dispatch entry,
     *                        re-applied around the single
     *                        {@code completeTask} CAS (may be null)
     * @return a future that completes with {@link MemberDispatchOutcome#completed}
     *         when the task transitioned CLAIMED → COMPLETED, or
     *         {@link MemberDispatchOutcome#failed} when the task stays CLAIMED
     */
    public static CompletableFuture<MemberDispatchOutcome> dispatch(
            TeamTask task,
            Team team,
            List<DispatchTarget> targets,
            IReductionStrategy reductionStrategy,
            IAgentEngine agentEngine,
            IMemberSpawner memberSpawner,
            ITeamTaskStore taskStore,
            String dispatchSessionId,
            Executor spawnExecutor,
            String capturedTenant) {

        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(reductionStrategy, "reductionStrategy");
        Objects.requireNonNull(memberSpawner, "memberSpawner");
        Objects.requireNonNull(taskStore, "taskStore");
        Objects.requireNonNull(dispatchSessionId, "dispatchSessionId");

        // Defend empty plan (caller pre-checks; this is the safety net —
        // never a vacuous success, Minimum Rules #24).
        if (targets.isEmpty()) {
            return CompletableFuture.completedFuture(MemberDispatchOutcome.failed(
                    new NopAiAgentException(
                            "nop.ai.team.flow.fanout-empty-plan: dispatch plan produced zero targets for taskId="
                                    + task.getTaskId() + ", teamId=" + task.getTeamId()
                                    + " (no dispatchable member)")));
        }
        // Validate engine / spawner / executor / team availability against
        // the target kinds actually present (symmetric availability contract:
        // agentEngine is required only for BOUND targets; spawnExecutor +
        // team only for SPAWN targets). This lets a spawn-only caller omit
        // the engine and a bound-only caller omit the executor / team.
        boolean hasBound = false;
        boolean hasSpawn = false;
        for (DispatchTarget t : targets) {
            if (t.isBound()) {
                hasBound = true;
            } else if (t.isSpawn()) {
                hasSpawn = true;
            }
        }
        if (hasBound && agentEngine == null) {
            throw new NopAiAgentException(
                    "nop.ai.team.flow.fanout-no-engine: plan contains a BOUND target for taskId="
                            + task.getTaskId()
                            + " but no agent engine was supplied");
        }
        if (hasSpawn) {
            if (spawnExecutor == null) {
                throw new NopAiAgentException(
                        "nop.ai.team.flow.fanout-no-spawn-executor: plan contains a SPAWN target for taskId="
                                + task.getTaskId()
                                + " but no dedicated spawn executor was supplied (plan 243 design 裁定 3)");
            }
            if (team == null) {
                throw new NopAiAgentException(
                        "nop.ai.team.flow.fanout-no-team: plan contains a SPAWN target for taskId="
                                + task.getTaskId()
                                + " but no team snapshot was supplied (the spawner needs it)");
            }
        }

        // 1. Build per-target futures (BOUND → engine.execute async; SPAWN →
        //    supplyAsync(spawnMember) on the dedicated executor with tenant
        //    re-application).
        final Team teamForSpawn = team;
        List<CompletableFuture<MemberExecOutcome>> perMember = new ArrayList<>(targets.size());
        for (DispatchTarget target : targets) {
            if (target.isBound()) {
                perMember.add(executeBoundMember(target, task, agentEngine));
            } else {
                perMember.add(spawnOneTarget(target, teamForSpawn, task, memberSpawner,
                        dispatchSessionId, spawnExecutor, capturedTenant));
            }
        }

        // 2. Reduce + 3. Complete once (re-apply tenant around the single CAS).
        //    The recorder is unused by the shipped AllMustSucceedReduction
        //    (it only reads taskId); a throwaway recorder satisfies custom
        //    strategies that might call markFailed/markComplete.
        ExecutionRecorder throwawayRecorder = new ExecutionRecorder();
        return CompletableFuture
                .allOf(perMember.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<MemberExecOutcome> outcomes = new ArrayList<>(perMember.size());
                    for (CompletableFuture<MemberExecOutcome> f : perMember) {
                        // allOf guarantees every future is settled by now.
                        outcomes.add(f.join());
                    }
                    return outcomes;
                })
                .thenCompose(outcomes -> {
                    ReductionContext ctx = new ReductionContext(task.getTaskId(), throwawayRecorder);
                    return reductionStrategy.reduce(outcomes, ctx)
                            .thenApply(reductionSucceeded -> {
                                if (!reductionSucceeded) {
                                    // Honest failure: a custom strategy explicitly
                                    // declined. The shipped AllMustSucceedReduction
                                    // never does this (it throws), but defend.
                                    throw new NopAiAgentException(
                                            "nop.ai.team.flow.fanout-reduction-declined: reduction returned false for taskId="
                                                    + task.getTaskId() + ", strategy=" + reductionStrategy.name());
                                }
                                // Complete the task ONCE under the caller's tenant.
                                // This chain runs on whatever thread completed the
                                // last member future, which may have cleared its
                                // tenant context (spawn worker's finally) — re-apply.
                                ThreadLocalTenantResolver.set(capturedTenant);
                                try {
                                    // completeTask CAS binds the claim epoch
                                    // captured at claim time on this task
                                    // (plan 279 / AR-01). A stale in-flight
                                    // dispatcher holding a pre-reclaim epoch
                                    // is rejected → empty → honest failure.
                                    Optional<TeamTask> completed =
                                            taskStore.completeTask(task.getTaskId(), dispatchSessionId,
                                                    task.getClaimEpoch());
                                    if (completed.isEmpty()) {
                                        throw new NopAiAgentException(
                                                "nop.ai.team.flow.complete-failed: cannot complete team task taskId="
                                                        + task.getTaskId()
                                                        + " (not in CLAIMED status — possible concurrent transition)");
                                    }
                                    throwawayRecorder.markComplete(task.getTaskId());
                                    return MemberDispatchOutcome.completed();
                                } finally {
                                    ThreadLocalTenantResolver.clear();
                                }
                            });
                })
                // Convert any exception (reduction failure / CAS loss) into an
                // honest FAILED outcome — the task stays CLAIMED. Never
                // propagate a raw exception: the caller relies on a non-exceptional
                // MemberDispatchOutcome to record counters + store state uniformly.
                .exceptionally(ex -> MemberDispatchOutcome.failed(unwrap(ex)));
    }

    /**
     * Execute one BOUND dispatch target: submit the request to the engine and
     * map the engine future to a {@link MemberExecOutcome} (honest failure
     * semantics mirror {@code MemberAgentTaskStep}: engine exception →
     * ENGINE_FAILED; non-completed status → NOT_COMPLETED; otherwise COMPLETED).
     */
    private static CompletableFuture<MemberExecOutcome> executeBoundMember(
            DispatchTarget target, TeamTask task, IAgentEngine agentEngine) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("teamTaskId", task.getTaskId());
        metadata.put("teamId", task.getTeamId());
        metadata.put("fanoutMember", target.getMemberName());
        AgentMessageRequest request = new AgentMessageRequest(target.getAgentName(),
                buildPrompt(task), target.getSessionId(), metadata);

        CompletableFuture<AgentExecutionResult> engineFuture = agentEngine.execute(request);

        return engineFuture.handle((result, ex) -> {
            if (ex != null) {
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                        ? ex.getCause() : ex;
                return MemberExecOutcome.engineFailed(target, cause);
            }
            if (result.getStatus() != AgentExecStatus.completed) {
                return MemberExecOutcome.notCompleted(target, result);
            }
            return MemberExecOutcome.completed(target, result);
        });
    }

    /**
     * Spawn one SPAWN dispatch target inside a {@code supplyAsync} worker on
     * the dedicated spawn executor: re-apply the captured tenant, construct a
     * {@link SpawnMemberRequest} carrying the specific target, call
     * {@code spawnMember}, interpret the three-state result, and map to a
     * {@link MemberExecOutcome}. Tenant is cleared in finally so pooled worker
     * threads never leak tenant context (plan 243 裁定 2).
     */
    private static CompletableFuture<MemberExecOutcome> spawnOneTarget(
            DispatchTarget target, Team team, TeamTask task,
            IMemberSpawner memberSpawner, String dispatchSessionId,
            Executor spawnExecutor, String capturedTenant) {
        SpawnMemberRequest spawnReq = new SpawnMemberRequest(
                team, task, dispatchSessionId, target.getSpawnTarget());
        return CompletableFuture.supplyAsync(() -> {
            ThreadLocalTenantResolver.set(capturedTenant);
            try {
                return spawnAndInterpret(target, task, memberSpawner, spawnReq);
            } finally {
                ThreadLocalTenantResolver.clear();
            }
        }, spawnExecutor);
    }

    /**
     * Run inside the supplyAsync worker: call the spawner and interpret the
     * three-state result into a {@link MemberExecOutcome} (honest-failure
     * semantics mirror {@code SpawnMemberAgentTaskStep} line-for-line).
     */
    private static MemberExecOutcome spawnAndInterpret(
            DispatchTarget target, TeamTask task,
            IMemberSpawner memberSpawner, SpawnMemberRequest spawnReq) {
        SpawnMemberResult spawnResult;
        try {
            spawnResult = memberSpawner.spawnMember(spawnReq);
        } catch (RuntimeException e) {
            return MemberExecOutcome.spawnerThrew(target, e);
        }
        if (spawnResult == null) {
            return MemberExecOutcome.spawnerNull(target);
        }
        switch (spawnResult.getStatus()) {
            case NO_SPAWN:
                return MemberExecOutcome.noSpawn(target, spawnResult.getReason());
            case SPAWN_FAILED:
                return MemberExecOutcome.engineFailed(target, new NopAiAgentException(
                        "nop.ai.team.flow.spawn-failed: spawn execution failed for taskId=" + task.getTaskId()
                                + ", member=" + target.getMemberName()
                                + ", reason=" + spawnResult.getReason()));
            case DISPATCHED:
                AgentExecutionResult executionResult = spawnResult.getExecutionResult();
                if (executionResult == null
                        || executionResult.getStatus() != AgentExecStatus.completed) {
                    return MemberExecOutcome.notCompleted(target, executionResult != null
                            ? executionResult
                            : new AgentExecutionResult(AgentExecStatus.failed, null,
                                    Collections.emptyList(), 0, 0L, 0L,
                                    "spawner returned DISPATCHED with null executionResult"));
                }
                return MemberExecOutcome.completed(target, executionResult);
            default:
                throw new IllegalStateException(
                        "unhandled spawn result status: " + spawnResult.getStatus());
        }
    }

    private static String buildPrompt(TeamTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Execute team task: ").append(task.getSubject());
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            sb.append("\n").append(task.getDescription());
        }
        return sb.toString();
    }

    private static Throwable unwrap(Throwable ex) {
        Throwable c = ex;
        while (c instanceof CompletionException && c.getCause() != null) {
            c = c.getCause();
        }
        return c;
    }
}
