package io.nop.ai.agent.reliability;

/**
 * Layer 3 extension point for session-level progress tracking and behaviour
 * fault detection (design {@code nop-ai-agent-reliability.md} §5.3 /
 * plan 211 / L3-3).
 *
 * <p>The ReAct loop's per-iteration boundary interacts with the tracker at
 * two distinct points (plan 211 Phase 1 adjudication):
 * <ul>
 *   <li><b>recordIteration</b> (write side) — called once per iteration,
 *       immediately after the LLM response is available and before the
 *       tool-dispatch / completion-judge branch. The engine builds an
 *       {@link IterationSnapshot} from {@code assistantMsg.getToolCalls()}
 *       (the request-level tool calls, covering both the dispatch and the
 *       no-tool-call paths) and passes it so the tracker can update its
 *       per-session state. A no-tool-call iteration carries an empty
 *       signature list — the tracker treats it as neither progress nor
 *       stuck evidence (window state unchanged).</li>
 *   <li><b>assessGoal</b> (read side) — called at the next iteration's start,
 *       after the force-stop (context-overflow) hard guard and before the
 *       PRE_REASONING hook. A STUCK return causes the ReAct loop to abort
 *       with status {@code escalated} (no silent skip — Minimum Rules #24).
 *       This is the same governance-abort tier as the denial-ledger pause
 *       check.</li>
 * </ul>
 *
 * <p>The two methods are deliberately split into a write side
 * ({@link #recordIteration}) and a read side ({@link #assessGoal}), mirroring
 * the {@link ICircuitBreaker} recordSuccess/recordFailure (write) +
 * allowCall/getState (read) read/write separation.
 *
 * <p>The tracker tracks state per <i>session</i> (the {@code sessionId} from
 * {@code AgentExecutionContext}), so the {@code sessionId} parameter is
 * mandatory on both methods. Anonymous execution ({@code sessionId == null})
 * is supported: a tracker may choose to skip tracking (the shipped
 * {@link NoOpGoalTracker} and the first version of {@code SessionGoalTracker}
 * report PROGRESSING for anonymous sessions, because anonymous execution is
 * a test scenario and production sessions always carry a sessionId).
 *
 * <p><b>Pass-through semantics of the shipped default</b>: {@link NoOpGoalTracker}
 * unconditionally reports {@link GoalAssessment#PROGRESSING} and treats
 * {@link #recordIteration} as an explicit no-op. This preserves the engine's
 * pre-plan-211 behaviour verbatim, so wiring the tracker is a zero-regression
 * change. A functional tracker ({@code SessionGoalTracker}) is registered
 * explicitly via {@code DefaultAgentEngine.setGoalTracker}.
 *
 * <p><b>Contract for implementations</b>: {@link #assessGoal} must never
 * return {@code null}. Implementations must be safe to call from the ReAct
 * loop's execution thread (stateless like {@link NoOpGoalTracker}, or
 * properly synchronised like {@code SessionGoalTracker}).
 */
public interface IGoalTracker {

    /**
     * Update the tracker's per-session state with the iteration that just
     * produced the assistant message. Called by the ReAct loop once per
     * iteration, after the LLM response and before the tool-dispatch /
     * completion-judge branch.
     *
     * @param sessionId the session identity (may be null for anonymous
     *                  execution; a tracker may skip tracking in that case)
     * @param snapshot  non-null per-iteration data carrier
     */
    void recordIteration(String sessionId, IterationSnapshot snapshot);

    /**
     * Assess whether the session is still progressing, stuck, or has reached
     * the goal. Called by the ReAct loop at the iteration start, after the
     * force-stop hard guard and before the PRE_REASONING hook. A STUCK return
     * aborts the loop with status {@code escalated}.
     *
     * @param sessionId the session identity (may be null for anonymous
     *                  execution)
     * @return a non-null {@link GoalAssessment}; never {@code null}
     */
    GoalAssessment assessGoal(String sessionId);
}
