package io.nop.ai.agent.runtime.recovery;

import java.util.Objects;

/**
 * Immutable record of the outcome of a single
 * {@link IOrphanRecoveryHandler#handleOrphan} invocation, for
 * observability and testing (plan 226 / L4-8-P4-RecoveryStrategy).
 *
 * <p>Each orphan session detected by {@code ScheduledRecoveryManager.scanOnce}
 * is passed to the configured handler, which returns a
 * {@code RecoveryOutcome}. Outcomes are aggregated into
 * {@link RecoveryScanResult#getRecoveryActions()} so a single scan's
 * full recovery summary is observable (Minimum Rules #24 — non-silent:
 * every orphan has an outcome, never silently dropped).
 *
 * <p><b>Field semantics</b>:
 * <ul>
 *   <li>{@code sessionId} — the orphan session ID this outcome applies
 *       to.</li>
 *   <li>{@code mode} — the {@link RecoveryMode} that produced this
 *       outcome (RESUME / ABORT / SKIP). Reflects the handler's
 *       configured mode.</li>
 *   <li>{@code succeeded} — whether the recovery action succeeded.
 *       {@code false} indicates the action was attempted but could not
 *       complete (e.g. RESUME hit a takeover-lock conflict, ABORT found
 *       the session already transitioned, or the action threw). A
 *       {@code false} outcome is always accompanied by a descriptive
 *       {@code message} — never silent.</li>
 *   <li>{@code message} — human-readable detail. For SKIP this is the
 *       observation text; for a failed action this contains the
 *       exception summary or transition reason.</li>
 * </ul>
 *
 * <p>See plan 226 and design
 * {@code nop-ai-agent-actor-runtime-vision.md} §6.3.
 */
public final class RecoveryOutcome {

    private final String sessionId;
    private final RecoveryMode mode;
    private final boolean succeeded;
    private final String message;

    public RecoveryOutcome(String sessionId, RecoveryMode mode, boolean succeeded, String message) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.succeeded = succeeded;
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public RecoveryMode getMode() {
        return mode;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "RecoveryOutcome{sessionId=" + sessionId
                + ", mode=" + mode
                + ", succeeded=" + succeeded
                + ", message=" + message + '}';
    }
}
