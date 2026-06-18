package io.nop.ai.agent.runtime.recovery;

import java.util.Collections;
import java.util.List;

/**
 * Shipped no-op default for {@link ITeamTaskRecoveryHandler} (plan 240 /
 * L4-team-task-reclaim-and-timeout-abandon).
 *
 * <p>Used by {@code ScheduledRecoveryManager} out-of-the-box: the daemon's
 * scanOnce team-task recovery step calls
 * {@link #recoverStuckTasks()}, which returns an empty outcome list and
 * performs <strong>zero DB access</strong> — preserving zero behaviour
 * regression with plan 229 (team-task recovery is opt-in). This is the
 * {@link TeamTaskRecoveryAction#SKIP} semantic.
 *
 * <p>This is an explicit "observe-only / opt-out" semantic, not a silent
 * no-op (Minimum Rules #24):
 * <ul>
 *   <li>{@link #recoverStuckTasks} returns an explicit empty outcome list
 *       without even detecting stuck tasks (the team-task recovery step is
 *       disabled by default). Integrators who want recovery must inject a
 *       functional handler ({@code DefaultTeamTaskRecoveryHandler}) via
 *       {@code ScheduledRecoveryManager.setTeamTaskRecoveryHandler}.</li>
 * </ul>
 *
 * <p>Consistent with the NoOp pattern in {@code NoOpOrphanRecoveryHandler}
 * / {@code NoOpSessionTimeoutHandler} — but note those handlers are still
 * invoked per detected item (and LOG.warn each one), whereas this NoOp
 * performs no detection at all (a stronger opt-out, matching the
 * self-contained handler design — design 裁定 3, plan 240).
 *
 * <p>See plan 240 Phase 1 and design
 * {@code nop-ai-agent-team-task-reclaim.md}.
 */
public final class NoOpTeamTaskRecoveryHandler implements ITeamTaskRecoveryHandler {

    private static final NoOpTeamTaskRecoveryHandler INSTANCE = new NoOpTeamTaskRecoveryHandler();

    private NoOpTeamTaskRecoveryHandler() {
    }

    public static NoOpTeamTaskRecoveryHandler noOp() {
        return INSTANCE;
    }

    @Override
    public List<TeamTaskRecoveryOutcome> recoverStuckTasks() {
        // Explicit SKIP semantic (Minimum Rules #24): zero DB access, zero
        // behaviour regression. The team-task recovery step is opt-in —
        // integrators inject a functional handler to enable detection +
        // recovery.
        return Collections.emptyList();
    }
}
