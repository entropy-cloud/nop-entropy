package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.security.ThreadLocalTenantResolver;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.AbstractTaskStep;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A nop-task {@link io.nop.task.ITaskStep} that fans a single team-task graph
 * node out to <b>N spawn targets</b> concurrently, spawning one member agent
 * per target and reducing their results (plan 244 /
 * L4-multi-member-per-task-routing, design 裁定 6).
 *
 * <p>This is the spawn-half multi-member fan-out variant of
 * {@link SpawnMemberAgentTaskStep} (which spawns a single target). It exists
 * only for dispatch plans whose N targets are <b>all spawn</b>; plans with
 * bound targets use {@link BoundMemberFanOutStep}, and plans mixing bound
 * and spawn targets use {@link MixedMemberFanOutStep}. Single spawn-target
 * plans use {@link SpawnMemberAgentTaskStep} directly (zero-regression
 * short-circuit in the orchestrator's build loop).
 *
 * <h2>Execution model (mirrors SpawnMemberAgentTaskStep, generalized to N)</h2>
 * <ol>
 *   <li><b>Claim synchronously</b> at node-trigger time (CREATED → CLAIMED)
 *       with the orchestrator session id — single claim for the whole node,
 *       preserving DAG dependency order.</li>
 *   <li><b>Fan out</b>: for each spawn dispatch target, construct a
 *       {@link SpawnMemberRequest} carrying <b>that specific target's
 *       memberSpec</b> (design 裁定 6 — the additive {@code target} field on
 *       {@link SpawnMemberRequest} tells the spawner to spawn exactly this
 *       target, rather than re-resolving one of its own), then offload the
 *       synchronous {@code spawnMember} call to the dedicated spawn
 *       executor via {@code supplyAsync} (plan 243 design 裁定 3, reused).
 *       Each supplyAsync future is mapped to a {@link MemberExecOutcome}.</li>
 *   <li><b>Tenant propagation</b> (plan 243 design 裁定 2, reused): each
 *       supplyAsync worker re-applies the captured tenant at the top and
 *       clears it in {@code finally}, so DB stores filter by the caller's
 *       tenant inside the worker.</li>
 *   <li><b>Reduce</b>: the plan's {@link IReductionStrategy} decides node
 *       success vs. failure over the N outcomes (shipped default
 *       {@link AllMustSucceedReduction}).</li>
 *   <li><b>Complete once</b>: on reduction success, a single
 *       {@code completeTask} (CLAIMED → COMPLETED); on reduction failure the
 *       future completes exceptionally and the task stays CLAIMED.</li>
 * </ol>
 *
 * <h2>Three-state spawner interpretation per target (plan 243 §3)</h2>
 * <p>Each per-target supplyAsync worker interprets the spawner's
 * {@link SpawnMemberResult} three-state:
 * <ul>
 *   <li>{@link SpawnMemberResult.Status#DISPATCHED} + completed → COMPLETED.</li>
 *   <li>{@link SpawnMemberResult.Status#DISPATCHED} + non-completed → NOT_COMPLETED.</li>
 *   <li>{@link SpawnMemberResult.Status#NO_SPAWN} → NO_SPAWN (honest failure).</li>
 *   <li>{@link SpawnMemberResult.Status#SPAWN_FAILED} → ENGINE_FAILED (honest failure).</li>
 *   <li>spawner returns {@code null} → SPAWNER_NULL (defensive honest failure).</li>
 *   <li>spawner throws → SPAWNER_THREW (defensive honest failure).</li>
 * </ul>
 * These per-target outcomes feed the reduction. The shipped
 * {@link AllMustSucceedReduction} fast-fails the node on the first failure;
 * other in-flight spawn workers run to completion and their results are
 * discarded (design 裁定 5 — no in-flight cancellation).
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 6.
 */
public class SpawnMemberFanOutStep extends AbstractTaskStep {

    private final TeamTask task;
    private final Team team;
    private final List<DispatchTarget> spawnTargets;
    private final String orchestratorSessionId;
    private final IMemberSpawner memberSpawner;
    private final ITeamTaskStore taskStore;
    private final ExecutionRecorder recorder;
    private final IReductionStrategy reductionStrategy;
    private final Executor spawnExecutor;
    private final String capturedTenant;

    /**
     * @param spawnTargets          the spawn dispatch targets to fan out to
     *                              (non-null, non-empty, all
     *                              {@link DispatchTarget.Kind#SPAWN}). The
     *                              orchestrator pre-checks the empty / mixed
     *                              case before constructing this step.
     * @param orchestratorSessionId the orchestrator session id used for the
     *                              single claim + complete (non-null)
     * @param spawnExecutor         a dedicated executor (independent of the
     *                              {@code commonPool}) used to offload the N
     *                              synchronous {@code spawnMember} calls
     *                              (plan 243 design 裁定 3, reused). Must not
     *                              be null.
     * @param capturedTenant        the caller's tenant captured by the
     *                              orchestrator at {@code executeAsync} entry,
     *                              re-applied inside each supplyAsync worker
     *                              (plan 243 design 裁定 2). May be null.
     * @param reductionStrategy     the reduction strategy for the N member
     *                              futures (non-null)
     */
    public SpawnMemberFanOutStep(TeamTask task, Team team, List<DispatchTarget> spawnTargets,
                                 String orchestratorSessionId,
                                 IMemberSpawner memberSpawner, ITeamTaskStore taskStore,
                                 ExecutionRecorder recorder, Executor spawnExecutor,
                                 String capturedTenant, IReductionStrategy reductionStrategy) {
        this.task = task;
        this.team = team;
        this.spawnTargets = new ArrayList<>(spawnTargets);
        this.orchestratorSessionId = orchestratorSessionId;
        this.memberSpawner = memberSpawner;
        this.taskStore = taskStore;
        this.recorder = recorder;
        this.spawnExecutor = Objects.requireNonNull(spawnExecutor,
                "spawnExecutor must not be null — a dedicated executor independent of the commonPool "
                        + "is required (plan 243 design 裁定 3)");
        this.capturedTenant = capturedTenant;
        this.reductionStrategy = reductionStrategy;
        if (this.spawnTargets.isEmpty()) {
            throw new IllegalArgumentException(
                    "SpawnMemberFanOutStep requires at least one spawn target — the orchestrator must pre-check the empty plan as an honest failure");
        }
        for (DispatchTarget t : this.spawnTargets) {
            if (!t.isSpawn()) {
                throw new IllegalArgumentException(
                        "SpawnMemberFanOutStep accepts only SPAWN targets; got " + t.getKind()
                                + " for memberName=" + t.getMemberName());
            }
        }
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String taskId = task.getTaskId();
        recorder.markStart(taskId);

        // 1. Claim SYNCHRONOUSLY at node-trigger time (single claim for the
        //    whole node — preserves DAG dependency order; claim CAS loss
        //    fast-fails synchronously; already-COMPLETED is an idempotent
        //    synchronous success).
        Optional<TeamTask> claimed = taskStore.claimTask(taskId, orchestratorSessionId);
        if (claimed.isEmpty()) {
            Optional<TeamTask> current = taskStore.getTask(taskId);
            if (current.isPresent() && current.get().getStatus() == TeamTaskStatus.COMPLETED) {
                recorder.markComplete(taskId);
                return TaskStepReturn.RETURN_RESULT("already-completed:" + taskId);
            }
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.claim-failed: cannot claim team task taskId=" + taskId
                            + " (missing or not in CREATED status)");
        }

        // 2. Fan out: one supplyAsync per spawn target. Each worker carries
        //    a SpawnMemberRequest with the specific target memberSpec so the
        //    spawner spawns exactly this target (design 裁定 6). Tenant is
        //    re-applied + cleared per worker (plan 243 裁定 2).
        List<CompletableFuture<MemberExecOutcome>> perMember = new ArrayList<>(spawnTargets.size());
        for (DispatchTarget target : spawnTargets) {
            perMember.add(spawnOneTarget(target, taskId));
        }

        // 3. Reduce + 4. Complete once (shared helper handles tenant
        //    re-application around completeTask — the reduction + complete
        //    chain runs on whatever thread completed the last supplyAsync
        //    worker, which has already cleared its tenant in finally).
        CompletableFuture<TaskStepReturn> steppedFuture = FanOutReduceComplete.reduceAndComplete(
                perMember, reductionStrategy, taskId, orchestratorSessionId,
                taskStore, recorder, capturedTenant);

        // Ensure markFailed is called on ANY exceptional completion (mirrors
        // BoundMemberFanOutStep). See that step for rationale.
        steppedFuture = steppedFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                recorder.markFailed(taskId);
            }
        });

        return TaskStepReturn.ASYNC_RETURN(steppedFuture);
    }

    /**
     * Spawn one target inside a supplyAsync worker: re-apply the captured
     * tenant, construct a {@link SpawnMemberRequest} carrying the specific
     * target, call {@code spawnMember}, interpret the three-state result,
     * and map to a {@link MemberExecOutcome}. Tenant is cleared in finally
     * so pooled worker threads never leak tenant context (plan 243 裁定 2).
     */
    private CompletableFuture<MemberExecOutcome> spawnOneTarget(DispatchTarget target, String taskId) {
        SpawnMemberRequest spawnReq = new SpawnMemberRequest(
                team, task, orchestratorSessionId, target.getSpawnTarget());
        return CompletableFuture.supplyAsync(() -> {
            ThreadLocalTenantResolver.set(capturedTenant);
            try {
                return spawnAndInterpret(target, taskId, spawnReq);
            } finally {
                ThreadLocalTenantResolver.clear();
            }
        }, spawnExecutor);
    }

    /**
     * Run inside the supplyAsync worker: call the spawner and interpret the
     * three-state result into a {@link MemberExecOutcome}. Honest-failure
     * semantics mirror {@link SpawnMemberAgentTaskStep#spawnAndComplete}
     * line-for-line (NO_SPAWN / SPAWN_FAILED / non-completed / null / throws
     * each map to a failure outcome; the reduction then fast-fails the node).
     */
    private MemberExecOutcome spawnAndInterpret(DispatchTarget target, String taskId,
                                                SpawnMemberRequest spawnReq) {
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
                        "nop.ai.team.flow.spawn-failed: spawn execution failed for taskId=" + taskId
                                + ", member=" + target.getMemberName()
                                + ", reason=" + spawnResult.getReason()));
            case DISPATCHED:
                AgentExecutionResult executionResult = spawnResult.getExecutionResult();
                if (executionResult == null
                        || executionResult.getStatus() != AgentExecStatus.completed) {
                    return MemberExecOutcome.notCompleted(target, executionResult != null
                            ? executionResult
                            : new AgentExecutionResult(AgentExecStatus.failed, null,
                                    java.util.Collections.emptyList(), 0, 0L, 0L,
                                    "spawner returned DISPATCHED with null executionResult"));
                }
                return MemberExecOutcome.completed(target, executionResult);
            default:
                throw new IllegalStateException(
                        "unhandled spawn result status: " + spawnResult.getStatus());
        }
    }
}
