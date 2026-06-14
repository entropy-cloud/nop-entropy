package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * The structured denial envelope returned by {@link IPostDenialGuard#checkBeforeDispatch}
 * (design §6.3). Carries the post-denial governance context for a blocked
 * blind retry: a structured {@link DenialReason}, a {@link DenialSuggestedStep}
 * the agent may take to legitimately proceed, the {@code actionFingerprint}
 * that was matched against the session's denied set, a human-readable
 * {@code message}, and a {@code retryable} flag.
 *
 * <p>Follows the same immutable value-object pattern as
 * {@link ApprovalDecision} and {@link MatrixDecision}: factory construction,
 * all-field equals/hashCode, no mutators.
 *
 * <p><b>Boundary vs sibling denial types</b>: {@code DenialResult} is scoped
 * to the {@code IPostDenialGuard} consultation surface only (design §6.3). It
 * is intentionally distinct from:
 * <ul>
 *   <li>{@link ApprovalDecision} — the {@link IApprovalGate}'s own
 *       approve/deny decision (approver + denial kind + reason);</li>
 *   <li>{@link DenialRecord} — the {@link IDenialLedger}'s per-session
 *       record structure (sessionId + layerSource + timestamp).</li>
 * </ul>
 * The three types describe different stages of the defense-in-depth chain
 * (design §8): the gate produces an {@code ApprovalDecision}, the dispatch
 * path records a {@code DenialRecord} into the ledger, and the post-denial
 * guard produces a {@code DenialResult} when it blocks a subsequent blind
 * retry.
 */
public final class DenialResult {

    private final DenialReason reason;
    private final DenialSuggestedStep suggestedNextStep;
    private final String actionFingerprint;
    private final String message;
    private final boolean retryable;

    private DenialResult(DenialReason reason, DenialSuggestedStep suggestedNextStep,
                         String actionFingerprint, String message, boolean retryable) {
        this.reason = reason;
        this.suggestedNextStep = suggestedNextStep;
        this.actionFingerprint = actionFingerprint;
        this.message = message;
        this.retryable = retryable;
    }

    /**
     * Create a denial result carrying the full post-denial governance
     * context.
     *
     * @param reason            the structured denial reason; never null
     * @param suggestedNextStep the suggested recovery strategy; never null
     * @param actionFingerprint the SHA-256 fingerprint of the blocked action;
     *                          may be null when fingerprint is not applicable
     * @param message           the human-readable denial message; may be null
     * @param retryable         whether the agent may retry (after changing
     *                          its approach as suggested)
     */
    public static DenialResult of(DenialReason reason, DenialSuggestedStep suggestedNextStep,
                                  String actionFingerprint, String message, boolean retryable) {
        if (reason == null) {
            throw new IllegalArgumentException("DenialResult.reason must not be null");
        }
        if (suggestedNextStep == null) {
            throw new IllegalArgumentException("DenialResult.suggestedNextStep must not be null");
        }
        return new DenialResult(reason, suggestedNextStep, actionFingerprint, message, retryable);
    }

    /**
     * Convenience factory for the core post-denial-guard scenario
     * (design §6.3): a blind retry of an already-denied action. Produces a
     * {@code REPEATED_SAME_INTENT} denial with {@code suggestedNextStep=REPLAN}
     * and {@code retryable=false} — the agent must change its approach, not
     * re-submit the same intent.
     *
     * @param actionFingerprint the SHA-256 fingerprint of the blocked action;
     *                          may be null when fingerprint is not applicable
     * @param message           the human-readable denial message; may be null
     */
    public static DenialResult repeatedSameIntent(String actionFingerprint, String message) {
        return new DenialResult(DenialReason.REPEATED_SAME_INTENT,
                DenialSuggestedStep.REPLAN, actionFingerprint, message, false);
    }

    public DenialReason getReason() {
        return reason;
    }

    public DenialSuggestedStep getSuggestedNextStep() {
        return suggestedNextStep;
    }

    /**
     * @return the SHA-256 fingerprint of the blocked action; may be null
     *         when fingerprint is not applicable
     */
    public String getActionFingerprint() {
        return actionFingerprint;
    }

    /**
     * @return the human-readable denial message; may be null
     */
    public String getMessage() {
        return message;
    }

    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DenialResult that = (DenialResult) o;
        return retryable == that.retryable
                && reason == that.reason
                && suggestedNextStep == that.suggestedNextStep
                && Objects.equals(actionFingerprint, that.actionFingerprint)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, suggestedNextStep, actionFingerprint, message, retryable);
    }

    @Override
    public String toString() {
        return "DenialResult{" +
                "reason=" + reason +
                ", suggestedNextStep=" + suggestedNextStep +
                ", actionFingerprint='" + actionFingerprint + '\'' +
                ", message='" + message + '\'' +
                ", retryable=" + retryable +
                '}';
    }
}
