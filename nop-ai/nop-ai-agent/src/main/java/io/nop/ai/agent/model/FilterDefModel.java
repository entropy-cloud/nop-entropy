package io.nop.ai.agent.model;

import io.nop.ai.agent.model._gen._FilterDefModel;

/**
 * W3-2 (declarative filter chain): a named filter definition that maps a
 * filter ID to its {@code IAgentMiddleware} implementation class. Declared
 * inside {@code <filter-chain><filter-definitions>} and referenced by ID from
 * {@code <input-filters>} / {@code <output-filters>}.
 *
 * <p>This is the agent-internal id->impl mapping (D1 option B): self-contained,
 * no IoC container injection required. The impl class is instantiated via
 * {@code ClassHelper.safeNewInstance} at assembly time.
 */
public class FilterDefModel extends _FilterDefModel {
    public FilterDefModel() {
    }
}
