package io.nop.ai.agent.plan.runtime;

import java.util.Objects;

/**
 * The outcome of running a single task via {@link TaskRunner}, consumed by
 * {@link PlanExecutor} to advance the runtime execution state.
 *
 * <p>An immutable value object. On failure, {@link #errorText} is recorded
 * by the host as an {@code AgentPlanError}-equivalent runtime record, which
 * is the input source for the {@link StagnationSignalType#REPEATED_ERRORS}
 * stagnation signal.
 *
 * <p>A failure may carry a {@link FailureType} (design §13.3 three-level
 * failure escalation, W2-3). When non-null, the host increments the
 * per-task typed-failure counter and consults the
 * {@link FailureEscalationPolicy} for per-attempt escalation. When null
 * (untyped), the failure is treated with the pre-W2-3 undifferentiated
 * behaviour (zero regression). The classification source is the
 * {@link TaskRunner} — it is the plan-layer execution boundary.
 */
public final class TaskOutcome {

    private final boolean success;
    private final String errorText;
    private final FailureType failureType;

    private TaskOutcome(boolean success, String errorText, FailureType failureType) {
        this.success = success;
        this.errorText = errorText;
        this.failureType = failureType;
    }

    /** Successful outcome. */
    public static TaskOutcome success() {
        return new TaskOutcome(true, null, null);
    }

    /**
     * Failed outcome with an error description (recorded as a runtime error).
     * The failure is untyped ({@code failureType = null}), preserving the
     * pre-W2-3 undifferentiated behaviour — zero regression for existing
     * {@link TaskRunner} implementations.
     */
    public static TaskOutcome failure(String errorText) {
        return new TaskOutcome(false, errorText == null ? "task failed" : errorText, null);
    }

    /**
     * Failed outcome with an error description and a typed classification
     * (design §13.3 W2-3). The type drives the per-task typed-failure counter
     * and per-attempt escalation via {@link FailureEscalationPolicy}.
     *
     * @param errorText   the error description (null → "task failed")
     * @param failureType the failure classification (null → untyped, equivalent
     *                    to {@link #failure(String)})
     */
    public static TaskOutcome failure(String errorText, FailureType failureType) {
        return new TaskOutcome(false, errorText == null ? "task failed" : errorText, failureType);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorText() {
        return errorText;
    }

    /**
     * The typed classification of this failure, or {@code null} if untyped
     * (pre-W2-3 undifferentiated failure). Always {@code null} for success.
     */
    public FailureType getFailureType() {
        return failureType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskOutcome)) return false;
        TaskOutcome that = (TaskOutcome) o;
        return success == that.success
                && Objects.equals(errorText, that.errorText)
                && failureType == that.failureType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errorText, failureType);
    }
}
