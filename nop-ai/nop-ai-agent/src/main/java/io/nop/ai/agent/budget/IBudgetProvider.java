package io.nop.ai.agent.budget;

import io.nop.ai.agent.engine.AgentExecutionContext;

/**
 * Layer 2 extension point for providing a session-level budget snapshot to
 * functional {@code IModelRouter} implementations (design
 * {@code nop-ai-agent-usage-and-billing.md} §3.6 / plan 206 / L2-22).
 *
 * <p>The ReAct loop calls {@link #getBudget(AgentExecutionContext)} once per
 * iteration, immediately before {@code IModelRouter.route(...)}, and stores the
 * returned snapshot into {@code ctx.setBudgetSnapshot(...)}. A functional
 * router reads {@code ctx.getBudgetSnapshot()} and, when {@code exceeded} is
 * {@code true}, can downgrade the model by returning a {@code RoutingResult}
 * with a cheaper model's options.
 *
 * <p><b>Pass-through semantics of the default</b>: {@link NoOpBudgetProvider}
 * is an <i>explicit</i> pass-through, not a hidden gap. Budget control is not a
 * correctness/safety component — the system runs correctly with no provider (it
 * simply exposes no cost/limit data to the router, so the router never
 * downgrades on budget). Combined with the shipped
 * {@code PassThroughModelRouter} (which never changes the model), the shipped
 * default behaviour is zero-change. Replacing the provider with a functional
 * implementation (e.g. a future {@code DbBudgetProvider}) adds cost-aware
 * routing, not correctness.
 *
 * <p>This interface deliberately holds only {@link #getBudget}. Cost
 * calculation (which requires pricing data from the DB) is the provider's
 * internal responsibility — the agent runtime layer does not perform DB lookups
 * (dependency direction: agent does not depend on service). Placing an
 * unconsumed query method here would violate the no-hollow-contract principle.
 *
 * <p><b>Contract for implementations</b>: {@link #getBudget} must never return
 * {@code null}. A provider that cannot compute a snapshot must return a NoOp
 * equivalent (unlimited) snapshot rather than null, so the router always sees a
 * well-formed snapshot. Implementations that hit an unexpected error should
 * throw {@code NopAiAgentException} rather than swallow it (no silent skip).
 */
public interface IBudgetProvider {

    /**
     * Compute the current session-level budget snapshot. Called once per ReAct
     * iteration, before {@code IModelRouter.route(...)}.
     *
     * @param ctx non-null execution context; the provider may read
     *            {@code sessionId}, {@code getTokensUsed()}, etc. to compute
     *            the snapshot
     * @return a non-null {@link BudgetSnapshot}; never {@code null}
     */
    BudgetSnapshot getBudget(AgentExecutionContext ctx);
}
