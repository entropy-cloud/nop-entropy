package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * The approve/deny decision returned by {@link IApprovalGate#requestApproval}.
 * Follows the same deny-with-reason pattern as {@link MatrixDecision} and
 * {@link ToolAccessResult}, with two distinctions specific to the approval-gate
 * contract (design §6.1):
 *
 * <ul>
 *   <li>An <b>approved</b> decision carries an {@code approver} identifier
 *       (e.g. {@code "auto"} for {@link AutoApproveGate}, or a user id for a
 *       functional gate) so the audit trail records <i>who</i> approved.</li>
 *   <li>A <b>denied</b> decision carries a structured {@link ApprovalDenialKind}
 *       (human-rejected vs timeout vs other) in addition to the free-text
 *       reason, so a human rejection and an approval timeout are
 *       distinguishable in audit (narrows finding L3-G1 to the gate's own
 *       semantics).</li>
 * </ul>
 *
 * <p>This type is scoped to the {@link IApprovalGate} decision surface only. It
 * is intentionally distinct from the {@code DenialResult} structured envelope
 * (design §6.3), which belongs to L3-7 ({@code IPostDenialGuard}) and carries
 * additional post-denial governance fields (suggestedNextStep,
 * actionFingerprint, retryable).
 */
public final class ApprovalDecision {

    private final boolean approved;
    private final String approver;
    private final ApprovalDenialKind denialKind;
    private final String reason;

    private ApprovalDecision(boolean approved, String approver,
                             ApprovalDenialKind denialKind, String reason) {
        this.approved = approved;
        this.approver = approver;
        this.denialKind = denialKind;
        this.reason = reason;
    }

    /**
     * An approve decision. The {@code approver} identifies who (or what)
     * approved the request — {@code "auto"} for {@link AutoApproveGate}, or a
     * user/system identifier for a functional gate. A null approver is treated
     * as {@code "system"} for audit consistency.
     */
    public static ApprovalDecision approve(String approver) {
        return new ApprovalDecision(true, approver != null ? approver : "system", null, null);
    }

    /**
     * A denial because a human reviewer explicitly rejected the request.
     */
    public static ApprovalDecision denyHumanRejected(String reason) {
        return new ApprovalDecision(false, null, ApprovalDenialKind.HUMAN_REJECTED, reason);
    }

    /**
     * A denial because no human response arrived within the wait window.
     */
    public static ApprovalDecision denyTimeout(String reason) {
        return new ApprovalDecision(false, null, ApprovalDenialKind.TIMEOUT, reason);
    }

    /**
     * A denial carrying an explicit kind. Use this for {@link ApprovalDenialKind#OTHER}
     * or when the kind is computed dynamically.
     */
    public static ApprovalDecision deny(ApprovalDenialKind kind, String reason) {
        ApprovalDenialKind effective = kind != null ? kind : ApprovalDenialKind.OTHER;
        return new ApprovalDecision(false, null, effective, reason);
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isDenied() {
        return !approved;
    }

    /**
     * @return the approver identifier (approved decisions only); null for
     *         denied decisions
     */
    public String getApprover() {
        return approver;
    }

    /**
     * @return the structured denial kind (denied decisions only); null for
     *         approved decisions
     */
    public ApprovalDenialKind getDenialKind() {
        return denialKind;
    }

    /**
     * @return the human-readable denial reason (denied decisions only); null
     *         for approved decisions
     */
    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalDecision that = (ApprovalDecision) o;
        return approved == that.approved
                && Objects.equals(approver, that.approver)
                && denialKind == that.denialKind
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(approved, approver, denialKind, reason);
    }

    @Override
    public String toString() {
        return "ApprovalDecision{" +
                "approved=" + approved +
                ", approver='" + approver + '\'' +
                ", denialKind=" + denialKind +
                ", reason='" + reason + '\'' +
                '}';
    }
}
