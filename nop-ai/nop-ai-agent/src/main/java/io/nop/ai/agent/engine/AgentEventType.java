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
    SESSION_PAUSED,

    /**
     * Session resumed by human intervention after a Layer 3 denial-ledger
     * pause (design §6.2 {@code pauseBehavior = sticky}): an explicit
     * {@code IAgentEngine.resumeSession(sessionId, approver, reason)} call
     * cleared the pause (via {@code IDenialLedger.reset}) and re-executed the
     * session. Semantically the inverse of {@link #SESSION_PAUSED} — paused
     * is an automatic governance action, resumed is an explicit human
     * recovery action. The event payload carries {@code approver},
     * {@code reason}, and {@code preResetDenialCount} for audit trail.
     */
    SESSION_RESUMED,

    /**
     * Session escalated by goal-tracker stuck detection (plan 211 / plan 277
     * AR-07): the goal tracker assessed the session as STUCK (looping /
     * no-progress), so autonomous execution is halted with an escalation
     * outcome. Semantically distinct from {@link #SESSION_PAUSED} (denial-
     * ledger governance, recoverable via resumeSession): escalated is a
     * terminal outcome requiring human re-evaluation, not a simple ledger
     * reset. Before plan 277, {@code handleGoalStuck} incorrectly published
     * {@link #SESSION_PAUSED}; it now publishes this dedicated event so
     * {@code SESSION_PAUSED} subscribers do not misinterpret an escalation
     * as a recoverable pause.
     */
    SESSION_ESCALATED,

    /**
     * Session restored after a process crash/restart (plan 183
     * crash/restart durable session restore protocol, design §1.1 / §5.4a):
     * an explicit {@code IAgentEngine.restoreSession(sessionId, approver, reason)}
     * call detected that the session was not in the active-execution map
     * ({@code runningExecutions}), loaded its persistent state from a
     * {@link io.nop.ai.agent.session.FileBackedSessionStore}, rebuilt the
     * {@link AgentExecutionContext} via {@code buildBaseExecutionContext}
     * (complete message-history replay), verified consistency against the
     * latest {@link io.nop.ai.agent.reliability.Checkpoint} (checkpoint
     * journal consumption — first time the checkpoint subsystem is used on
     * the restore path), transitioned status to {@code running}, and resumed
     * ReAct execution.
     * <p>
     * Semantically distinct from {@link #SESSION_RESUMED}: resume clears a
     * denial-ledger sticky-pause on an in-memory paused session (plan 180);
     * restore recovers a session from persistent state after a crash/restart
     * where the session is not in the active memory (plan 183).
     * <p>
     * The event payload carries {@code approver}, {@code reason}, and
     * {@code latestCheckpointWatermark} (null when no checkpoint exists) for
     * audit trail.
     */
    SESSION_RESTORED
}
