package io.nop.ai.agent.quota;

import java.util.Objects;

/**
 * Immutable result of an {@link IResourceGuard#checkConcurrent} adjudication
 * (plan 234, vision §5.2). Carries the allow/deny verdict plus the
 * dimension/scope/limit/projectedCount context that produced it, so an
 * enforcement point can include a precise English reason when it fail-fast
 * throws {@link io.nop.ai.agent.engine.NopAiAgentException} on denial.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@code allowed == true} ⇒ {@code reason == null} (no reason needed
 *       for an allow). {@code limit} and {@code projectedCount} still carry
 *       the resolved values for observability.</li>
 *   <li>{@code allowed == false} ⇒ {@code reason != null} (a deny always
 *       explains why — Minimum Rules #24, no silent deny).</li>
 *   <li>{@code limit} is the limit the guard actually applied: when the
 *       caller-supplied override was {@code > 0}, the override; otherwise the
 *       {@link QuotaConfig} global default for the dimension. {@code <= 0}
 *       means unlimited (the guard returns allow for unlimited).</li>
 * </ul>
 *
 * <p>Use the {@link #allow} / {@link #deny} factories; the constructor is
 * private so the allow⇒null-reason / deny⇒non-null-reason invariant is
 * enforced.
 *
 * <p>See plan 234 (L4-resource-guard-quota), Design Decisions §1 / §3.
 */
public final class QuotaDecision {

    private final boolean allowed;
    private final QuotaDimension dimension;
    private final String scopeKey;
    private final int limit;
    private final int projectedCount;
    private final String reason;

    private QuotaDecision(boolean allowed, QuotaDimension dimension, String scopeKey,
                          int limit, int projectedCount, String reason) {
        this.allowed = allowed;
        this.dimension = dimension;
        this.scopeKey = scopeKey;
        this.limit = limit;
        this.projectedCount = projectedCount;
        this.reason = reason;
    }

    /**
     * Build an allow decision. {@code reason} is forced to {@code null}; the
     * resolved {@code limit} and {@code projectedCount} are carried for
     * observability (e.g. a NoOp allow carries the caller's context verbatim).
     *
     * @param dimension      the quota dimension adjudicated (non-null)
     * @param scopeKey       the scope the decision applies to (e.g. teamId /
     *                       tenant); may be null for NoOp allow
     * @param limit          the resolved limit ({@code <= 0} = unlimited)
     * @param projectedCount the projected post-operation count
     * @return an immutable allow decision (reason is null)
     */
    public static QuotaDecision allow(QuotaDimension dimension, String scopeKey,
                                      int limit, int projectedCount) {
        return new QuotaDecision(true, dimension, scopeKey, limit, projectedCount, null);
    }

    /**
     * Build a deny decision. {@code reason} must be non-null and should
     * include the dimension/scopeKey/limit/projectedCount so the enforcement
     * point can surface a precise English message.
     *
     * @param dimension      the quota dimension adjudicated (non-null)
     * @param scopeKey       the scope the decision applies to
     * @param limit          the resolved limit
     * @param projectedCount the projected post-operation count that exceeded the limit
     * @param reason         human-readable denial reason (non-null)
     * @return an immutable deny decision
     */
    public static QuotaDecision deny(QuotaDimension dimension, String scopeKey,
                                     int limit, int projectedCount, String reason) {
        Objects.requireNonNull(reason, "reason");
        return new QuotaDecision(false, dimension, scopeKey, limit, projectedCount, reason);
    }

    /**
     * @return {@code true} if the projected operation is permitted.
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * @return the quota dimension adjudicated (non-null).
     */
    public QuotaDimension getDimension() {
        return dimension;
    }

    /**
     * @return the scope the decision applies to (e.g. teamId / tenant); may be
     *         null for a NoOp allow with no scope context.
     */
    public String getScopeKey() {
        return scopeKey;
    }

    /**
     * @return the resolved limit the guard applied ({@code <= 0} = unlimited).
     */
    public int getLimit() {
        return limit;
    }

    /**
     * @return the projected post-operation count the guard evaluated.
     */
    public int getProjectedCount() {
        return projectedCount;
    }

    /**
     * @return the denial reason, or {@code null} when {@link #isAllowed()}.
     */
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "QuotaDecision{allowed=" + allowed
                + ", dimension=" + dimension
                + ", scopeKey=" + (scopeKey != null ? "'" + scopeKey + "'" : "null")
                + ", limit=" + limit
                + ", projectedCount=" + projectedCount
                + ", reason=" + (reason != null ? "'" + reason + "'" : "null") + '}';
    }
}
