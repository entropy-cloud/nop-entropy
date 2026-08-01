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
 */
public final class TaskOutcome {

    private final boolean success;
    private final String errorText;

    private TaskOutcome(boolean success, String errorText) {
        this.success = success;
        this.errorText = errorText;
    }

    /** Successful outcome. */
    public static TaskOutcome success() {
        return new TaskOutcome(true, null);
    }

    /** Failed outcome with an error description (recorded as a runtime error). */
    public static TaskOutcome failure(String errorText) {
        return new TaskOutcome(false, errorText == null ? "task failed" : errorText);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorText() {
        return errorText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskOutcome)) return false;
        TaskOutcome that = (TaskOutcome) o;
        return success == that.success && Objects.equals(errorText, that.errorText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errorText);
    }
}
