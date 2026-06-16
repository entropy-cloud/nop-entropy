package io.nop.ai.agent.security;

/**
 * Identifies the dispatch-path layer that produced a denial recorded into the
 * {@link IDenialLedger}. Every dispatch-path deny checkpoint (Layer 1 / 2 / 3)
 * maps to exactly one {@code DenialLayerSource}, so the ledger can attribute
 * each per-session denial to the security mechanism that triggered it.
 */
public enum DenialLayerSource {
    /** Layer 1 tool-access checker denial ({@code IToolAccessChecker}). */
    LAYER1_TOOL_ACCESS,

    /** Layer 1 permission-provider denial ({@code IPermissionProvider}). */
    LAYER1_PERMISSION,

    /** Layer 1 path-access checker denial ({@code IPathAccessChecker}). */
    LAYER1_PATH_ACCESS,

    /** Layer 2 security-policy denial ({@code ISecurityLevelResolver} + {@code IPermissionMatrix}). */
    LAYER2_SECURITY_POLICY,

    /**
     * Layer 2 conflict-strategy denial (plan 214 / design
     * {@code nop-ai-agent-multi-agent.md} §4.4): a multi-agent write
     * conflict detected by the {@code IConflictStrategy}. Conflict
     * detection is a coordination concern (not a security boundary), so it
     * carries its own denial-layer source rather than reusing
     * {@link #LAYER2_SECURITY_POLICY}.
     */
    LAYER2_CONFLICT_STRATEGY,

    /** Layer 3 approval-gate denial ({@code IApprovalGate}). */
    LAYER3_APPROVAL_GATE,

    /**
     * Layer 3 post-denial-guard denial ({@code IPostDenialGuard},
     * design §6.3 / L3-7): a blind-retry consultation denial — the dispatch
     * path's pre-Layer-1 consultation detected that the action's fingerprint
     * was already in the session's denied set.
     */
    LAYER3_POST_DENIAL_GUARD
}
