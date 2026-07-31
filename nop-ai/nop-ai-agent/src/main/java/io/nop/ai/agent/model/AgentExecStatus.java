package io.nop.ai.agent.model;

/**
 * Agent runtime execution status (agent engine internal, not persisted).
 * <p>
 * <b>P2-MA1-035 ruling (2026-07-31) — boundary documented, no merge:</b> this
 * enum is the agent-engine runtime status. The ORM entity
 * {@code NopAiSession.status} uses its own lifecycle dict {@code ai/session-status}
 * (int: CREATED/RUNNING/IDLE/COMPLETED/FAILED/STOPPED) managed by the
 * nop-ai-dao/service persistence layer. The two layers do not exchange status
 * values (no conversion code exists) and represent different concepts (runtime
 * execution state incl. paused/truncated vs persisted session lifecycle).
 * Merging them would be a cross-module contract change; keep the boundary and
 * do not "unify" without a dedicated design decision.
 */
public enum AgentExecStatus {
    pending,

    running,

    completed,

    failed,

    cancelled,

    forced_stopped,

    escalated,

    /**
     * Session paused by Layer 3 denial-ledger governance (design §6.2): the
     * cumulative per-session denial count reached the configured threshold, so
     * autonomous execution is halted until a human recovery action resets the
     * ledger ({@code IDenialLedger.reset}). Distinct from {@link #cancelled}
     * (user-initiated), {@link #forced_stopped} (system context-window overflow),
     * and {@link #escalated} (escalation path) — paused is a governance policy
     * action triggered automatically by accumulated denials.
     */
    paused,

    /**
     * AR-14 (plan 277): the ReAct loop reached the configured max-iterations
     * budget without the completion judge declaring completion. Before plan
     * 277, this was silently reported as {@link #completed}, which misled
     * downstream consumers (sub-agent success flags, billing, UI status)
     * into treating a truncated session as a successful completion.
     * Semantically distinct from {@link #completed} (the agent voluntarily
     * finished) and {@link #failed} (an error occurred) — truncated means
     * the agent ran out of iteration budget, not that it succeeded or
     * errored. Terminal: a truncated session is not restored by
     * {@code restorePendingSessions} (it reached a final outcome).
     */
    truncated
}
