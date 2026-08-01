package io.nop.ai.agent.plan.runtime;

/**
 * Construction-time policy for three-level failure escalation (design §13.3,
 * W2-3). Each level has an independent per-task cumulative counter and a
 * threshold; when the counter reaches the threshold the host escalates the
 * task to {@code failed}.
 *
 * <p>Immutable and thread-safe (construction-time config, analogous to
 * {@link ReplanPolicy} / {@code StagnationDetector(staleTaskCycles,
 * maxErrorsPerTask)}). Avoids codegen cascade — thresholds are constructor
 * parameters, not xdef elements.
 *
 * <p>The shipped default {@link #disabled()} sets all thresholds to
 * {@link Integer#MAX_VALUE}, so {@link #shouldEscalate} always returns
 * {@code false} and every failure (typed or untyped) is retried at the plan
 * level (task → pending) — zero regression to the pre-W2-3 undifferentiated
 * behaviour.
 */
public final class FailureEscalationPolicy {

    private final int maxAegisRejections;
    private final int staleTaskMaxRetries;
    private final int maxDispatchRetries;

    /**
     * @param maxAegisRejections  quality-failure threshold per task (must be &gt; 0)
     * @param staleTaskMaxRetries stall-failure threshold per task (must be &gt; 0)
     * @param maxDispatchRetries  infrastructure-failure threshold per task (must be &gt; 0)
     */
    public FailureEscalationPolicy(int maxAegisRejections, int staleTaskMaxRetries, int maxDispatchRetries) {
        if (maxAegisRejections <= 0) {
            throw new IllegalArgumentException("maxAegisRejections must be > 0");
        }
        if (staleTaskMaxRetries <= 0) {
            throw new IllegalArgumentException("staleTaskMaxRetries must be > 0");
        }
        if (maxDispatchRetries <= 0) {
            throw new IllegalArgumentException("maxDispatchRetries must be > 0");
        }
        this.maxAegisRejections = maxAegisRejections;
        this.staleTaskMaxRetries = staleTaskMaxRetries;
        this.maxDispatchRetries = maxDispatchRetries;
    }

    /**
     * The shipped default: all thresholds at {@link Integer#MAX_VALUE}, so no
     * typed failure ever escalates. Every failure is retried at the plan level
     * (task → pending), preserving the pre-W2-3 undifferentiated behaviour.
     */
    public static FailureEscalationPolicy disabled() {
        return new FailureEscalationPolicy(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public int getMaxAegisRejections() {
        return maxAegisRejections;
    }

    public int getStaleTaskMaxRetries() {
        return staleTaskMaxRetries;
    }

    public int getMaxDispatchRetries() {
        return maxDispatchRetries;
    }

    /**
     * Whether the given typed-failure count for this failure type has reached
     * its escalation threshold.
     *
     * @param type        the failure type (non-null)
     * @param currentCount the per-task cumulative count for this type (must be &ge; 0)
     * @return {@code true} if the task should be escalated to {@code failed}
     */
    public boolean shouldEscalate(FailureType type, int currentCount) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (currentCount < 0) {
            throw new IllegalArgumentException("currentCount must be >= 0");
        }
        int threshold;
        switch (type) {
            case QUALITY:
                threshold = maxAegisRejections;
                break;
            case STALL:
                threshold = staleTaskMaxRetries;
                break;
            case INFRASTRUCTURE:
                threshold = maxDispatchRetries;
                break;
            default:
                throw new IllegalArgumentException("Unknown failure type: " + type);
        }
        return currentCount >= threshold;
    }
}
