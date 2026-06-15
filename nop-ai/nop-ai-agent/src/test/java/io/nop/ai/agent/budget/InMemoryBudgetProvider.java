package io.nop.ai.agent.budget;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Test-only {@link IBudgetProvider} with a configurable cost limit and an
 * internally-maintained {@code estimatedTotalCost} (plan 206 Phase 2 / L2-22).
 *
 * <p>Used by budget-control focused tests and the end-to-end budget-downgrade
 * test to drive a functional {@code IModelRouter} into a model-downgrade
 * decision without requiring a DB-backed cost source.
 *
 * <p><b>Usage</b>:
 * <ul>
 *   <li>{@link #InMemoryBudgetProvider(BigDecimal)} — configure the cost
 *       ceiling. {@code null} means unlimited (no limit tracking).</li>
 *   <li>{@link #addCost(BigDecimal)} — manually accumulate cost (e.g. simulate
 *       per-LLM-call cost accrual between iterations).</li>
 *   <li>{@link #getBudget(AgentExecutionContext)} — returns the current
 *       snapshot, with {@code exceeded} computed from the running cost vs the
 *       configured limit.</li>
 * </ul>
 *
 * <p>When {@code budgetLimit} is {@code null}, the snapshot's
 * {@code exceeded} is always {@code false} (no limit configured), matching the
 * {@code BudgetSnapshot} contract. When {@code estimatedTotalCost} has not been
 * accumulated (still {@code null}) but a limit is configured, the snapshot
 * reports {@code estimatedTotalCost=null} and {@code exceeded=false} (cost not
 * yet tracked).
 *
 * <p>Thread-safety: cost accumulation is synchronized so the provider can be
 * shared across concurrent test threads if needed.
 */
public final class InMemoryBudgetProvider implements IBudgetProvider {

    private final BigDecimal budgetLimit;
    private BigDecimal estimatedTotalCost;

    public InMemoryBudgetProvider(BigDecimal budgetLimit) {
        this.budgetLimit = budgetLimit;
        this.estimatedTotalCost = null;
    }

    /**
     * Manually add to the running estimated cost. The first non-null add sets
     * the running total; subsequent adds accumulate. Passing {@code null} is a
     * no-op (cost stays untracked if it was never set).
     *
     * @param delta the cost increment (non-null to accumulate)
     */
    public synchronized void addCost(BigDecimal delta) {
        if (delta == null) {
            return;
        }
        if (estimatedTotalCost == null) {
            estimatedTotalCost = delta;
        } else {
            estimatedTotalCost = estimatedTotalCost.add(delta);
        }
    }

    /**
     * Directly set the running estimated cost. Useful for test setup where the
     * exact cumulative cost must be controlled (e.g. set just below / at the
     * limit).
     *
     * @param cost the new running cost ({@code null} resets to "not tracked")
     */
    public synchronized void setCost(BigDecimal cost) {
        this.estimatedTotalCost = cost;
    }

    public synchronized BigDecimal getEstimatedTotalCost() {
        return estimatedTotalCost;
    }

    public BigDecimal getBudgetLimit() {
        return budgetLimit;
    }

    @Override
    public BudgetSnapshot getBudget(AgentExecutionContext ctx) {
        long tokens = ctx != null ? ctx.getTokensUsed() : 0L;
        BigDecimal cost;
        synchronized (this) {
            cost = estimatedTotalCost;
        }
        return new BudgetSnapshot(cost, tokens, budgetLimit);
    }

    @Override
    public String toString() {
        return "InMemoryBudgetProvider{budgetLimit=" + budgetLimit
                + ", estimatedTotalCost=" + estimatedTotalCost + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InMemoryBudgetProvider that = (InMemoryBudgetProvider) o;
        return Objects.equals(budgetLimit, that.budgetLimit)
                && Objects.equals(estimatedTotalCost, that.estimatedTotalCost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(budgetLimit, estimatedTotalCost);
    }
}
