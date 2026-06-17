package io.nop.ai.agent.runtime.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shipped no-op default for {@link ISessionTimeoutHandler} (plan 229 /
 * L4-8-P4-TimeoutAbort).
 *
 * <p>Used by {@code ScheduledRecoveryManager} out-of-the-box: the daemon
 * detects timed-out sessions (status=running/pending + activity
 * timestamp older than {@code now - timeoutSeconds}) and LOG.warn's
 * each one, but takes no timeout action — preserving zero behaviour
 * regression with plan 226. This is the {@link TimeoutAction#SKIPPED}
 * semantic.
 *
 * <p>This is an explicit "observe-only" semantic, not a silent no-op
 * (Minimum Rules #24):
 * <ul>
 *   <li>{@link #handleTimeout} LOG.warn's the timed-out session ID and
 *       returns a SKIPPED-action {@link TimeoutOutcome} (non-null,
 *       never silently dropped).</li>
 * </ul>
 *
 * <p>Consistent with the NoOp pattern in
 * {@code NoOpRecoveryManager} / {@code NoOpOrphanRecoveryHandler} /
 * {@code NoOpSessionTakeoverLock}.
 *
 * <p>See plan 229 Phase 1.
 */
public final class NoOpSessionTimeoutHandler implements ISessionTimeoutHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpSessionTimeoutHandler.class);

    private static final NoOpSessionTimeoutHandler INSTANCE = new NoOpSessionTimeoutHandler();

    private NoOpSessionTimeoutHandler() {
    }

    public static NoOpSessionTimeoutHandler noOp() {
        return INSTANCE;
    }

    @Override
    public TimeoutOutcome handleTimeout(String sessionId) {
        // Non-silent observation (Minimum Rules #24): timed-out sessions
        // are LOG.warn'd, never silently ignored. Preserves plan 226
        // shipped default behaviour exactly (timeout detection is
        // observe-only out of the box).
        LOG.warn("NoOpSessionTimeoutHandler: detected timed-out session "
                + "(SKIPPED action, no timeout enforcement): sessionId={}", sessionId);
        return new TimeoutOutcome(sessionId, TimeoutAction.SKIPPED, true,
                "SKIPPED: timed-out session observed, no timeout action taken (shipped default)");
    }
}
