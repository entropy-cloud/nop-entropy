package io.nop.ai.agent.security;

/**
 * The structured denial kind carried by a denied {@link ApprovalDecision}
 * (design §6.1). Narrows the audit-readiness finding L3-G1 to the approval
 * gate's own semantics: a human rejection and an approval timeout are two
 * distinct events that must be distinguishable in the audit trail.
 *
 * <p>This enum is scoped to the {@link IApprovalGate} decision surface only. It
 * is intentionally distinct from the broader {@code DenialResult.reason}
 * envelope (design §6.3), which belongs to L3-7 ({@code IPostDenialGuard}) and
 * covers additional downstream scenarios (e.g. {@code threshold_exceeded},
 * {@code repeated_same_intent}).
 *
 * <table>
 *   <tr><th>Kind</th><th>Semantics</th></tr>
 *   <tr><td>{@link #HUMAN_REJECTED}</td><td>A human reviewer explicitly rejected the request</td></tr>
 *   <tr><td>{@link #TIMEOUT}</td><td>No human response arrived within the wait window</td></tr>
 *   <tr><td>{@link #OTHER}</td><td>Any other denial cause not covered by the above (forward-compatible extension point)</td></tr>
 * </table>
 */
public enum ApprovalDenialKind {
    HUMAN_REJECTED,
    TIMEOUT,
    OTHER
}
