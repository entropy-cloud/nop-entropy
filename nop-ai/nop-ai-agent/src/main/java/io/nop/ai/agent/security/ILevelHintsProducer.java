package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.io.File;
import java.util.Map;

/**
 * Layer 2 contract: runtime production of {@link LevelHints} for a tool call
 * (design §5.1). Given the tool name, the tool-call arguments, the agent's
 * working directory, and the execution context, the producer derives the
 * auditable boolean hints consumed by {@link ISecurityLevelResolver} when
 * resolving the {@link SecurityLevel} of the action.
 *
 * <p>Each hint is produced by a dedicated evaluation strategy:
 *
 * <ul>
 *   <li>{@code trustedSource} — via {@link IContentTrustEvaluator} (the tool
 *       call is produced by the agent's own reasoning chain →
 *       {@link ContentOrigin#AGENT_GENERATED} → trusted under the default
 *       evaluator).</li>
 *   <li>{@code writesOutsideWorkspace} — via path-argument extraction
 *       ({@link ToolPathArgKeys}) and comparison against the working directory
 *       (or JVM CWD when the workDir is absent).</li>
 *   <li>{@code needsNetwork} / {@code highImpact} — via tool-name
 *       classification against known tool categories.</li>
 *   <li>{@code crossesTrustBoundary} — conservatively {@code false} in the
 *       shipped default; precise evaluation needs tool metadata or cross-system
 *       call tracing (future enhancement).</li>
 * </ul>
 *
 * <p><b>Default</b>: {@link DefaultLevelHintsProducer} — a functional
 * implementation that produces semantically-distinct hints (not an all-false
 * stub). It is wired into {@code DefaultAgentEngine} as the shipped default so
 * the resolver receives meaningful hints once the dispatch-path consultation is
 * connected. A custom producer can be registered to override any hint strategy.
 *
 * <p><b>Robustness</b>: implementations must tolerate {@code null} tool name,
 * {@code null} / empty arguments, and {@code null} workDir without throwing —
 * returning a conservative {@link LevelHints} instead.
 */
public interface ILevelHintsProducer {

    /**
     * Produce the {@link LevelHints} for a tool call.
     *
     * @param toolName  the tool name / operation category; may be {@code null} or unknown
     * @param arguments the tool-call arguments; may be {@code null} or empty
     * @param workDir   the agent's working directory, or {@code null} (JVM CWD is used as the base)
     * @param ctx       the execution context; may be {@code null}
     * @return the produced hints; never {@code null}
     */
    LevelHints produce(String toolName, Map<String, Object> arguments, File workDir, AgentExecutionContext ctx);
}
