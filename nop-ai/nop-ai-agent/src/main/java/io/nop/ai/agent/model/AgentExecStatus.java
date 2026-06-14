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
    paused
}
