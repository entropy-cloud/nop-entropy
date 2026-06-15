package io.nop.ai.agent.security;

/**
 * Shipped default {@link IApprovalGate} used as the engine default (design §4.8
 * / §6.1). Unlike {@link AutoApproveGate} (which unconditionally approves all
 * levels), this gate implements a <b>defense-in-depth</b> tightening for
 * {@link SecurityLevel#RESTRICTED}: STANDARD and ELEVATED are auto-approved
 * (approver = {@value #APPROVER}), but RESTRICTED is denied.
 *
 * <p><b>Defense-in-depth</b>: the shipped default
 * {@link NoOpSecurityLevelResolver} always resolves to
 * {@link SecurityLevel#STANDARD}, so RESTRICTED is never produced under the
 * default configuration and this gate's RESTRICTED denial is never reached.
 * The denial becomes observable only when an integrator registers a functional
 * {@link ISecurityLevelResolver} that returns RESTRICTED — at which point the
 * gate ensures the RESTRICTED operation is not silently auto-approved.
 *
 * <p><b>ELEVATED is still approved</b>: design semantics define ELEVATED as
 * "requires confirmation" (stricter than STANDARD, looser than RESTRICTED).
 * Confirmation is not equivalent to human approval — a functional gate
 * implementation is responsible for the real ELEVATED confirmation flow. The
 * default gate only defense-in-depth denies RESTRICTED ("requires approval").
 *
 * <p>This class replaces {@link AutoApproveGate} as the
 * {@code DefaultAgentEngine} default (plan 199 / [13-4]).
 * {@link AutoApproveGate} is retained as a public opt-in for integrators who
 * need unconditional auto-approval (e.g. trusted test environments), analogous
 * to the {@code AllowAll*} checker opt-in pattern (plan 193).
 */
public final class DefaultApprovalGate implements IApprovalGate {

    /** The approver identifier recorded on auto-approved decisions. */
    public static final String APPROVER = "default";

    /** The denial reason recorded on RESTRICTED defense-in-depth denials. */
    public static final String RESTRICTED_DENY_REASON =
            "RESTRICTED operations require a functional approval gate (defense-in-depth default deny). "
                    + "Register a functional IApprovalGate via DefaultAgentEngine.setApprovalGate, "
                    + "or explicitly opt into unconditional auto-approval via AutoApproveGate.";

    @Override
    public ApprovalDecision requestApproval(SecurityLevel level, String toolName,
                                             ChannelKind channel, Principal principal,
                                             String sessionId, String agentName) {
        if (level == SecurityLevel.RESTRICTED) {
            return ApprovalDecision.deny(ApprovalDenialKind.OTHER, RESTRICTED_DENY_REASON);
        }
        return ApprovalDecision.approve(APPROVER);
    }
}
