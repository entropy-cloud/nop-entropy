package io.nop.ai.agent.security;

import java.util.Objects;
import io.nop.ai.agent.engine.NopAiAgentException;

/**
 * The composite return value of {@link IDenialLedger#recordDenial}: the
 * cumulative per-session denial count after recording this denial, together
 * with whether the denial threshold has been reached. Follows the same
 * immutable value-object pattern as {@link MatrixDecision} and
 * {@link ApprovalDecision}: factory construction, all-field equals/hashCode,
 * no mutators.
 *
 * <p>The dispatch path records a denial, then inspects
 * {@code thresholdExceeded} to decide whether to abort the dispatch loop and
 * mark the session as paused (design §6.2).
 */
public final class DenialRecordOutcome {

    private final int count;
    private final boolean thresholdExceeded;

    private DenialRecordOutcome(int count, boolean thresholdExceeded) {
        this.count = count;
        this.thresholdExceeded = thresholdExceeded;
    }

    /**
     * @param count             the cumulative per-session denial count after
     *                          recording this denial (never negative)
     * @param thresholdExceeded whether the denial threshold has been reached
     *                          for this session
     */
    public static DenialRecordOutcome of(int count, boolean thresholdExceeded) {
        if (count < 0) {
            throw new NopAiAgentException(
                    "DenialRecordOutcome.count must not be negative, got: " + count);
        }
        return new DenialRecordOutcome(count, thresholdExceeded);
    }

    /**
     * @return the cumulative per-session denial count after this denial
     */
    public int getCount() {
        return count;
    }

    /**
     * @return {@code true} when the session has reached the denial threshold
     *         and should be paused
     */
    public boolean isThresholdExceeded() {
        return thresholdExceeded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DenialRecordOutcome that = (DenialRecordOutcome) o;
        return count == that.count && thresholdExceeded == that.thresholdExceeded;
    }

    @Override
    public int hashCode() {
        return Objects.hash(count, thresholdExceeded);
    }

    @Override
    public String toString() {
        return "DenialRecordOutcome{" +
                "count=" + count +
                ", thresholdExceeded=" + thresholdExceeded +
                '}';
    }
}
