package io.nop.ai.agent.security;

/**
 * Layer 3 approval-governance contract: the human approval gate (design §6.1).
 * After the Layer 2 policy ({@link ISecurityLevelResolver} +
 * {@link IPermissionMatrix}) allows a tool call, the dispatch path consults the
 * gate with the resolved {@link SecurityLevel} and the tool-call context. The
 * gate decides whether the action may proceed without human approval, requires
 * approval (and waits for a human response), or is denied.
 *
 * <p><b>Defense-in-depth position</b> (design §8): the gate sits in the chain
 * after the {@code ISecurityLevelResolver} and before the downstream
 * {@code IDenialLedger} (L3-6 — landed) /
 * {@code IPostDenialGuard} (L3-7 — landed) /
 * {@code ISandboxBackend} (Layer 4 — deferred successor). It is the Layer 3
 * chain entry point.
 *
 * <p><b>Default</b>: {@link AutoApproveGate} — all requests are auto-approved
 * (approver = "auto"). This is the shipped default injected into the engine, so
 * unattended Layer 1 automation is unaffected unless a functional gate is
 * explicitly registered. A functional gate implementation is responsible for
 * the real human-approval workflow (enqueue request, wait with timeout, route
 * via an approval channel — design §6.1 steps 2-4); those concerns are
 * out-of-scope for {@link AutoApproveGate}.
 *
 * <p><b>Dispatch-path consultation</b>: the {@code ReActAgentExecutor}
 * dispatch loop consults the gate after the Layer 2 matrix allows a tool call
 * and before the call is added to {@code allowedCalls}. A denial records an
 * {@link AuditEvent} (DENY + reason + matched rule {@code "layer3_approval_gate"})
 * and produces a {@code ChatToolResponseMessage.error(...)}, mirroring the
 * Layer 1 / Layer 2 deny paths.
 */
public interface IApprovalGate {

    /**
     * Decide whether the action requires and has obtained human approval.
     *
     * @param level     the security level resolved for the action (never null);
     *                  the gate typically only acts on {@link SecurityLevel#ELEVATED}
     *                  / {@link SecurityLevel#RESTRICTED}
     * @param toolName  the tool name / operation category under consideration
     *                  (e.g. {@code shell.exec}); may be null
     * @param channel   the communication channel; may be null (unknown)
     * @param principal the identity; may be null (anonymous)
     * @param sessionId the session identifier; may be null
     * @param agentName the agent name; may be null
     * @return an approve/deny decision; denials carry an auditable
     *         {@link ApprovalDenialKind} and reason; never null
     */
    ApprovalDecision requestApproval(SecurityLevel level, String toolName,
                                     ChannelKind channel, Principal principal,
                                     String sessionId, String agentName);
}
