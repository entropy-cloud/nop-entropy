package io.nop.ai.agent.reliability;

import java.util.List;

/**
 * Reliability-local data carrier passed to
 * {@link IGoalTracker#recordIteration(String, IterationSnapshot)} once per
 * ReAct iteration, after the LLM response is available and before the
 * tool-dispatch / completion-judge branch (design
 * {@code nop-ai-agent-reliability.md} §5.3 / plan 211 / L3-3).
 *
 * <p>This is a reliability-package-local type — it does not reference
 * {@code AgentExecutionContext} (an engine type), keeping the reliability
 * package self-contained. This mirrors the {@link RetryContext} /
 * {@code Checkpoint} carrier pattern used by the sibling reliability
 * extension points (L3-1 circuit breaker / L3-2 retry / L3-4 checkpoint).
 *
 * <p>Carries:
 * <ul>
 *   <li>{@code iteration} — the zero-based index of the ReAct iteration that
 *       just produced the assistant message (same notion as
 *       {@code ctx.getCurrentIteration()} at the call site).</li>
 *   <li>{@code toolCallSignatures} — the stable signatures of the tool calls
 *       the LLM requested this iteration, in request order. Each signature is
 *       the {@code toolName:stableArgsString} form computed by the engine
 *       from {@code assistantMsg.getToolCalls()} (the args are key-sorted
 *       before serialisation so key-order differences do not produce
 *       different signatures). An empty list means the iteration produced no
 *       tool calls (the completion-judge branch).</li>
 * </ul>
 *
 * <p>This is an immutable data carrier. The engine constructs a fresh
 * instance per iteration.
 */
public final class IterationSnapshot {

    private final int iteration;
    private final List<String> toolCallSignatures;

    public IterationSnapshot(int iteration, List<String> toolCallSignatures) {
        if (iteration < 0) {
            throw new IllegalArgumentException("IterationSnapshot iteration must not be negative: " + iteration);
        }
        if (toolCallSignatures == null) {
            throw new IllegalArgumentException("IterationSnapshot toolCallSignatures must not be null");
        }
        this.iteration = iteration;
        this.toolCallSignatures = List.copyOf(toolCallSignatures);
    }

    public int getIteration() {
        return iteration;
    }

    public List<String> getToolCallSignatures() {
        return toolCallSignatures;
    }
}
