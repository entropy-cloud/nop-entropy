package io.nop.ai.agent.conflict;

import java.util.Set;

/**
 * Extension point for resolving write conflicts between concurrent agent
 * sessions. The dispatch path (see {@code ReActAgentExecutor}) calls
 * {@link #resolve} when the {@link IWriteIntentRegistry} reports that the
 * current tool call's write intent collides with one or more existing
 * intents on the same file from other sessions.
 *
 * <p>The shipped default is {@link FailFastStrategy}: any cross-session
 * conflict is denied (fail-fast). Alternative strategies (e.g. a future
 * {@code CoordinationBusStrategy} that broadcasts coordination messages to
 * let an LLM adjudicate) can be plugged in via
 * {@code DefaultAgentEngine.setConflictStrategy(...)}.
 *
 * <p>Semantics contract:
 * <ul>
 *   <li>{@code existing} is never {@code null}; an empty set means "no
 *       conflicting intents" and a well-behaved strategy returns
 *       {@link ConflictResult#allow} in that case.</li>
 *   <li>Implementations must be deterministic and side-effect-free with
 *       respect to the registry (the registry's atomicity guarantees are
 *       the caller's responsibility, not the strategy's).</li>
 *   <li>The returned {@link ConflictResult#getStrategyName()} should
 *       identify the strategy for audit attribution.</li>
 * </ul>
 *
 * <p>Design {@code nop-ai-agent-multi-agent.md} §4.4.
 */
public interface IConflictStrategy {

    /**
     * Decide whether the current write intent may proceed given the set of
     * existing (already-registered) write intents on the same file from
     * other sessions.
     *
     * @param current  the write intent being registered by the current tool
     *                 call; never null
     * @param existing the already-registered intents on the same
     *                 {@code filePath} whose {@code sessionId} differs from
     *                 {@code current.sessionId}; never null, possibly empty
     *                 (empty = no conflict)
     * @return the resolution result; never null
     */
    ConflictResult resolve(WriteIntent current, Set<WriteIntent> existing);

    /**
     * @return a short stable name identifying this strategy (used for audit
     *         attribution in {@link ConflictResult#getStrategyName()})
     */
    String name();
}
