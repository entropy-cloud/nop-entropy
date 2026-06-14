package io.nop.ai.agent.talent;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.util.List;

/**
 * Pass-through {@link ITalent} that never activates. Its admission gate
 * ({@link #isSupported}) always returns {@code false}, so the engine never
 * consults {@link #onAttach}, {@link #getInstruction} or {@link #getTools} on
 * the default path. Using it (or registering no talents) leaves engine
 * behaviour unchanged.
 */
public final class NoOpTalent implements ITalent {

    private static final NoOpTalent INSTANCE = new NoOpTalent();

    private NoOpTalent() {
    }

    public static ITalent noOp() {
        return INSTANCE;
    }

    @Override
    public boolean isSupported(AgentExecutionContext ctx) {
        return false;
    }

    @Override
    public void onAttach(AgentExecutionContext ctx) {
        // Never invoked because isSupported always returns false; defined for
        // interface completeness only.
    }

    @Override
    public String getInstruction(AgentExecutionContext ctx) {
        return null;
    }

    @Override
    public List<String> getTools(AgentExecutionContext ctx) {
        return List.of();
    }
}
