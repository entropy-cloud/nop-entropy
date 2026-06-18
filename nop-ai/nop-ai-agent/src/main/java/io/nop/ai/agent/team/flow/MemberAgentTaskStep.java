package io.nop.ai.agent.team.flow;

import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.team.ITeamTaskStore;
import io.nop.ai.agent.team.TeamTask;
import io.nop.ai.agent.team.TeamTaskStatus;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.AbstractTaskStep;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * A nop-task {@link io.nop.task.ITaskStep} that represents a single team-task
 * graph node: it delegates the node's work to a bound member agent via
 * {@link IAgentEngine#execute(AgentMessageRequest)} <b>asynchronously</b>
 * (plan 241 / async team-task orchestration), drives the
 * {@link TeamTaskStatus} state machine through {@link ITeamTaskStore}
 * (claim synchronously at node-trigger time → complete / fail in the async
 * callback), and records its execution sequence into the shared
 * {@link ExecutionRecorder} for dependency-order verification.
 *
 * <p><b>Async execution model (plan 241, consuming nop-task's existing async
 * contract — design 裁定 1 / 3)</b>: the step performs the
 * CREATED→CLAIMED transition <b>synchronously</b> at the moment the nop-task
 * DAG scheduler triggers the node (after its {@code blockedBy} predecessors
 * have completed), then wraps the engine's existing
 * {@link CompletableFuture} into an <b>async</b> {@link TaskStepReturn}
 * (via {@link TaskStepReturn#ASYNC_RETURN}). The member-agent execution,
 * the CLAIMED→COMPLETED transition, and the failure paths all happen in
 * the async callback — the node does NOT block the DAG scheduler's thread
 * on {@code .join()}. Independent diamond branches (no {@code blockedBy}
 * dependency between them) therefore truly run in parallel: nop-task's
 * {@link io.nop.task.step.GraphTaskStep} triggers ready nodes through
 * {@code CompletableFuture} scheduling and each node's async return does
 * not tie up the scheduler thread.
 *
 * <p><b>Node → member resolution</b> (design 裁定 4): the step does NOT
 * spawn a member. The member session (and optional agent model) is resolved
 * by the orchestrator from the team's already-bound roster before the graph
 * runs and is injected at construction. The step consumes that binding.
 *
 * <p><b>No Silent No-Op</b> (Minimum Rules #24), preserved verbatim in the
 * async path:
 * <ul>
 *   <li>If {@code claimTask} loses (task missing / already claimed by
 *       another), the step throws synchronously at node-trigger time — it
 *       never silently skips the node. (claim happens before the async
 *       future is created, so a synchronous throw is the honest fast-fail
 *       path, identical to the pre-241 sync behaviour.)</li>
 *   <li>If the member-agent future completes exceptionally, the step's
 *       async future completes exceptionally with a
 *       {@link NopAiAgentException} wrapping the cause — failure is
 *       reported, not swallowed.</li>
 *   <li>If the member-agent execution completes with a
 *       non-{@link AgentExecStatus#completed} status (failed / cancelled /
 *       forced_stopped / escalated / paused), the async future completes
 *       exceptionally — failure is reported, not swallowed. The task is
 *       left in CLAIMED (it is NOT auto-abandoned — consistent with the
 *       daemon's abandon being the unattended-recovery model, not the
 *       one-shot orchestrator's).</li>
 *   <li>On success the task is transitioned CLAIMED → COMPLETED in the
 *       async callback; if {@code completeTask} loses the CAS the async
 *       future completes exceptionally.</li>
 * </ul>
 *
 * <p>The propagated exception flows through nop-task's
 * {@link io.nop.task.step.GraphTaskStep} scheduler's CompletableFuture,
 * which short-circuits the graph and cancels successor nodes
 * (dependency-ordered failure propagation). The orchestrator's
 * {@code executeAsync} catches this via {@code exceptionally} and reports
 * an honest {@code TeamTaskFlowResult{success=false}}.
 *
 * <p><b>Idempotent re-run</b>: a task already COMPLETED by a prior partial
 * run is treated as already-done — the step records completion and returns
 * a synchronous success {@link TaskStepReturn} (no engine invocation).
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 2 for the original
 * sync implementation; plan 241 (L4-async-cross-process-orchestration,
 * async half) for the async conversion.
 */
public class MemberAgentTaskStep extends AbstractTaskStep {

    private final TeamTask task;
    private final String memberSessionId;
    private final String agentName;
    private final IAgentEngine agentEngine;
    private final ITeamTaskStore taskStore;
    private final ExecutionRecorder recorder;

    public MemberAgentTaskStep(TeamTask task, String memberSessionId, String agentName,
                               IAgentEngine agentEngine, ITeamTaskStore taskStore,
                               ExecutionRecorder recorder) {
        this.task = task;
        this.memberSessionId = memberSessionId;
        this.agentName = agentName;
        this.agentEngine = agentEngine;
        this.taskStore = taskStore;
        this.recorder = recorder;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String taskId = task.getTaskId();
        recorder.markStart(taskId);

        // 1. Claim the task SYNCHRONOUSLY at node-trigger time
        //    (CREATED -> CLAIMED). The DAG scheduler invokes this step only
        //    after its blockedBy predecessors completed, so claiming here
        //    preserves DAG dependency order (claim at the correct DAG
        //    point, not at graph-build time). A task already COMPLETED by a
        //    prior partial run is treated as already-done (idempotent
        //    re-run). A claim CAS loss is an honest fast-fail throw (No
        //    Silent No-Op #24).
        Optional<TeamTask> claimed = taskStore.claimTask(taskId, memberSessionId);
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

        // 2. Delegate to the bound member agent ASYNCHRONOUSLY (plan 241).
        //    IAgentEngine.execute() already returns a CompletableFuture;
        //    wrap it into an async TaskStepReturn instead of .join()-blocking
        //    the scheduler thread. Independent diamond branches (no blockedBy
        //    edge between them) therefore truly run in parallel — each
        //    node's async return releases the DAG scheduler's thread to
        //    trigger the next ready node.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("teamTaskId", taskId);
        metadata.put("teamId", task.getTeamId());
        AgentMessageRequest request = new AgentMessageRequest(agentName, buildPrompt(task),
                memberSessionId, metadata);

        CompletableFuture<AgentExecutionResult> engineFuture = agentEngine.execute(request);

        // 3. Wrap the engine future into an async TaskStepReturn. The
        //    CLAIMED→COMPLETED transition, the non-completed status check,
        //    and the failure-recording all happen in the async callback
        //    (preserving the pre-241 honest-failure semantics line-for-
        //    line, only the threading model changes from .join() to async
        //    composition). Use an explicit result future so exceptions
        //    surface as the cause (no layered CompletionException).
        CompletableFuture<TaskStepReturn> steppedFuture = new CompletableFuture<>();
        engineFuture.whenComplete((result, ex) -> {
            try {
                if (ex != null) {
                    // Engine future completed exceptionally (e.g.
                    // CompletionException wrapping the engine's own
                    // RuntimeException). Honest failure: record + propagate.
                    Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                            ? ex.getCause() : ex;
                    recorder.markFailed(taskId);
                    steppedFuture.completeExceptionally(new NopAiAgentException(
                            "nop.ai.team.flow.member-agent-execution-error: member agent threw for taskId="
                                    + taskId + ", sessionId=" + memberSessionId, cause));
                    return;
                }

                // Honest failure: a non-completed terminal status is a real
                // failure. The task stays CLAIMED (not auto-abandoned) —
                // consistent with the daemon's abandon being the unattended-
                // recovery model, not the orchestrator's.
                if (result.getStatus() != AgentExecStatus.completed) {
                    recorder.markFailed(taskId);
                    steppedFuture.completeExceptionally(new NopAiAgentException(
                            "nop.ai.team.flow.member-agent-not-completed: member agent did not complete for taskId="
                                    + taskId + ", sessionId=" + memberSessionId
                                    + ", status=" + result.getStatus()
                                    + (result.getError() != null ? ", error=" + result.getError() : "")));
                    return;
                }

                // 4. Complete the task (CLAIMED -> COMPLETED) in the async
                //    callback. Honest failure on CAS loss.
                Optional<TeamTask> completed = taskStore.completeTask(taskId, memberSessionId);
                if (completed.isEmpty()) {
                    recorder.markFailed(taskId);
                    steppedFuture.completeExceptionally(new NopAiAgentException(
                            "nop.ai.team.flow.complete-failed: cannot complete team task taskId=" + taskId
                                    + " (not in CLAIMED status — possible concurrent transition)"));
                    return;
                }

                recorder.markComplete(taskId);

                String reply = result.getFinalMessage() != null
                        ? result.getFinalMessage()
                        : ("completed:" + taskId);
                steppedFuture.complete(TaskStepReturn.RETURN_RESULT(reply));
            } catch (Throwable t) {
                // Defensive: any unexpected throwable inside the callback
                // (e.g. completeTask throwing) is honestly propagated.
                steppedFuture.completeExceptionally(t);
            }
        });

        return TaskStepReturn.ASYNC_RETURN(steppedFuture);
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
