package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.plan.model.AgentPlanTaskModel;

/**
 * External callback used by {@link PlanExecutor} to advance a single task
 * (design §14.5 host ruling). The host calls the runner for each ready task
 * and applies the returned {@link TaskOutcome} to the runtime execution
 * state (status transition + error recording on failure).
 *
 * <p>This indirection keeps the minimal plan executor self-contained and
 * testable without depending on the full LLM/agent engine (which has no
 * phase-transition hook today). Production wiring can delegate to the agent
 * engine; tests inject controlled outcomes (e.g. always-fail) to drive real
 * stagnation through the state machine rather than synthesizing events.
 */
@FunctionalInterface
public interface TaskRunner {

    /**
     * Execute a single ready task and report its outcome.
     *
     * @param task the task to advance (non-null); its declared structure is
     *             read-only (the loaded plan is frozen), runtime status is
     *             tracked by the host
     * @return the task outcome; never null
     */
    TaskOutcome run(AgentPlanTaskModel task);
}
