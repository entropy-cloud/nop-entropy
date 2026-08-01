package io.nop.ai.agent.plan.runtime;

/**
 * Classification of a single task-attempt failure (design §13.3 three-level
 * failure escalation, W2-3).
 *
 * <p>Each level has an independent per-task cumulative counter and a
 * configurable threshold on {@link FailureEscalationPolicy}. When the
 * counter for a level reaches its threshold, the host escalates the task to
 * {@code failed} (terminal), which in turn feeds the W1-4
 * {@link StagnationSignalType#REPEATED_ERRORS} signal via the normal
 * {@code recordError} pipeline (Contribute model, design §13.3 裁定 E).
 *
 * <p>The classification source is the {@link TaskRunner} — it is the plan-layer
 * execution boundary and the only component that knows why an attempt failed.
 * An untyped failure ({@code null} type) preserves the pre-W2-3 undifferentiated
 * behaviour (zero regression).
 *
 * @see TaskOutcome#failure(String, FailureType)
 */
public enum FailureType {
    /**
     * Quality failure — the attempt's output was rejected by a content/security
     * guardrail (aegis). Threshold: {@code max_aegis_rejections}.
     */
    QUALITY,

    /**
     * Stall failure — the attempt made no progress (e.g. the underlying agent
     * looped without advancing the goal). Threshold: {@code stale_task_max_retries}.
     *
     * <p>Distinct from the W1-4 {@link StagnationSignalType#TASK_STALLED} signal,
     * which counts consecutive failures of any type across attempts. This type
     * classifies the <em>kind</em> of a single failure.
     */
    STALL,

    /**
     * Infrastructure failure — the attempt failed due to a transient
     * provider/tool issue (timeout, connection, IO). Threshold:
     * {@code max_dispatch_retries}.
     */
    INFRASTRUCTURE
}
