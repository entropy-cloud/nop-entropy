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
import java.util.concurrent.CompletionException;

/**
 * A nop-task {@link io.nop.task.ITaskStep} that represents a single team-task
 * graph node: it delegates the node's work to a bound member agent via
 * {@link IAgentEngine#execute(AgentMessageRequest)} (synchronous join — design
 * 裁定 3), drives the {@link TeamTaskStatus} state machine through
 * {@link ITeamTaskStore} (claim → complete), and records its execution
 * sequence into the shared {@link ExecutionRecorder} for dependency-order
 * verification.
 *
 * <p><b>Node → member resolution</b> (design 裁定 4): the step does NOT spawn
 * a member. The member session (and optional agent model) is resolved by the
 * orchestrator from the team's already-bound roster before the graph runs and
 * is injected at construction. The step consumes that binding.
 *
 * <p><b>No Silent No-Op</b> (Minimum Rules #24):
 * <ul>
 *   <li>If {@code claimTask} loses (task missing / already claimed by another),
 *       the step throws — it never silently skips the node.</li>
 *   <li>If the member-agent execution completes with a non-{@link AgentExecStatus#completed}
 *       status (failed / cancelled / forced_stopped / escalated / paused), the
 *       step throws — failure is reported, not swallowed.</li>
 *   <li>On success the task is transitioned CLAIMED → COMPLETED; if
 *       {@code completeTask} loses the CAS the step throws.</li>
 * </ul>
 *
 * <p>The thrown exception propagates through nop-task's {@link io.nop.task.step.GraphTaskStep}
 * scheduler, which short-circuits the graph and cancels successor nodes
 * (dependency-ordered failure propagation).
 *
 * <p>See plan 233 (L4-nop-task-dag-integration) Phase 2.
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

        // 1. Claim the task (CREATED -> CLAIMED). A task already COMPLETED by a
        //    prior partial run is treated as already-done (idempotent re-run).
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

        // 2. Delegate to the bound member agent. Synchronous join per裁定3:
        //    the orchestrator blocks until the member agent finishes so the
        //    nop-task graph scheduler's dependency ordering is observable.
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("teamTaskId", taskId);
        metadata.put("teamId", task.getTeamId());
        AgentMessageRequest request = new AgentMessageRequest(agentName, buildPrompt(task),
                memberSessionId, metadata);
        AgentExecutionResult result;
        try {
            result = agentEngine.execute(request).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.member-agent-execution-error: member agent threw for taskId="
                            + taskId + ", sessionId=" + memberSessionId, cause);
        }

        // 3. Honest failure: a non-completed terminal status is a real failure.
        if (result.getStatus() != AgentExecStatus.completed) {
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.member-agent-not-completed: member agent did not complete for taskId="
                            + taskId + ", sessionId=" + memberSessionId
                            + ", status=" + result.getStatus()
                            + (result.getError() != null ? ", error=" + result.getError() : ""));
        }

        // 4. Complete the task (CLAIMED -> COMPLETED).
        Optional<TeamTask> completed = taskStore.completeTask(taskId, memberSessionId);
        if (completed.isEmpty()) {
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.complete-failed: cannot complete team task taskId=" + taskId
                            + " (not in CLAIMED status — possible concurrent transition)");
        }

        recorder.markComplete(taskId);

        String reply = result.getFinalMessage() != null ? result.getFinalMessage() : ("completed:" + taskId);
        return TaskStepReturn.RETURN_RESULT(reply);
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
