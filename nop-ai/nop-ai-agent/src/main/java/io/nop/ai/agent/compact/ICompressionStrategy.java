package io.nop.ai.agent.compact;

import io.nop.ai.agent.session.CompactionResult;

/**
 * Pluggable per-layer compression strategy composed by the pipeline orchestrator
 * ({@link PipelineCompactor}).
 * <p>
 * Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md} §7.8.
 * Each strategy implements one escalation layer. The orchestrator runs them in
 * configured order and stops escalating once the context falls below the trigger
 * threshold.
 */
public interface ICompressionStrategy {

    /**
     * Stable identity of this strategy (e.g. "layer1-micro-compression").
     */
    String name();

    /**
     * Apply this layer's compression to the context.
     * <p>
     * Implementations must never fail the agent: when the layer cannot help
     * (e.g. too few messages, LLM unavailable), return an explicit unchanged
     * result and log — never throw, never return a silent no-op.
     */
    CompactionResult compact(CompactionContext ctx);
}
