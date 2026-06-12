package io.nop.ai.agent.engine;

import java.util.concurrent.CompletionStage;

/**
 * Strategy interface for Agent execution modes (ReAct, single-turn, etc.).
 *
 * <p>{@code IAgentEngine} delegates actual execution to an {@code IAgentExecutor}
 * implementation. The executor reads all configuration from the provided
 * {@link AgentExecutionContext} and does not hold mutable state itself.</p>
 *
 * <p>Implementations decide how the agent runs: a single LLM call, a multi-step
 * ReAct loop, or any other execution pattern.</p>
 */
public interface IAgentExecutor {

    /**
     * Execute the agent according to the strategy implemented by this executor.
     *
     * @param ctx the execution context containing agent model, messages, session info, etc.
     * @return a {@link CompletionStage} that completes with the {@link AgentExecutionResult}
     */
    CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx);
}
