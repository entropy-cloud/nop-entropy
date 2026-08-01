package io.nop.ai.agent.plan.runtime;

import java.util.Objects;

/**
 * An immutable, structured stagnation event produced by
 * {@link StagnationDetector} and consumed by {@link PlanReplanner}
 * (design §14.4.1 / §14.4.2).
 *
 * <p>An event captures: the {@link StagnationSignalType signal type}, the
 * target phase and/or task it refers to, an ordinal {@code count} (cycles,
 * attempts, or unresolved-error count depending on signal), and a
 * human-readable reason.
 *
 * <p><b>Idempotency</b>: {@link #idempotencyKey()} returns a deterministic
 * key composed of <em>structural / ordinal</em> fields only (signal type,
 * target phase, target task, count). Wall-clock timestamps are deliberately
 * excluded, so the same observable stagnation state always maps to the same
 * key — and therefore, since {@link PlanReplanner#decide} is a pure function
 * of the event, the same {@link ReplanDecision} (design §14.4.4).
 */
public final class StagnationEvent {

    private final StagnationSignalType signalType;
    private final String targetPhase;
    private final String targetTaskNo;
    private final int count;
    private final String reason;

    public StagnationEvent(StagnationSignalType signalType, String targetPhase,
                           String targetTaskNo, int count, String reason) {
        if (signalType == null) {
            throw new IllegalArgumentException("signalType must not be null");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        this.signalType = signalType;
        this.targetPhase = targetPhase;
        this.targetTaskNo = targetTaskNo;
        this.count = count;
        this.reason = reason;
    }

    public StagnationSignalType getSignalType() {
        return signalType;
    }

    public String getTargetPhase() {
        return targetPhase;
    }

    public String getTargetTaskNo() {
        return targetTaskNo;
    }

    public int getCount() {
        return count;
    }

    public String getReason() {
        return reason;
    }

    /**
     * Deterministic key over structural / ordinal fields only. Two events
     * describing the same observable stagnation (same signal, same target,
     * same count) always produce the same key, regardless of when they are
     * produced. Used to guarantee idempotent replan decisions.
     */
    public String idempotencyKey() {
        return signalType.name() + "|" + targetPhase + "|" + targetTaskNo + "|" + count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StagnationEvent)) return false;
        StagnationEvent that = (StagnationEvent) o;
        return count == that.count
                && signalType == that.signalType
                && Objects.equals(targetPhase, that.targetPhase)
                && Objects.equals(targetTaskNo, that.targetTaskNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalType, targetPhase, targetTaskNo, count);
    }

    @Override
    public String toString() {
        return "StagnationEvent{signal=" + signalType
                + ", phase=" + targetPhase
                + ", task=" + targetTaskNo
                + ", count=" + count
                + ", reason=" + reason + '}';
    }
}
