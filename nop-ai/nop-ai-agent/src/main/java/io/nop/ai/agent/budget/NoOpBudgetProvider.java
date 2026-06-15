package io.nop.ai.agent.budget;

import io.nop.ai.agent.engine.AgentExecutionContext;

/**
 * Pass-through {@link IBudgetProvider} used as the shipped default when no
 * functional provider is registered (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.6 default / plan 206 Phase 1).
 *
 * <p>{@link #getBudget} returns an <b>unlimited</b> snapshot:
 * {@code estimatedTotalCost=null}, {@code budgetLimit=null},
 * {@code exceeded=false}, and {@code totalTokensUsed} sourced from the context
 * (so the token figure stays consistent with {@code ctx.getTokensUsed()} even
 * though cost/limit are disabled).
 *
 * <p>This is an <b>explicit pass-through default</b>, not a silent skip of
 * required behaviour: budget control is not a correctness/safety component, so
 * the system runs correctly with no provider — it simply exposes no cost/limit
 * data to the router, so a functional router never downgrades on budget.
 * Combined with the shipped {@code PassThroughModelRouter} (which never changes
 * the model), the shipped default behaviour is zero-change. A functional
 * provider is registered explicitly via
 * {@code DefaultAgentEngine.setBudgetProvider}. This mirrors the
 * {@code NoOpUsageRecorder} / {@code NoOpCheckpoint} / {@code NoOpContextCompactor}
 * sibling pass-through pattern.
 *
 * <p>This implementation is stateless and therefore inherently thread-safe.
 */
public final class NoOpBudgetProvider implements IBudgetProvider {

    private static final NoOpBudgetProvider INSTANCE = new NoOpBudgetProvider();

    private NoOpBudgetProvider() {
    }

    /**
     * @return the singleton pass-through {@link IBudgetProvider} instance
     */
    public static IBudgetProvider noOp() {
        return INSTANCE;
    }

    @Override
    public BudgetSnapshot getBudget(AgentExecutionContext ctx) {
        long tokens = ctx != null ? ctx.getTokensUsed() : 0L;
        return new BudgetSnapshot(null, tokens, null);
    }
}
