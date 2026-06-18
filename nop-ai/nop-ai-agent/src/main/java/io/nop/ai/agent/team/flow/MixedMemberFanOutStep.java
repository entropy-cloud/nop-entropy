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
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.ai.agent.team.scheduler.SpawnMemberRequest;
import io.nop.ai.agent.team.scheduler.SpawnMemberResult;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.AbstractTaskStep;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * A nop-task {@link io.nop.task.ITaskStep} that fans a single team-task graph
 * node out to <b>a mix of bound and spawn targets</b> concurrently and
 * reduces their results under a unified reduction (plan 244 /
 * L4-multi-member-per-task-routing, design 裁定 2 — single routing extension
 * point covering both halves).
 *
 * <p>This is the mixed-half multi-member fan-out variant. It exists for
 * dispatch plans whose targets include <b>both</b> bound and spawn members.
 * Plans with only bound targets use {@link BoundMemberFanOutStep}; plans
 * with only spawn targets use {@link SpawnMemberFanOutStep}; singleton
 * plans use the original single-target steps. The mixed step unifies the
 * two execution paths under a single reduction strategy so a fan-out like
 * "2 bound members + 1 spawned member" reduces all 3 outcomes together.
 *
 * <h2>Execution model (mirrors BoundMemberFanOutStep + SpawnMemberFanOutStep)</h2>
 * <ol>
 *   <li><b>Claim synchronously</b> (single claim for the whole node).</li>
 *   <li><b>Fan out per target</b>: bound targets submit directly to
 *       {@link IAgentEngine#execute} (engine already async); spawn targets
 *       offload {@code spawnMember} via {@code supplyAsync} on the dedicated
 *       spawn executor (plan 243 design 裁定 3, reused) with tenant
 *       re-application (plan 243 design 裁定 2, reused). Each target maps to
 *       a {@link MemberExecOutcome}.</li>
 *   <li><b>Reduce</b>: the plan's {@link IReductionStrategy} decides node
 *       success vs. failure over the unified N outcomes (shipped default
 *       {@link AllMustSucceedReduction}).</li>
 *   <li><b>Complete once</b>: single {@code completeTask} on reduction
 *       success; reduction failure leaves the task CLAIMED.</li>
 * </ol>
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 2 / 6 / 7.
 */
public class MixedMemberFanOutStep extends AbstractTaskStep {

    private final TeamTask task;
    private final Team team;
    private final List<DispatchTarget> targets;
    private final String orchestratorSessionId;
    private final IAgentEngine agentEngine;
    private final IMemberSpawner memberSpawner;
    private final ITeamTaskStore taskStore;
    private final ExecutionRecorder recorder;
    private final IReductionStrategy reductionStrategy;
    private final Executor spawnExecutor;
    private final String capturedTenant;

    public MixedMemberFanOutStep(TeamTask task, Team team, List<DispatchTarget> targets,
                                 String orchestratorSessionId,
                                 IAgentEngine agentEngine, IMemberSpawner memberSpawner,
                                 ITeamTaskStore taskStore, ExecutionRecorder recorder,
                                 Executor spawnExecutor, String capturedTenant,
                                 IReductionStrategy reductionStrategy) {
        this.task = task;
        this.team = team;
        this.targets = new ArrayList<>(targets);
        this.orchestratorSessionId = orchestratorSessionId;
        this.agentEngine = agentEngine;
        this.memberSpawner = memberSpawner;
        this.taskStore = taskStore;
        this.recorder = recorder;
        this.spawnExecutor = spawnExecutor;
        this.capturedTenant = capturedTenant;
        this.reductionStrategy = reductionStrategy;
        if (this.targets.size() < 2) {
            throw new IllegalArgumentException(
                    "MixedMemberFanOutStep is for plans mixing bound and spawn targets with size >= 2; "
                            + "got size=" + this.targets.size() + " (use the single-target step or a same-kind fan-out step)");
        }
        boolean hasBound = false;
        boolean hasSpawn = false;
        for (DispatchTarget t : this.targets) {
            if (t.isBound()) {
                hasBound = true;
            } else if (t.isSpawn()) {
                hasSpawn = true;
            } else {
                throw new IllegalArgumentException(
                        "MixedMemberFanOutStep accepts only BOUND or SPAWN targets; got " + t.getKind());
            }
        }
        if (!(hasBound && hasSpawn)) {
            throw new IllegalArgumentException(
                    "MixedMemberFanOutStep requires both BOUND and SPAWN targets; got hasBound=" + hasBound
                            + ", hasSpawn=" + hasSpawn + " (use the same-kind fan-out step for a uniform plan)");
        }
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String taskId = task.getTaskId();
        recorder.markStart(taskId);

        // 1. Claim SYNCHRONOUSLY (single claim for the whole node).
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

        // 2. Fan out per target. Bound → engine.execute (async). Spawn →
        //    supplyAsync(spawnMember) on the dedicated executor.
        List<CompletableFuture<MemberExecOutcome>> perMember = new ArrayList<>(targets.size());
        for (DispatchTarget target : targets) {
            if (target.isBound()) {
                perMember.add(executeBoundMember(target, taskId));
            } else {
                perMember.add(spawnOneTarget(target, taskId));
            }
        }

        // 3. Reduce + 4. Complete once (shared helper).
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
     * Execute one bound dispatch target (mirrors BoundMemberFanOutStep).
     */
    private CompletableFuture<MemberExecOutcome> executeBoundMember(DispatchTarget target, String taskId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("teamTaskId", taskId);
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
     * Spawn one spawn dispatch target (mirrors SpawnMemberFanOutStep).
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
                                    Collections.emptyList(), 0, 0L, 0L,
                                    "spawner returned DISPATCHED with null executionResult"));
                }
                return MemberExecOutcome.completed(target, executionResult);
            default:
                throw new IllegalStateException(
                        "unhandled spawn result status: " + spawnResult.getStatus());
        }
    }

    private String buildPrompt(TeamTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Execute team task: ").append(task.getSubject());
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            sb.append("\n").append(task.getDescription());
        }
        return sb.toString();
    }
}
