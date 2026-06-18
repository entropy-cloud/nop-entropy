package io.nop.ai.agent.runtime.recovery;

import java.util.Objects;

/**
 * Immutable record of the outcome of a single stuck-team-task recovery
 * attempted by {@link ITeamTaskRecoveryHandler#recoverStuckTasks} (plan 240 /
 * L4-team-task-reclaim-and-timeout-abandon).
 *
 * <p>Each stuck CLAIMED team task detected by the handler is acted upon
 * (RECLAIM / ABORT) and produces one {@code TeamTaskRecoveryOutcome}.
 * Outcomes are aggregated into
 * {@link RecoveryScanResult#getTeamTaskRecoveryActions()} so a single scan's
 * full team-task recovery summary is observable (Minimum Rules #24 —
 * non-silent: every acted-upon task has an outcome, never silently dropped).
 * Mirrors the {@link RecoveryOutcome} (plan 226) and {@link TimeoutOutcome}
 * (plan 229) structure.
 *
 * <p><b>Field semantics</b>:
 * <ul>
 *   <li>{@code taskId} — the stuck team task ID this outcome applies to.</li>
 *   <li>{@code action} — the {@link TeamTaskRecoveryAction} that produced
 *       this outcome (RECLAIM / ABORT). SKIP is not produced per-task by
 *       the functional handler (SKIP semantics = NoOp handler returning an
 *       empty outcome list).</li>
 *   <li>{@code succeeded} — whether the recovery action succeeded.
 *       {@code false} indicates the action was attempted but could not
 *       complete (e.g. the task already transitioned between detection and
 *       action — CAS affected-rows=0, or the raw JDBC UPDATE threw a
 *       SQLException). A {@code false} outcome is always accompanied by a
 *       descriptive {@code message} — never silent.</li>
 *   <li>{@code message} — human-readable detail. For a successful RECLAIM
 *       this is the confirmation text; for a failed action this contains
 *       the transition reason or exception summary.</li>
 * </ul>
 *
 * <p>See plan 240 and design {@code nop-ai-agent-team-task-reclaim.md}.
 */
public final class TeamTaskRecoveryOutcome {

    private final String taskId;
    private final TeamTaskRecoveryAction action;
    private final boolean succeeded;
    private final String message;

    public TeamTaskRecoveryOutcome(String taskId, TeamTaskRecoveryAction action,
                                   boolean succeeded, String message) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.succeeded = succeeded;
        this.message = message;
    }

    public String getTaskId() {
        return taskId;
    }

    public TeamTaskRecoveryAction getAction() {
        return action;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "TeamTaskRecoveryOutcome{taskId=" + taskId
                + ", action=" + action
                + ", succeeded=" + succeeded
                + ", message=" + message + '}';
    }
}
