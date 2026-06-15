package io.nop.ai.agent.router;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

public interface IModelRouter {

    RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx);

    /**
     * Plan 209: query the fallback model for the currently-routed options, used
     * by the ReAct loop's retry branch when {@code RetryDecision.FALLBACK} is
     * returned by the {@code IRetryPolicy} (design
     * {@code nop-ai-agent-llm-layer.md} §6.5). The returned options (when
     * non-null) replace the routed options for the next LLM call attempt;
     * {@code null} means the router has no fallback model available, and the
     * retry loop fails loud (Minimum Rules #24 — no silent skip).
     *
     * <p>The default implementation returns {@code null} (no fallback
     * capability), so {@link PassThroughModelRouter} and other non-functional
     * routers inherit fail-loud semantics without modification. A functional
     * router ({@link SmartModelRouter}) overrides this to return the next model
     * in its configured fallback chain.
     *
     * @param currentOptions the options currently in use (the most recent
     *                       {@code RoutingResult.getOptions()} or a prior
     *                       fallback); identifies the model whose fallback is
     *                       being requested
     * @return fully-usable fallback options (carrying the new provider/model
     *         and preserving the request's tools/settings), or {@code null} if
     *         no fallback model is available / the chain is exhausted
     */
    default ChatOptions getFallback(ChatOptions currentOptions) {
        return null;
    }
}
