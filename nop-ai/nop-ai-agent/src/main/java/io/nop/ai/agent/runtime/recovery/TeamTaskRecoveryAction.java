package io.nop.ai.agent.runtime.recovery;

/**
 * Recovery action that {@link ITeamTaskRecoveryHandler} may apply to a stuck
 * CLAIMED team task (plan 240 / L4-team-task-reclaim-and-timeout-abandon).
 *
 * <p>A <em>stuck</em> team task is one whose {@code STATUS='CLAIMED'} and
 * whose {@code ai_agent_team_task.UPDATED_AT} is older than the configured
 * {@code taskTimeoutSeconds} (time-based detection, mirroring plan 229
 * session-timeout). The recovery daemon's scanOnce step delegates to the
 * handler, which decides and acts per-task.
 *
 * <p><b>Action semantics</b>:
 * <ul>
 *   <li>{@link #RECLAIM} — transition the stuck task
 *       {@code CLAIMED → CREATED} (clear {@code claimedBy}), making it
 *       re-claimable by another member. Retry-friendly: suitable for
 *       dev/test or environments where transient crashes are expected and
 *       the work should be re-attempted.</li>
 *   <li>{@link #ABORT} — transition the stuck task
 *       {@code CLAIMED → ABANDONED} (terminal), marking it as failed.
 *       Strict mode: suitable for production where a stuck task should fail
 *       fast rather than be silently re-attempted.</li>
 *   <li>{@link #SKIP} — observe-only: LOG.warn the stuck task and take no
 *       DB action. This is the semantic of the shipped default
 *       {@link NoOpTeamTaskRecoveryHandler} (zero behaviour regression).
 *       A {@link DefaultTeamTaskRecoveryHandler} is never constructed with
 *       SKIP (fail-fast: SKIP integrators use the NoOp handler directly).</li>
 * </ul>
 *
 * <p>Terminal statuses (COMPLETED / ABANDONED) are never affected: both
 * RECLAIM and ABORT use a conditional {@code WHERE STATUS='CLAIMED'} CAS
 * guard, so a task that transitioned between detection and action yields
 * affected-rows=0 → {@code succeeded=false} (honest, non-silent).
 *
 * <p>See plan 240 and design
 * {@code nop-ai-agent-team-task-reclaim.md}.
 */
public enum TeamTaskRecoveryAction {
    /**
     * Reset the stuck CLAIMED task to CREATED (clear claimedBy) so it can be
     * re-claimed by another member. Raw JDBC conditional UPDATE:
     * {@code SET STATUS='CREATED', CLAIMED_BY=NULL WHERE TASK_ID=? AND
     * STATUS='CLAIMED'}.
     */
    RECLAIM,

    /**
     * Mark the stuck CLAIMED task as ABANDONED (terminal failure). Raw JDBC
     * conditional UPDATE: {@code SET STATUS='ABANDONED' WHERE TASK_ID=? AND
     * STATUS='CLAIMED'}.
     */
    ABORT,

    /**
     * Observe-only: LOG.warn the stuck task and take no DB action. The
     * shipped {@link NoOpTeamTaskRecoveryHandler} returns an empty outcome
     * list (no per-task outcomes — no DB access at all). Not a valid
     * {@link DefaultTeamTaskRecoveryHandler} configuration (fail-fast).
     */
    SKIP
}
