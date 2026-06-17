package io.nop.ai.agent.runtime.recovery;

import java.util.Objects;

/**
 * Immutable record of the outcome of a single
 * {@link ISessionTimeoutHandler#handleTimeout} invocation, for
 * observability and testing (plan 229 / L4-8-P4-TimeoutAbort).
 *
 * <p>Each timed-out session detected by
 * {@code ScheduledRecoveryManager.scanOnce} is passed to the configured
 * handler, which returns a {@code TimeoutOutcome}. Outcomes are
 * aggregated into {@link RecoveryScanResult#getTimeoutActions()} so a
 * single scan's full timeout-handling summary is observable (Minimum
 * Rules #24 — non-silent: every timed-out session has an outcome,
 * never silently dropped). Mirrors the {@link RecoveryOutcome}
 * structure (plan 226).
 *
 * <p><b>Field semantics</b>:
 * <ul>
 *   <li>{@code sessionId} — the timed-out session ID this outcome
 *       applies to.</li>
 *   <li>{@code action} — the {@link TimeoutAction} that produced this
 *       outcome (LOCAL_CANCELLED / FORCE_FAILED / SKIPPED_REMOTE /
 *       SKIPPED). Reflects the three-way classification result (design
 *       裁定 1) for a functional handler, or the NoOp SKIPPED for the
 *       shipped default.</li>
 *   <li>{@code succeeded} — whether the action succeeded.
 *       {@code false} indicates the action was attempted but could not
 *       complete (e.g. LOCAL_CANCELLED hit a synchronous
 *       {@code cancelSession} exception, FORCE_FAILED found the session
 *       already transitioned, or the action threw). A {@code false}
 *       outcome is always accompanied by a descriptive {@code message}
 *       — never silent.</li>
 *   <li>{@code message} — human-readable detail. For SKIPPED /
 *       SKIPPED_REMOTE this is the observation text; for a failed
 *       action this contains the exception summary or transition
 *       reason.</li>
 * </ul>
 *
 * <p>See plan 229 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3.
 */
public final class TimeoutOutcome {

    private final String sessionId;
    private final TimeoutAction action;
    private final boolean succeeded;
    private final String message;

    public TimeoutOutcome(String sessionId, TimeoutAction action, boolean succeeded, String message) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.succeeded = succeeded;
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public TimeoutAction getAction() {
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
        return "TimeoutOutcome{sessionId=" + sessionId
                + ", action=" + action
                + ", succeeded=" + succeeded
                + ", message=" + message + '}';
    }
}
