package io.nop.ai.agent.conflict;

import java.util.Objects;

/**
 * Outcome of an {@link IConflictStrategy#resolve} consultation: the
 * decision ({@link ConflictDecision#ALLOW} or {@link ConflictDecision#DENY})
 * plus a human-readable reason and the strategy name that produced it.
 *
 * <p>Use the static factories {@link #allow(String)} and
 * {@link #deny(String, String)} to construct instances; the constructor is
 * public only to keep the type a plain data carrier.
 *
 * <p>Design {@code nop-ai-agent-multi-agent.md} §4.4.
 */
public final class ConflictResult {

    private final ConflictDecision decision;
    private final String reason;
    private final String strategyName;

    public ConflictResult(ConflictDecision decision, String reason, String strategyName) {
        this.decision = decision;
        this.reason = reason;
        this.strategyName = strategyName;
    }

    /**
     * Factory for an allow decision. The reason is left empty (an allow is
     * the no-conflict / accepted case and carries no diagnostic payload).
     *
     * @param strategyName the name of the strategy that produced this result
     *                     (used for audit attribution)
     */
    public static ConflictResult allow(String strategyName) {
        return new ConflictResult(ConflictDecision.ALLOW, null, strategyName);
    }

    /**
     * Factory for a deny decision. The reason must describe the conflict so
     * the agent (and any human reviewing the audit trail) can understand
     * why the write was rejected.
     *
     * @param strategyName the name of the strategy that produced this result
     * @param reason       a non-null human-readable description of the conflict
     */
    public static ConflictResult deny(String strategyName, String reason) {
        return new ConflictResult(ConflictDecision.DENY, reason, strategyName);
    }

    public ConflictDecision getDecision() {
        return decision;
    }

    /**
     * @return {@code true} when {@link #getDecision()} is
     *         {@link ConflictDecision#DENY}; convenience for dispatch-path
     *         callers
     */
    public boolean isDenied() {
        return decision == ConflictDecision.DENY;
    }

    public String getReason() {
        return reason;
    }

    public String getStrategyName() {
        return strategyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConflictResult that = (ConflictResult) o;
        return decision == that.decision
                && Objects.equals(reason, that.reason)
                && Objects.equals(strategyName, that.strategyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, reason, strategyName);
    }

    @Override
    public String toString() {
        return "ConflictResult{decision=" + decision
                + ", reason='" + reason + '\''
                + ", strategyName='" + strategyName + '\'' + '}';
    }
}
