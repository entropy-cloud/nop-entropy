package io.nop.ai.agent.runtime.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shipped no-op default for {@link IOrphanRecoveryHandler} (plan 226 /
 * L4-8-P4-RecoveryStrategy).
 *
 * <p>Used by {@code ScheduledRecoveryManager} out-of-the-box: the daemon
 * detects orphan sessions and LOG.warn's each one, but takes no recovery
 * action — preserving zero behaviour regression with plan 222. This is
 * the {@link RecoveryMode#SKIP} semantic.
 *
 * <p>This is an explicit "observe-only" semantic, not a silent no-op
 * (Minimum Rules #24):
 * <ul>
 *   <li>{@link #handleOrphan} LOG.warn's the orphan session ID and
 *       returns a SKIP-mode {@link RecoveryOutcome} (non-null, never
 *       silently dropped).</li>
 * </ul>
 *
 * <p>Consistent with the NoOp pattern in
 * {@code NoOpRecoveryManager} / {@code NoOpSessionTakeoverLock}.
 *
 * <p>See plan 226 Phase 1.
 */
public final class NoOpOrphanRecoveryHandler implements IOrphanRecoveryHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpOrphanRecoveryHandler.class);

    private static final NoOpOrphanRecoveryHandler INSTANCE = new NoOpOrphanRecoveryHandler();

    private NoOpOrphanRecoveryHandler() {
    }

    public static NoOpOrphanRecoveryHandler noOp() {
        return INSTANCE;
    }

    @Override
    public RecoveryOutcome handleOrphan(String sessionId) {
        // Non-silent observation (Minimum Rules #24): orphan sessions are
        // LOG.warn'd, never silently ignored. Preserves plan 222 shipped
        // default behaviour exactly.
        LOG.warn("NoOpOrphanRecoveryHandler: detected orphan session (SKIP mode, no recovery action): sessionId={}",
                sessionId);
        return new RecoveryOutcome(sessionId, RecoveryMode.SKIP, true,
                "SKIP: orphan session observed, no recovery action taken (shipped default)");
    }
}
