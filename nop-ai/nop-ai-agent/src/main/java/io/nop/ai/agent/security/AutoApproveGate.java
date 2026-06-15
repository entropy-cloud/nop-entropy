package io.nop.ai.agent.security;

/**
 * Pass-through {@link IApprovalGate} that <b>unconditionally approves all
 * requests</b> (including {@link SecurityLevel#RESTRICTED}) with approver
 * {@code "auto"}. Consistent with the {@code AllowAll*} opt-in pattern (plan
 * 193): this class is retained as a public opt-in for integrators who need
 * unconditional auto-approval (e.g. trusted test environments).
 *
 * <p><b>Not the engine default</b> (plan 199 / design §4.8): the
 * {@code DefaultAgentEngine} shipped default is now
 * {@link DefaultApprovalGate}, which defense-in-depth denies RESTRICTED
 * operations. To opt back into unconditional auto-approval, explicitly call
 * {@code DefaultAgentEngine.setApprovalGate(AutoApproveGate.autoApprove())}.
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
