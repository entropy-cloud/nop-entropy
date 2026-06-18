package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.IMemberSpawner;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.Team;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.AbstractTaskStep;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
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

        // 2. Fan out + reduce + complete via the shared dispatcher (plan 245
        //    design 裁定 1: single canonical chain shared with the daemon —
        //    no dual code path). The dispatcher routes each target by kind:
        //    BOUND → engine.execute async, SPAWN → supplyAsync(spawnMember)
        //    on the dedicated executor with tenant re-application, then
        //    reduces the unified outcomes + performs the single completeTask
        //    CAS on success.
        CompletableFuture<MemberDispatchOutcome> dispatched = MemberFanOutDispatcher.dispatch(
                task, team, targets, reductionStrategy,
                agentEngine, memberSpawner, taskStore, orchestratorSessionId, spawnExecutor, capturedTenant);

        // Adapt to a nop-task TaskStepReturn + record markComplete/markFailed
        // on the STEP's shared recorder (the dispatcher uses a throwaway
        // recorder only to satisfy custom-strategy contracts — the step owns
        // the real execution-sequence recording for DAG-order verification).
        CompletableFuture<TaskStepReturn> steppedFuture = dispatched.handle((outcome, ex) -> {
            if (ex != null) {
                recorder.markFailed(taskId);
                throw ex instanceof CompletionException
                        ? (CompletionException) ex
                        : new CompletionException(ex);
            }
            if (outcome.isCompleted()) {
                recorder.markComplete(taskId);
                return TaskStepReturn.RETURN_RESULT("completed:" + taskId);
            }
            recorder.markFailed(taskId);
            throw new CompletionException(outcome.getCause());
        });

        return TaskStepReturn.ASYNC_RETURN(steppedFuture);
    }
}
