package io.nop.ai.agent.budget;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable snapshot of a session's budget state, produced by an
 * {@link IBudgetProvider} and consumed by a functional {@code IModelRouter} to
 * decide whether to downgrade the model (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.6 / plan 206 / L2-22).
 *
 * <p><b>Field semantics</b>:
 * <ul>
 *   <li>{@code estimatedTotalCost} (nullable {@link BigDecimal}) — the estimated
 *       cumulative cost of the session so far. {@code null} means cost is not
 *       being tracked (graceful degradation, consistent with the nullable
 *       {@code complexity}/{@code routingReason} on {@code RoutingResult}).</li>
 *   <li>{@code totalTokensUsed} ({@code long}) — the session's cumulative token
 *       usage, sourced from {@code AgentExecutionContext.getTokensUsed()}. A
 *       router that needs token-only budget decisions can read this directly.</li>
 *   <li>{@code budgetLimit} (nullable {@link BigDecimal}) — the configured cost
 *       ceiling. {@code null} means no cost limit is configured (unlimited).</li>
 *   <li>{@code exceeded} ({@code boolean}) — {@code true} iff both
 *       {@code estimatedTotalCost} and {@code budgetLimit} are non-null and
 *       {@code estimatedTotalCost >= budgetLimit}. When either is null,
 *       {@code exceeded} is {@code false}.</li>
 * </ul>
 *
 * <p><b>{@code exceeded} computation</b> is deliberately cost-based only. A
 * token-only budget (cost not tracked) does not set {@code exceeded}; a router
 * that needs token-budget awareness reads {@code totalTokensUsed} (or
 * {@code ctx.getTokensUsed()}) directly. This keeps the {@code exceeded} flag
 * unambiguous and avoids conflating two independent budget dimensions (plan 206
 * §设计裁定 2 / Non-Blocking Follow-up).
 */
public final class BudgetSnapshot {

    private final BigDecimal estimatedTotalCost;
    private final long totalTokensUsed;
    private final BigDecimal budgetLimit;
    private final boolean exceeded;

    /**
     * Full-argument constructor. The {@code exceeded} flag is <b>ignored</b>
     * — it is always recomputed from {@code estimatedTotalCost} and
     * {@code budgetLimit} to guarantee the documented invariant
     * ({@code exceeded == estimatedTotalCost != null && budgetLimit != null
     * && estimatedTotalCost >= budgetLimit}). This prevents a caller from
     * constructing an inconsistent snapshot where {@code exceeded} disagrees
     * with its cost/limit inputs.
     *
     * @param estimatedTotalCost nullable cumulative cost (null = cost not tracked)
     * @param totalTokensUsed    cumulative token usage
     * @param budgetLimit        nullable cost ceiling (null = unlimited)
     */
    public BudgetSnapshot(BigDecimal estimatedTotalCost, long totalTokensUsed, BigDecimal budgetLimit) {
        this.estimatedTotalCost = estimatedTotalCost;
        this.totalTokensUsed = totalTokensUsed;
        this.budgetLimit = budgetLimit;
        this.exceeded = estimatedTotalCost != null && budgetLimit != null
                && estimatedTotalCost.compareTo(budgetLimit) >= 0;
    }

    public BigDecimal getEstimatedTotalCost() {
        return estimatedTotalCost;
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    public BigDecimal getBudgetLimit() {
        return budgetLimit;
    }

    /**
     * @return {@code true} iff the cost budget is exceeded (both cost and limit
     *         are non-null and {@code cost >= limit}); {@code false} otherwise
     *         (including when cost or limit tracking is disabled)
     */
    public boolean isExceeded() {
        return exceeded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BudgetSnapshot that = (BudgetSnapshot) o;
        return totalTokensUsed == that.totalTokensUsed
                && exceeded == that.exceeded
                && Objects.equals(estimatedTotalCost, that.estimatedTotalCost)
                && Objects.equals(budgetLimit, that.budgetLimit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(estimatedTotalCost, totalTokensUsed, budgetLimit, exceeded);
    }

    @Override
    public String toString() {
        return "BudgetSnapshot{estimatedTotalCost=" + estimatedTotalCost
                + ", totalTokensUsed=" + totalTokensUsed
                + ", budgetLimit=" + budgetLimit
                + ", exceeded=" + exceeded + "}";
    }
}
