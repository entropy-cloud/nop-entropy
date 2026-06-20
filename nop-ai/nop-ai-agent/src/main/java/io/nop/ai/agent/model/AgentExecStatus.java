package io.nop.ai.agent.model;

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
