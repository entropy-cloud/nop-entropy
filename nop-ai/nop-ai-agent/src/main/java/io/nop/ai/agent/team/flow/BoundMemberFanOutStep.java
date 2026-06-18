package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.team.ITeamTaskStore;
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

/**
 * A nop-task {@link io.nop.task.ITaskStep} that fans a single team-task graph
 * node out to <b>N already-bound member agents</b> concurrently and reduces
 * their results (plan 244 / L4-multi-member-per-task-routing, design 裁定 7).
 *
 * <p>This is the bound-half multi-member fan-out variant of
 * {@link MemberAgentTaskStep} (which delegates to a single bound member).
 * It exists only for dispatch plans whose N targets are <b>all bound</b>;
 * plans with spawn targets use the spawn fan-out step (Phase 2), and plans
 * mixing bound and spawn targets use a unified fan-out step. Single-member
 * bound plans use {@link MemberAgentTaskStep} directly (zero-regression
 * short-circuit in the orchestrator's build loop).
 *
 * <h2>Execution model (mirrors MemberAgentTaskStep, generalized to N)</h2>
 * <ol>
 *   <li><b>Claim synchronously</b> at node-trigger time (CREATED → CLAIMED)
 *       with the orchestrator session id — single claim for the whole node,
 *       preserving DAG dependency order (claim CAS loss fast-fails
 *       synchronously; an already-COMPLETED task is an idempotent
 *       synchronous success).</li>
 *   <li><b>Fan out</b>: for each bound dispatch target, construct an
 *       {@link AgentMessageRequest} and submit it to
 *       {@link IAgentEngine#execute} — each call returns its own
 *       {@link CompletableFuture} (the engine is already async), so the N
 *       submissions run concurrently without any {@code supplyAsync} offload
 *       (design 裁定 7).</li>
 *   <li><b>Reduce</b>: each engine future is mapped to a
 *       {@link MemberExecOutcome} (COMPLETED / ENGINE_FAILED / NOT_COMPLETED);
 *       the plan's {@link IReductionStrategy} decides node success vs.
 *       failure. The shipped {@link AllMustSucceedReduction} requires every
 *       member to reach {@link AgentExecStatus#completed}.</li>
 *   <li><b>Complete once</b>: on reduction success the orchestrator performs
 *       a <b>single</b> {@code completeTask} (CLAIMED → COMPLETED) — the
 *       task transition is per-node, not per-member. On reduction failure
 *       the future completes exceptionally and the task stays CLAIMED
 *       (No Silent No-Op #24).</li>
 * </ol>
 *
 * <h2>Fast-fail, no in-flight cancellation (design 裁定 5)</h2>
 * <p>The first member failure fast-fails the node's reduced future. The
 * other in-flight engine futures are <b>not cancelled</b> — they run to
 * completion and their results are discarded. Cancellation is a Non-Goal
 * successor (requires {@code IAgentEngine.cancelSession}).
 *
 * <h2>Async return</h2>
 * <p>The step returns an async {@link TaskStepReturn} (via
 * {@link TaskStepReturn#ASYNC_RETURN}), the same contract as
 * {@link MemberAgentTaskStep} and {@link SpawnMemberAgentTaskStep}. The
 * nop-task {@link io.nop.task.step.GraphTaskStep} scheduler therefore
 * triggers the node, releases its thread on this async return, and the
 * fan-out's N engine futures drive the node's completion independently —
 * independent fan-out branches in the DAG truly run in parallel.
 *
 * <p>See plan 244 (L4-multi-member-per-task-routing), design 裁定 4 / 5 / 7.
 */
public class BoundMemberFanOutStep extends AbstractTaskStep {

    private final TeamTask task;
    private final List<DispatchTarget> boundTargets;
    private final String orchestratorSessionId;
    private final IAgentEngine agentEngine;
    private final ITeamTaskStore taskStore;
    private final ExecutionRecorder recorder;
    private final IReductionStrategy reductionStrategy;
    private final String capturedTenant;

    /**
     * @param boundTargets          the bound dispatch targets to fan out to
     *                              (non-null, non-empty, all
     *                              {@link DispatchTarget.Kind#BOUND}). The
     *                              orchestrator pre-checks the empty / mixed
     *                              case before constructing this step.
     * @param orchestratorSessionId the orchestrator session id used for the
     *                              single claim + complete (non-null)
     * @param capturedTenant        the caller's tenant captured by the
     *                              orchestrator at {@code executeAsync} entry,
     *                              re-applied around the single
     *                              {@code completeTask} call so DB stores
     *                              filter by the caller's tenant (plan 243
     *                              裁定 2). May be null.
     * @param reductionStrategy     the reduction strategy for the N member
     *                              futures (non-null)
     */
    public BoundMemberFanOutStep(TeamTask task, List<DispatchTarget> boundTargets,
                                 String orchestratorSessionId,
                                 IAgentEngine agentEngine, ITeamTaskStore taskStore,
                                 ExecutionRecorder recorder, String capturedTenant,
                                 IReductionStrategy reductionStrategy) {
        this.task = task;
        this.boundTargets = new ArrayList<>(boundTargets);
        this.orchestratorSessionId = orchestratorSessionId;
        this.agentEngine = agentEngine;
        this.taskStore = taskStore;
        this.recorder = recorder;
        this.capturedTenant = capturedTenant;
        this.reductionStrategy = reductionStrategy;
        if (this.boundTargets.isEmpty()) {
            throw new IllegalArgumentException(
                    "BoundMemberFanOutStep requires at least one bound target — the orchestrator must pre-check the empty plan as an honest failure");
        }
        for (DispatchTarget t : this.boundTargets) {
            if (!t.isBound()) {
                throw new IllegalArgumentException(
                        "BoundMemberFanOutStep accepts only BOUND targets; got " + t.getKind()
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

        // 2. Fan out + reduce + complete via the shared dispatcher (plan 245
        //    design 裁定 1: the dispatcher is the single canonical fan-out +
        //    reduce + complete chain, consumed by both the orchestrator step
        //    variants and the daemon — no dual code path). The dispatcher
        //    builds one engine.execute future per bound target, reduces under
        //    the plan's strategy, and performs the single completeTask CAS on
        //    success under the caller's tenant. Bound-only: no team / no spawn
        //    executor is needed.
        CompletableFuture<MemberDispatchOutcome> dispatched = MemberFanOutDispatcher.dispatch(
                task, /*team=*/null, boundTargets, reductionStrategy,
                agentEngine, io.nop.ai.agent.team.NoOpMemberSpawner.noOp(),
                taskStore, orchestratorSessionId, /*spawnExecutor=*/null, capturedTenant);

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
