package io.nop.ai.agent.engine;

public enum AgentEventType {
    EXECUTION_STARTED,

    ITERATION_STARTED,

    LLM_RESPONSE_RECEIVED,

    TOOL_CALL_STARTED,

    TOOL_CALL_COMPLETED,

    TOOL_CALL_DENIED,

    PATH_ACCESS_DENIED,

    EXECUTION_COMPLETED,

    EXECUTION_FAILED,

    SESSION_CREATED,

    SESSION_LOADED,

    SESSION_CANCEL_REQUESTED,

    SESSION_CANCELLED,

    SESSION_FORKED,

    FORCED_STOP,

    /**
     * Session paused by Layer 3 denial-ledger governance (design §6.2): the
     * cumulative per-session denial count reached the configured threshold.
     * Semantically distinct from {@link #EXECUTION_FAILED} (error),
     * {@link #FORCED_STOP} (system context overflow), and
     * {@link #SESSION_CANCELLED} (user-initiated): paused is a governance
     * policy action triggered automatically by accumulated denials.
     */
    SESSION_PAUSED
}
