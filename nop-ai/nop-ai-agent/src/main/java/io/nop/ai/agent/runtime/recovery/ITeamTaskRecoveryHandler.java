package io.nop.ai.agent.runtime.recovery;

import java.util.List;

/**
 * Strategy hook invoked by {@code ScheduledRecoveryManager.scanOnce} to
 * detect and recover stuck CLAIMED team tasks (plan 240 /
 * L4-team-task-reclaim-and-timeout-abandon).
 *
 * <p>This interface extends nop-ai-agent team-task lifecycle from
 * "CLAIMED is one-way — a task claimed by a member that then disappeared
 * (session orphaned / timeout / process crash) is stuck in CLAIMED forever,
 * blocking the task DAG" to "the recovery daemon's scanOnce invokes the
 * handler, which internally detects stuck tasks (CLAIMED + UPDATED_AT older
 * than the threshold) and acts per the configured strategy (RECLAIM reset
 * to re-claimable / ABORT terminal-fail / SKIP observe-only)".
 *
 * <p><b>Self-contained handler (design 裁定 3, plan 240)</b>: unlike
 * {@link IOrphanRecoveryHandler} and {@link ISessionTimeoutHandler} where
 * scanOnce performs the detection SELECT and the handler acts per-item, this
 * handler is <em>self-contained</em> — it performs both the detection
 * (SELECT {@code ai_agent_team_task} for stuck CLAIMED tasks) and the action
 * (raw JDBC UPDATE) internally. scanOnce calls
 * {@link #recoverStuckTasks()} once and aggregates the returned outcome list.
 * Rationale: team-task is a different domain table from session recovery;
 * encapsulating the team-task domain logic in the handler keeps the daemon
 * focused on the session domain. The handler is independently testable
 * (inject an H2 DataSource).
 *
 * <p>The shipped default is {@link NoOpTeamTaskRecoveryHandler} (SKIP —
 * returns an empty outcome list, zero DB access, zero behaviour regression);
 * the functional implementation is {@code DefaultTeamTaskRecoveryHandler}
 * (RECLAIM / ABORT per the configured {@link TeamTaskRecoveryAction}).
 *
 * <p><b>Wiring</b>: the handler is a sub-component of the recovery manager,
 * not an engine-layer configuration point (mirrors the plan 226 / 229
 * handler layering). Integrators wire it via
 * {@code ScheduledRecoveryManager.setTeamTaskRecoveryHandler}.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent invocation. The handler is
 * called from the daemon's scan thread (single scan at a time per manager
 * instance), but integrators may also call {@code scanOnce} directly from
 * other threads.
 *
 * <h2>Non-silent contract (Minimum Rules #24)</h2>
 * A handler must never silently drop a stuck task. A SKIP-mode handler
 * returns an empty outcome list <em>by design</em> (no detection performed
 * = no outcomes — this is the documented SKIP semantic, not a silent
 * drop); a RECLAIM/ABORT handler returns one outcome per detected stuck
 * task, with {@code succeeded=false} + descriptive message on CAS failure
 * or SQLException (never swallowed).
 *
 * <p>See plan 240 and design {@code nop-ai-agent-team-task-reclaim.md}.
 */
public interface ITeamTaskRecoveryHandler {

    /**
     * Detect and recover stuck CLAIMED team tasks in a single self-contained
     * invocation. The handler performs the detection SELECT and the
     * per-task action (RECLAIM / ABORT raw JDBC UPDATE) internally; the
     * caller (scanOnce) only aggregates the returned outcome list.
     *
     * <p>The shipped {@link NoOpTeamTaskRecoveryHandler} returns an empty
     * list (SKIP semantic — zero DB access, zero behaviour regression). A
     * functional handler returns one outcome per detected stuck task, in
     * detection order.
     *
     * @return a non-null, possibly-empty list of
     *         {@link TeamTaskRecoveryOutcome} — one per detected stuck task
     *         that was acted upon (RECLAIM / ABORT). Empty when no stuck
     *         tasks were detected (NoOp handler) or when no CLAIMED task
     *         exceeded the timeout threshold.
     */
    List<TeamTaskRecoveryOutcome> recoverStuckTasks();
}
