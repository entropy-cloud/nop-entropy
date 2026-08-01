package io.nop.ai.agent.model;

import io.nop.ai.agent.model._gen._AgentFilterChainModel;

/**
 * W3-2 (declarative filter chain): the declarative guardrail pipeline for an
 * agent. Holds three ordered lists:
 * <ul>
 *   <li>{@code filter-definitions} — the agent-internal id->impl mapping
 *       (D1 option B, self-contained).</li>
 *   <li>{@code input-filters} — request-side guardrails (default PRE_CALL).</li>
 *   <li>{@code output-filters} — response-side guardrails (default POST_CALL).</li>
 * </ul>
 *
 * <p>At assembly time {@code AgentExecutorResolver} resolves every filter ref
 * to an {@link io.nop.ai.agent.middleware.IAgentMiddleware} instance (fast-fail
 * on unknown ID) and produces a {@code ResolvedFilterChain} that keeps the
 * declarative filter IDs and the resolved middleware objects in sync
 * (auditable / serializable).
 */
public class AgentFilterChainModel extends _AgentFilterChainModel {
    public AgentFilterChainModel() {
    }

    /**
     * @return true when this chain declares any filter-definitions or any
     *         input/output filter refs (i.e. assembly must process it).
     */
    public boolean hasAnyFilters() {
        return hasFilterDefinitions()
                || (getInputFilters() != null && !getInputFilters().isEmpty())
                || (getOutputFilters() != null && !getOutputFilters().isEmpty());
    }
}
