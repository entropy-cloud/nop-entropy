package io.nop.ai.agent.talent;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.util.List;

/**
 * Dynamic-admission extension point (Solon AI "Talent" pattern).
 *
 * <p>A talent decides, based on the current {@link AgentExecutionContext},
 * whether a context-dependent behavior and its associated tool set should be
 * activated for the current execution. The engine consults registered talents
 * once at execution setup: for each talent whose admission gate
 * ({@link #isSupported}) passes, the engine fires the activation callback
 * ({@link #onAttach}), then merges the talent's dynamic instruction fragment
 * ({@link #getInstruction}) into the system-prompt context and its dynamic tool
 * set ({@link #getTools}) into the active tool definitions sent to the LLM.
 *
 * <p>Design: {@code nop-ai-agent-llm-layer.md} §5.2.
 *
 * <p>Tool-return contract: talents do not invent a parallel tool type. The
 * names returned by {@link #getTools} reference <b>existing registry tools</b>
 * and are resolved through the existing {@code IToolManager.loadTool()}
 * pipeline (reusing schema generation and per-invocation access checks).
 */
public interface ITalent {

    /**
     * Admission gate: returns whether this talent activates for the current
     * execution context. Evaluated once by the engine at execution setup.
     */
    boolean isSupported(AgentExecutionContext ctx);

    /**
     * Activation callback, invoked exactly once after {@link #isSupported}
     * returns {@code true} and before {@link #getInstruction} /
     * {@link #getTools} are collected. Lets a talent register context side
     * effects (e.g. metadata) at attach time.
     */
    void onAttach(AgentExecutionContext ctx);

    /**
     * Dynamic system-prompt instruction fragment contributed by this talent.
     * Returns {@code null} or an empty string when the talent contributes no
     * instruction. Additive to the agent's declared prompt (never replaces it).
     */
    String getInstruction(AgentExecutionContext ctx);

    /**
     * Dynamic tool set contributed by this talent, as a list of registry tool
     * names. Returns an empty list when the talent contributes no tools. The
     * engine resolves each name through {@code IToolManager.loadTool()} and
     * merges the resulting tool definitions (additive to the agent's declared
     * tools), so schemas and access checks apply uniformly.
     */
    List<String> getTools(AgentExecutionContext ctx);
}
