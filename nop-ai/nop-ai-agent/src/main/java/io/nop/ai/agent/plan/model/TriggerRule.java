package io.nop.ai.agent.plan.model;

/**
 * Trigger rule that determines when a task becomes ready based on its
 * dependencies' states (design §14.2 Trigger Rule).
 * <ul>
 *   <li>{@link #all_success} — ready when <b>all</b> dependencies are
 *       {@link io.nop.ai.agent.model.AgentExecStatus#completed}. Default.</li>
 *   <li>{@link #one_success} — ready when <b>any one</b> dependency is
 *       {@link io.nop.ai.agent.model.AgentExecStatus#completed}.</li>
 *   <li>{@link #none_failed_min_one_success} — ready when at least one
 *       dependency is {@link io.nop.ai.agent.model.AgentExecStatus#completed}
 *       and none are {@link io.nop.ai.agent.model.AgentExecStatus#failed}.</li>
 *   <li>{@link #all_done} — ready when all dependencies are in a terminal
 *       state (completed or failed), regardless of outcome.</li>
 * </ul>
 */
public enum TriggerRule {
    all_success,

    one_success,

    none_failed_min_one_success,

    all_done
}
