package io.nop.ai.agent.plan.model;

/**
 * Action to take when a phase gate fails (design §14.1 Gate 门控).
 * <ul>
 *   <li>{@link #retry} — return to the current phase and retry (bounded by
 *       {@code max-retries}; once exhausted, escalate).</li>
 *   <li>{@link #block} — block all subsequent phases until the gate passes.</li>
 *   <li>{@link #escalate} — set the plan/phase status to
 *       {@link io.nop.ai.agent.model.AgentExecStatus#escalated}.</li>
 * </ul>
 */
public enum GateOnFail {
    retry,

    block,

    escalate
}
