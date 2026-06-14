package io.nop.ai.agent.security;

/**
 * Pass-through {@link IApprovalGate} used as the default when no functional
 * gate is registered. All requests are auto-approved with approver
 * {@code "auto"} (design §6.1 default). Consistent with the
 * {@code NoOpSecurityLevelResolver} / {@code PassThroughPermissionMatrix} /
 * {@code PassThroughModelRouter} sibling pattern.
 *
 * <p>The auto-approve semantics are semantically correct (design §6.1 default):
 * the shipped default does not require human approval, so unattended Layer 1
 * automation is unaffected. A functional gate (implementing the real
 * enqueue / wait / timeout workflow) is registered explicitly via
 * {@code DefaultAgentEngine.setApprovalGate}.
 *
 * <p>This implementation does not differentiate between {@link SecurityLevel}
 * values: STANDARD, ELEVATED, and RESTRICTED are all approved. Functional
 * enforcement of ELEVATED / RESTRICTED is the responsibility of a registered
 * gate implementation.
 */
public final class AutoApproveGate implements IApprovalGate {

    private static final AutoApproveGate INSTANCE = new AutoApproveGate();

    /** The approver identifier recorded on auto-approved decisions. */
    public static final String APPROVER = "auto";

    private AutoApproveGate() {
    }

    public static IApprovalGate autoApprove() {
        return INSTANCE;
    }

    @Override
    public ApprovalDecision requestApproval(SecurityLevel level, String toolName,
                                            ChannelKind channel, Principal principal,
                                            String sessionId, String agentName) {
        return ApprovalDecision.approve(APPROVER);
    }
}
