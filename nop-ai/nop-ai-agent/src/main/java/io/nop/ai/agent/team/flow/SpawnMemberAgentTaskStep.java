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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A nop-task {@link io.nop.task.ITaskStep} that represents a single team-task
 * graph node whose team has <b>no already-bound member</b>: at <b>node run
 * time</b> (when the nop-task DAG scheduler triggers the node — not at graph
 * build time) it consults the injected {@link IMemberSpawner} to spawn a
 * member agent for the task, drives the {@link TeamTaskStatus} state machine
 * through {@link ITeamTaskStore} (claim → complete), and records its
 * execution sequence into the shared {@link ExecutionRecorder} for
 * dependency-order verification (plan 238 / L4-orchestrator-auto-spawn-integration).
 *
 * <p><b>Async execution model (plan 243 / L4-spawn-step-async, successor of
 * plan 241 design 裁定 3a)</b>: the step performs the CREATED→CLAIMED
 * transition <b>synchronously</b> at the moment the nop-task DAG scheduler
 * triggers the node (after its {@code blockedBy} predecessors have
 * completed), then offloads the {@code spawnMember} call + three-state
 * interpretation + {@code completeTask} to a dedicated {@link Executor} via
 * {@link CompletableFuture#supplyAsync}. The result is wrapped into an
 * <b>async</b> {@link TaskStepReturn} (via {@link TaskStepReturn#ASYNC_RETURN}),
 * mirroring {@link MemberAgentTaskStep}'s async contract. The spawn node does
 * NOT block the DAG scheduler's thread, so independent diamond branches (no
 * {@code blockedBy} edge between them) that are <b>both</b> spawn nodes truly
 * run in parallel — closing the concurrency gap left open by plan 241 (which
 * only async-converted the bound-member step because {@code IAgentEngine.execute}
 * already returns a {@link CompletableFuture}, whereas
 * {@link IMemberSpawner#spawnMember} is a synchronous contract).
 *
 * <p><b>Why a separate step class?</b> The orchestrator's
 * {@link TeamTaskFlowOrchestrator} resolves a bound member at <b>graph build
 * time</b> (before the graph runs). If the spawner's execution-style
 * {@link IMemberSpawner#spawnMember} were called inside that build-time
 * resolution, a task would be executed before its {@code blockedBy}
 * predecessors had completed — breaking DAG dependency order (a task
 * blockedBy another would run before its predecessor finished). Therefore the
 * member resolution for an unbound node only <b>selects</b> this step at build
 * time; the actual spawn execution is deferred to this step's
 * {@link #execute}, which the DAG scheduler invokes only once the node's
 * {@code waitSteps} predecessors have all completed successfully. This is the
 * critical difference from the daemon path (where the spawner runs inline
 * inside the daemon's own dispatch loop): the orchestrator delegates
 * execution to the graph node step, so spawn must happen at node run time.
 *
 * <p><b>State-machine model (mirrors {@link MemberAgentTaskStep}, NOT the
 * daemon's abandon model)</b>: this step claims the task
 * (CREATED → CLAIMED) with the orchestrator session id, spawns the member
 * agent, and on success completes it (CLAIMED → COMPLETED). On failure it
 * <b>throws</b> (leaving the task in CLAIMED — it does NOT abandon), which
 * propagates through nop-task's {@link io.nop.task.step.GraphTaskStep}
 * scheduler, short-circuiting the graph and cancelling successor nodes
 * (dependency-ordered failure propagation). This is deliberately consistent
 * with {@link MemberAgentTaskStep}'s failure model (a failed bound-member
 * node also throws and leaves its task in CLAIMED — see
 * {@code nop-ai-agent-task-flow-integration.md} §4): within a single
 * orchestrator run, bound-member and spawned-member nodes share the same
 * post-failure task state. The daemon's abandon (CLAIMED → ABANDONED) is the
 * unattended guardian's reclamation model and does not apply to this
 * one-shot programmatic DAG orchestrator.
 *
 * <p><b>Spawn result interpretation</b> (mirrors the daemon's spawn branch +
 * the shared {@code complete/abandon} semantics, decision 3 of plan 238 — all
 * executed inside the supplyAsync worker, preserving honest-failure semantics
 * line-for-line in the async path):
 * <ul>
 *   <li>{@link SpawnMemberResult.Status#DISPATCHED} — the spawner executed a
 *       member agent. The step inspects the wrapped
 *       {@link AgentExecutionResult#getStatus()}: {@link AgentExecStatus#completed}
 *       → complete the task; anything else → throw (honest failure, leave
 *       CLAIMED).</li>
 *   <li>{@link SpawnMemberResult.Status#NO_SPAWN} — the spawner honestly
 *       declined (no declarative memberSpec, or the NoOp shipped default).
 *       The step throws — never silently skips the node (Minimum Rules #24).
 *       Under the NoOp shipped default this yields
 *       {@code TeamTaskFlowResult{success=false}} (an honest failure), which
 *       is the documented intentional API change from the pre-238
 *       {@code resolveMember} fast-fail throw.</li>
 *   <li>{@link SpawnMemberResult.Status#SPAWN_FAILED} — the spawner attempted
 *       to spawn but the execution threw. The step throws (honest failure,
 *       leave CLAIMED).</li>
 *   <li>The spawner itself throwing (a contract violation) — the step throws,
 *       wrapping the cause (honest failure, leave CLAIMED).</li>
 * </ul>
 *
 * <p><b>Executor isolation (plan 243 design 裁定 3)</b>: the supplied
 * {@code spawnExecutor} MUST be independent of the {@code commonPool}
 * ({@code ForkJoinPool.commonPool()}) that {@code DefaultAgentEngine} uses for
 * its own one-arg {@code supplyAsync}. The spawn worker synchronously joins
 * the engine future (via {@code DefaultMemberSpawner.spawnMember} →
 * {@code engine.execute(req).join()}); if both ran on the commonPool,
 * concurrent spawn nodes ≥ commonPool parallelism would park every commonPool
 * thread on {@code .join()} with no thread left to advance the engine
 * futures — a nested-blocking stall. The orchestrator therefore supplies a
 * dedicated bounded executor (its pool size is the spawn concurrency cap);
 * the step fails fast ({@link NopAiAgentException}) if no executor is wired.
 *
 * <p><b>Tenant-context propagation (plan 243 design 裁定 2, explicit-
 * propagation mechanism)</b>: standard {@link ThreadLocal} does not cross the
 * {@code supplyAsync} boundary, so the orchestrator captures the caller's
 * tenant ({@link ThreadLocalTenantResolver#current()}) once at
 * {@code executeAsync} entry (on the caller's thread, where it is reliably
 * set) and injects it into this step. The step re-applies it inside the
 * supplyAsync worker lambda ({@link ThreadLocalTenantResolver#set}) before
 * any {@code completeTask} (which reads the thread-local tenant for DB
 * stores) and clears it in a {@code finally} so pooled worker threads never
 * leak tenant context. This explicit-propagation mechanism is robust to any
 * DAG topology (both enter and non-enter spawn nodes), reusing the standard
 * paradigm established by {@code DefaultAgentEngine.doExecute} (plan 232).
 *
 * <p><b>No Silent No-Op</b> (Minimum Rules #24), preserved verbatim in the
 * async path: claim CAS loss (task missing / already claimed by another)
 * throws synchronously at node-trigger time — it never silently skips the
 * node. A non-completed spawned execution throws — failure is reported, not
 * swallowed. A {@code completeTask} CAS loss throws. None of these are
 * silently degraded to success in the async path.
 *
 * <p>See plan 238 (L4-orchestrator-auto-spawn-integration) for the original
 * sync implementation; plan 243 (L4-spawn-step-async) for the async
 * conversion.
 */
public class SpawnMemberAgentTaskStep extends AbstractTaskStep {

    private final TeamTask task;
    private final Team team;
    private final String orchestratorSessionId;
    private final IMemberSpawner memberSpawner;
    private final ITeamTaskStore taskStore;
    private final ExecutionRecorder recorder;
    private final Executor spawnExecutor;
    private final String capturedTenant;

    /**
     * @param spawnExecutor     a dedicated executor (independent of the
     *                          {@code commonPool}) used to offload the
     *                          synchronous {@code spawnMember} + complete
     *                          work off the DAG scheduler thread (plan 243
     *                          design 裁定 3). Must not be {@code null}.
     * @param capturedTenant    the caller's tenant captured by the
     *                          orchestrator at {@code executeAsync} entry,
     *                          re-applied inside the supplyAsync worker
     *                          (plan 243 design 裁定 2). May be {@code null}
     *                          (= no tenant context, all data visible).
     */
    public SpawnMemberAgentTaskStep(TeamTask task, Team team, String orchestratorSessionId,
                                     IMemberSpawner memberSpawner, ITeamTaskStore taskStore,
                                     ExecutionRecorder recorder, Executor spawnExecutor,
                                     String capturedTenant) {
        this.task = task;
        this.team = team;
        this.orchestratorSessionId = orchestratorSessionId;
        this.memberSpawner = memberSpawner;
        this.taskStore = taskStore;
        this.recorder = recorder;
        this.spawnExecutor = Objects.requireNonNull(spawnExecutor,
                "spawnExecutor must not be null — a dedicated executor independent of the commonPool "
                        + "is required (plan 243 design 裁定 3: commonPool nesting would stall/deadlock)");
        this.capturedTenant = capturedTenant;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        String taskId = task.getTaskId();
        recorder.markStart(taskId);

        // 1. Claim the task SYNCHRONOUSLY at node-trigger time
        //    (CREATED -> CLAIMED) with the orchestrator session id (mirrors
        //    MemberAgentTaskStep). The DAG scheduler invokes this step only
        //    after its blockedBy predecessors completed, so claiming here
        //    preserves DAG dependency order. A task already COMPLETED by a
        //    prior partial run is treated as already-done (idempotent re-run,
        //    synchronous success return — not a silent skip). A claim CAS
        //    loss is an honest fast-fail throw (No Silent No-Op #24).
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

        // 2. Offload spawnMember + three-state interpretation + completeTask
        //    to the dedicated executor (plan 243 design 裁定 1/3). IMemberSpawner
        //    .spawnMember() is a synchronous contract (DefaultMemberSpawner
        //    does engine.execute(request).join()), so supplyAsync is the
        //    standard means of converting it to an async TaskStepReturn
        //    WITHOUT altering the IMemberSpawner contract (mechanism b —
        //    rejected (a) changing the contract and (c) bypassing the spawner).
        //    Re-apply the caller's tenant inside the worker lambda and clear
        //    it in finally so the pooled worker never leaks tenant context
        //    (design 裁定 2). The DAG scheduler's thread is released as soon
        //    as this method returns the async TaskStepReturn, so independent
        //    spawn branches truly run in parallel.
        CompletableFuture<TaskStepReturn> steppedFuture = CompletableFuture.supplyAsync(() -> {
            ThreadLocalTenantResolver.set(capturedTenant);
            try {
                return spawnAndComplete(taskId, claimed.get().getClaimEpoch());
            } finally {
                ThreadLocalTenantResolver.clear();
            }
        }, spawnExecutor);

        return TaskStepReturn.ASYNC_RETURN(steppedFuture);
    }

    /**
     * Run inside the supplyAsync worker: invoke the spawner, interpret the
     * three-state result, and complete the task. Honest-failure semantics are
     * preserved line-for-line with the pre-243 sync path — each failure path
     * records {@code markFailed} and throws {@link NopAiAgentException},
     * leaving the task in CLAIMED (not abandoned). The thrown exception
     * propagates through the {@link CompletableFuture} chain to the
     * orchestrator's {@code exceptionally} handler, which converts it into an
     * honest {@code TeamTaskFlowResult{success=false}}.
     */
    private TaskStepReturn spawnAndComplete(String taskId, Long claimEpoch) {
        // spawnMember executes the member agent synchronously
        // (DefaultMemberSpawner does execute(request).join()) and returns a
        // three-state result. This call happens here — inside the supplyAsync
        // worker — so it only runs once the DAG scheduler has triggered this
        // node after its blockedBy predecessors completed (plan 238 decision
        // 1), and it no longer blocks the DAG scheduler thread (plan 243).
        SpawnMemberRequest spawnReq = new SpawnMemberRequest(team, task, orchestratorSessionId);
        SpawnMemberResult spawnResult;
        try {
            spawnResult = memberSpawner.spawnMember(spawnReq);
        } catch (RuntimeException e) {
            // A spawner that throws (instead of returning SPAWN_FAILED) is a
            // contract violation, but we still handle it honestly: the task
            // stays CLAIMED and the node throws (decision 3 — throw + leave
            // CLAIMED, NOT the daemon's abandon).
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.spawn-failed: memberSpawner threw for taskId=" + taskId
                            + ", teamId=" + task.getTeamId(), e);
        }
        if (spawnResult == null) {
            // Defensive: a well-behaved spawner never returns null (Minimum
            // Rules #24), but treat a null as honest failure rather than NPE.
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.spawn-failed: memberSpawner returned null for taskId=" + taskId
                            + ", teamId=" + task.getTeamId());
        }

        // Interpret the three-state result (mirror the daemon's spawn
        // branch, but with the orchestrator's throw+leave-CLAIMED model).
        AgentExecutionResult executionResult;
        switch (spawnResult.getStatus()) {
            case NO_SPAWN:
                // Honest failure: the spawner honestly declined (no memberSpec
                // / NoOp shipped default). Never silently skip the node.
                recorder.markFailed(taskId);
                throw new NopAiAgentException(
                        "nop.ai.team.flow.no-spawn: spawner declined to spawn for taskId=" + taskId
                                + ", teamId=" + task.getTeamId()
                                + ", reason=" + spawnResult.getReason());
            case SPAWN_FAILED:
                recorder.markFailed(taskId);
                throw new NopAiAgentException(
                        "nop.ai.team.flow.spawn-failed: spawn execution failed for taskId=" + taskId
                                + ", teamId=" + task.getTeamId()
                                + ", reason=" + spawnResult.getReason());
            case DISPATCHED:
                // Spawner executed the agent; inspect the wrapped result to
                // decide complete vs. fail (shared semantics with the daemon's
                // completeOrAbandonAfterExecution, decision 3).
                executionResult = spawnResult.getExecutionResult();
                break;
            default:
                throw new IllegalStateException(
                        "unhandled spawn result status: " + spawnResult.getStatus());
        }

        if (executionResult == null || executionResult.getStatus() != AgentExecStatus.completed) {
            // Honest failure: a non-completed terminal status (or a
            // defensively-null wrapped result) is a real failure.
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.spawn-not-completed: spawned agent did not complete for taskId="
                            + taskId + ", teamId=" + task.getTeamId() + ", status="
                            + (executionResult != null ? executionResult.getStatus() : "null"));
        }

        // Complete the task (CLAIMED -> COMPLETED) with the orchestrator
        // session id. Bind the claim epoch captured at claim time
        // (plan 279 / AR-01).
        Optional<TeamTask> completed = taskStore.completeTask(taskId, orchestratorSessionId, claimEpoch);
        if (completed.isEmpty()) {
            recorder.markFailed(taskId);
            throw new NopAiAgentException(
                    "nop.ai.team.flow.complete-failed: cannot complete team task taskId=" + taskId
                            + " (not in CLAIMED status — possible concurrent transition)");
        }

        recorder.markComplete(taskId);

        String reply = executionResult.getFinalMessage() != null
                ? executionResult.getFinalMessage()
                : ("completed:" + taskId);
        return TaskStepReturn.RETURN_RESULT(reply);
    }
}
