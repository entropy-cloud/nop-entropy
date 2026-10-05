package io.nop.ai.agent.engine;

/**
 * Plan 2290 (WI9): authoritative key contract for {@link AgentEvent} payloads.
 * Every publish site must reference these constants (compile-time protection
 * against key drift); the full key list per event type lives in the javadoc of
 * each constant. Unknown keys are ignored by consumers (forward-compatible —
 * {@code AgentEvent.getSchemaVersion()} ≥ 1 contract, see
 * {@code 02-execution-model.md} 事件契约节).
 */
public final class AgentEventPayloads {

    private AgentEventPayloads() {
    }

    // ---- tool call repair audit (WI12) ----
    /** TOOL_CALL_STARTED payload: the provider tool-call id (join key). */
    public static final String CALL_ID = "callId";
    /** TOOL_CALL_STARTED payload: whether a repair changed the call. */
    public static final String REPAIR_APPLIED = "repairApplied";
    /** TOOL_CALL_STARTED payload: tool name before repair (present when repaired). */
    public static final String NAME_BEFORE = "nameBefore";
    /** TOOL_CALL_STARTED payload: tool name after repair. */
    public static final String NAME_AFTER = "nameAfter";
    /** TOOL_CALL_STARTED payload: arguments text before repair (truncated to 512 chars). */
    public static final String ARGS_BEFORE = "argsBefore";
    /** TOOL_CALL_STARTED payload: arguments text after repair (truncated to 512 chars). */
    public static final String ARGS_AFTER = "argsAfter";

    // ---- subscriber failure channel (WI10) ----
    /** Subscriber failure payloads: subscriber class name. */
    public static final String SUBSCRIBER_ID = "subscriberId";
    /** Subscriber failure payloads: cumulative failure count. */
    public static final String FAILURE_COUNT = "failureCount";

    // ---- execution lifecycle ----
    /** EXECUTION_COMPLETED payload: veto origin point ("PRE_CALL" etc.). */
    public static final String VETOED_AT = "vetoedAt";
    /** EXECUTION_COMPLETED payload: veto/block reason. */
    public static final String REASON = "reason";
    /** EXECUTION_COMPLETED payload: guardrail-blocked marker (boolean). */
    public static final String GUARDRAIL_BLOCKED = "guardrailBlocked";
    /** EXECUTION_FAILED payload origin (SESSION_CANCELLED: user reason). */
    public static final String CANCEL_REASON = "reason";

    // ---- iteration / LLM turns ----
    /** ITERATION_STARTED / LLM_RESPONSE_RECEIVED payload: 0-based iteration. */
    public static final String ITERATION = "iteration";
    /** LLM_RESPONSE_RECEIVED payload: whether the response carries tool calls. */
    public static final String HAS_TOOL_CALLS = "hasToolCalls";

    // ---- tool calls ----
    /** TOOL_CALL_* payload: the tool name. */
    public static final String TOOL_NAME = "toolName";
    /** TOOL_CALL_COMPLETED payload: terminal status of the tool call. */
    public static final String TOOL_STATUS = "status";

    // ---- session history count ----
    /** SESSION_LOADED payload: restored history message count. */
    public static final String HISTORY_COUNT = "historyCount";

    // ---- injection queue (WI8) ----
    /** INJECTION_QUEUE_UPDATED payload: injection semantics name. */
    public static final String SEMANTICS = "semantics";
    /** INJECTION_QUEUE_UPDATED payload: action = "enqueued" | "drained". */
    public static final String ACTION = "action";
    /** INJECTION_QUEUE_UPDATED payload: pending entries after the action. */
    public static final String PENDING_COUNT = "pendingCount";

    // ---- context compaction ----
    /** COMPACTION payload: pre-compaction token estimate. */
    public static final String TOKENS_BEFORE = "tokensBefore";
    /** COMPACTION payload: post-compaction token estimate. */
    public static final String TOKENS_AFTER = "tokensAfter";

    // ---- guard / governance ----
    /** Guard/governance payloads: denial count at the decision point. */
    public static final String DENIAL_COUNT = "denialCount";
    /** FORCED_STOP payload: estimated tokens at the stop. */
    public static final String ESTIMATED_TOKENS = "estimatedTokens";
    /** FORCED_STOP payload: the context-window ceiling. */
    public static final String MAX_CONTEXT_TOKENS = "maxContextTokens";
    /** FORCED_STOP payload: the forced-stop percent threshold. */
    public static final String FORCED_STOP_PERCENT = "forcedStopPercent";

    // ---- security ----
    /** Security denial payload: which checkpoint layer denied. */
    public static final String LAYER_SOURCE = "layerSource";
    /** Security denial payload: the access path in question. */
    public static final String PATH = "path";
    /** Security denial payload: resolved security level. */
    public static final String SECURITY_LEVEL = "securityLevel";
    /** Security denial payload: channel kind. */
    public static final String CHANNEL = "channel";
    /** Security denial payload: denial classification kind. */
    public static final String DENIAL_KIND = "denialKind";
    /** Security denial payload: human-readable denial reason. */
    public static final String DENIAL_REASON = "denialReason";
    /** Security denial payload: suggested next step for the caller. */
    public static final String SUGGESTED_NEXT_STEP = "suggestedNextStep";
    /** Conflict payload: the contested path. */
    public static final String CONFLICT_PATH = "conflictPath";
    /** Conflict payload: applied conflict strategy. */
    public static final String CONFLICT_STRATEGY = "conflictStrategy";
    /** Conflict payload: conflicting session ids. */
    public static final String CONFLICTING_SESSIONS = "conflictingSessions";

    // ---- execution lifecycle (cont.) ----
    /** EXECUTION_STARTED payload: the agent name taking the execution. */
    public static final String AGENT_NAME = "agentName";

    // ---- session lifecycle ----
    /** SESSION_CANCEL_REQUESTED payload: force flag. */
    public static final String FORCED = "forced";
    /** SESSION_FORKED payload: source session id. */
    public static final String PARENT_SESSION_ID = "parentSessionId";
    /** SESSION_FORKED payload: new session id. */
    public static final String CHILD_SESSION_ID = "childSessionId";
    /** SESSION_FORKED payload: context inheritance flag. */
    public static final String INHERIT_CONTEXT = "inheritContext";
    /** SESSION_RESTORED payload: divergence detected flag. */
    public static final String DIVERGENCE_DETECTED = "divergenceDetected";
    /** SESSION_RESTORED payload: rejected checkpoint watermark. */
    public static final String REJECTED_CHECKPOINT_WATERMARK = "rejectedCheckpointWatermark";
    /** SESSION_RESTORED payload: shadowed token count. */
    public static final String SHADOWED_TOKEN_COUNT = "shadowedTokenCount";
    /** SESSION_RESTORED payload: snapshot id. */
    public static final String SNAPSHOT_ID = "snapshotId";
    /** Resume/restore payloads: the approver. */
    public static final String APPROVER = "approver";
    /** SESSION_RESTORED payload: latest checkpoint watermark. */
    public static final String LATEST_CHECKPOINT_WATERMARK = "latestCheckpointWatermark";
    /** SESSION_RESTORED payload: pre-restore status name. */
    public static final String PRE_RESTORE_STATUS = "preRestoreStatus";
    /** Session pause payload: pre-reset denial count. */
    public static final String PRE_RESET_DENIAL_COUNT = "preResetDenialCount";

    // ---- execution result ----
    /** EXECUTION_COMPLETED payload: total iterations at completion. */
    public static final String TOTAL_ITERATIONS = "totalIterations";
    /** EXECUTION_COMPLETED payload: total tokens used. */
    public static final String TOTAL_TOKENS_USED = "totalTokensUsed";
    /** EXECUTION_COMPLETED payload: execution duration ms. */
    public static final String DURATION_MS = "durationMs";
    /** POST_REASONING bail payload: bail reason. */
    public static final String BAIL_REASON = "bailReason";
}
