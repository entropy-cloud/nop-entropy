package io.nop.ai.agent.reliability;

/**
 * Session-level progress assessment result returned by
 * {@link IGoalTracker#assessGoal(String)} (design
 * {@code nop-ai-agent-reliability.md} §5.3 / plan 211 / L3-3).
 *
 * <ul>
 *   <li>{@link #PROGRESSING} — the agent is making progress; the ReAct loop
 *       continues normally. This is the default for the shipped
 *       {@link NoOpGoalTracker} (always reports PROGRESSING, zero-regression).
 *       A functional tracker reports PROGRESSING when it sees no evidence of
 *       a stuck/looping pattern.</li>
 *   <li>{@link #STUCK} — a stuck/looping pattern has been detected (e.g. the
 *       same tool-call signature repeats within the sliding window past the
 *       configured threshold). The ReAct loop must abort/escalate rather than
 *       silently continue consuming the iteration budget.</li>
 *   <li>{@link #GOAL_ACHIEVED} — reserved for a future LLM-based progress
 *       assessor that can judge the goal has been reached. Programmatic
 *       detection (like {@code SessionGoalTracker}) cannot determine goal
 *       achievement — that is a semantic judgement — so this value is never
 *       produced by the shipped trackers. It is carried in the contract to
 *       avoid a future breaking change when the LLM-based successor lands.</li>
 * </ul>
 */
public enum GoalAssessment {
    PROGRESSING,
    STUCK,
    GOAL_ACHIEVED
}
