package io.nop.ai.agent.team.scheduler;

/**
 * Team-task auto-scheduling daemon — extends nop-ai-agent team-task
 * orchestration from "manual / programmatic invocation of
 * {@code TeamTaskFlowOrchestrator.execute(teamId)}" to a continuously running
 * periodic sweep that <b>automatically</b> claims and dispatches
 * dependency-ready team tasks. This interface is the opt-in extension point;
 * the shipped default is {@link NoOpTeamTaskSchedulerDaemon} (zero behaviour
 * regression), and the functional implementation is
 * {@link TeamTaskSchedulerDaemon} (plan 236 / L4-blockedBy-resolution-engine).
 *
 * <p><b>Scope (plan 236)</b>: each {@link #scanOnce} performs:
 * <ol>
 *   <li>For each target team (configured team-id set, or all active teams when
 *       none configured): load the team's tasks, build a
 *       {@link io.nop.ai.agent.team.flow.TeamTaskTopology}, and query
 *       {@link io.nop.ai.agent.team.flow.TeamTaskTopology#getReadyTasks()}.</li>
 *   <li>Filter the ready set to tasks whose status is
 *       {@link io.nop.ai.agent.team.TeamTaskStatus#CREATED}. Tasks returned
 *       by the topology in {@link io.nop.ai.agent.team.TeamTaskStatus#CLAIMED}
 *       (another member is executing them) are <b>skipped</b> — never claimed,
 *       never abandoned (兑现 Non-Goal「不强占 CLAIMED 任务」).</li>
 *   <li>For each CREATED ready task: CAS-claim it via
 *       {@link io.nop.ai.agent.team.ITeamTaskStore#claimTask} (idempotent —
 *       a CAS loss returns empty Optional and the task is silently skipped,
 *       a legitimate concurrency outcome, not an error). On a successful
 *       claim, resolve the bound member via
 *       {@link io.nop.ai.agent.team.ITeamManager} and dispatch via
 *       {@link io.nop.ai.agent.engine.IAgentEngine#execute} (synchronous
 *       join, identical delegation mechanism to plan 233
 *       {@code MemberAgentTaskStep}). Success transitions the task
 *       CLAIMED → COMPLETED via
 *       {@link io.nop.ai.agent.team.ITeamTaskStore#completeTask}; a dispatch
 *       failure (member agent threw / returned non-completed terminal status /
 *       completeTask CAS lost) transitions it CLAIMED → ABANDONED via
 *       {@link io.nop.ai.agent.team.ITeamTaskStore#abandonTask} — <b>only</b>
 *       for tasks this daemon CAS-claimed, never another member's task.</li>
 * </ol>
 *
 * <p><b>Dependency order is auto-guaranteed</b> by the ready query: a task
 * whose {@code blockedBy} are not all COMPLETED is never in the ready set, so
 * it is never dispatched. When its dependencies complete in a prior scan, the
 * next scan's ready query includes it automatically. No runtime blocking,
 * no deadlock surface (design 裁定 2).
 *
 * <p>The daemon <b>does not</b> call
 * {@link io.nop.ai.agent.team.flow.TeamTaskFlowOrchestrator#execute(String)}.
 * That entry point rebuilds the whole-team graph on every invocation and
 * short-circuits when any node's CAS claim loses — unsuitable for periodic
 * incremental advancement (design 裁定 1).
 *
 * <h2>Lifecycle semantics (start / stop)</h2>
 * {@link #start} registers a fixed-delay periodic task on the configured
 * {@code IScheduledExecutor} (default cadence); {@link #stop} cancels it.
 * Both are <b>idempotent</b>. {@code stop} is graceful: in-progress
 * dispatched tasks (already CLAIMED and executing) are not interrupted, but
 * no new tasks are claimed after stop (design 裁定 3).
 *
 * <p><b>Deployment-layer lifecycle</b>: mirroring the
 * {@link io.nop.ai.agent.runtime.recovery.IRecoveryManager} contract, the
 * engine does <em>not</em> call {@code start()}/{@code stop()} on the daemon.
 * Integrators wire it (e.g. via
 * {@code DefaultAgentEngine.setTeamTaskSchedulerDaemon}) and call
 * {@code start()}/{@code stop()} from the deployment layer.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent calls to {@link #scanOnce} and
 * the lifecycle methods. {@link TeamTaskSchedulerDaemon} achieves this via a
 * {@code volatile} {@code Future} handle guarded by {@code synchronized}
 * start/stop, and per-task try/catch isolation inside {@code scanOnce}.
 *
 * <p>See plan 236 (L4-blockedBy-resolution-engine).
 */
public interface ITeamTaskSchedulerDaemon {

    /**
     * Start the periodic scan (idempotent). After the first successful call,
     * a fixed-delay task is registered on the configured
     * {@code IScheduledExecutor}; subsequent calls before {@link #stop} are
     * no-ops.
     */
    void start();

    /**
     * Stop the periodic scan (idempotent). Cancels the registered task
     * (best-effort, {@code mayInterruptIfRunning=false} — graceful stop).
     * In-progress dispatched tasks are not interrupted, but no new tasks are
     * claimed after stop. Subsequent calls when no task is registered are
     * no-ops.
     */
    void stop();

    /**
     * Run a single scan synchronously and return the result. Exposed as a
     * public method so tests can trigger a scan without relying on scheduler
     * timing, and so integrators can trigger an on-demand scan. This is also
     * the unit of work scheduled by {@link #start}.
     *
     * <p>Under the shipped {@link NoOpTeamTaskSchedulerDaemon} default this
     * returns an all-zero {@link SchedulerScanResult} — an explicit "no
     * scheduling scanning" semantic, not a silent no-op (Minimum Rules #24).
     *
     * <p>Per-task failure isolation: a dispatch failure for one task (member
     * agent threw / non-completed terminal status / completeTask CAS lost)
     * is recorded in the returned result (the task is abandoned) and does
     * <b>not</b> abort the rest of the scan.
     *
     * @return a non-null snapshot of this scan's outcome
     */
    SchedulerScanResult scanOnce();
}
