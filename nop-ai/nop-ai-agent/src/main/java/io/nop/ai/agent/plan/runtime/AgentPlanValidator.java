package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.task.model.GraphTaskStepModel;

/**
 * Load-time validator for {@link AgentPlan}. Performs structural validation
 * and DAG cycle detection, following the runtime strong-validation
 * recommendations in design §7.
 *
 * <p><b>Validation checks</b> (fail-fast, no silent acceptance):
 * <ul>
 *   <li>{@code currentPhase} (if set) must exist in {@code phases}.</li>
 *   <li>{@code phase.name} must be unique within the plan.</li>
 *   <li>{@code taskNo} must be globally unique across all phases and
 *       subTasks.</li>
 *   <li>All {@code dependsOn} references must point to existing taskNos
 *       (no dangling dependencies).</li>
 *   <li>The global {@code dependsOn} DAG must not contain cycles (delegated
 *       to nop-task's {@code GraphStepAnalyzer} via {@link PlanDagBuilder}).</li>
 * </ul>
 *
 * <p><b>Wiring</b>: this validator is invoked from
 * {@link AgentPlan#init()} (the {@code INeedInit} hook), which is called
 * automatically by {@code DslModelParser} when an {@code .agent-plan.xml} or
 * {@code .agent-plan.md} file is loaded through
 * {@code ResourceComponentManager}. This means validation runs at load time
 * — invalid plans are rejected before they enter the runtime, not silently
 * accepted.
 */
public class AgentPlanValidator {

    /**
     * Validate an {@link AgentPlan}: structural checks + DAG cycle detection.
     *
     * @param plan the agent plan to validate (non-null)
     * @throws NopAiAgentException if any validation check fails
     */
    public void validate(AgentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        validateCurrentPhase(plan);
        validatePhaseNameUnique(plan);
        validateDag(plan);
    }

    private void validateCurrentPhase(AgentPlan plan) {
        String currentPhase = plan.getCurrentPhase();
        if (currentPhase == null || currentPhase.isEmpty()) {
            return;
        }

        if (plan.getPhases() != null) {
            for (AgentPlanPhase phase : plan.getPhases()) {
                if (currentPhase.equals(phase.getName())) {
                    return;
                }
            }
        }

        throw new NopAiAgentException(
                "nop.ai.agent.plan.invalid-current-phase: currentPhase '" + currentPhase
                        + "' does not match any phase name in the plan");
    }

    private void validatePhaseNameUnique(AgentPlan plan) {
        if (plan.getPhases() == null) return;

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AgentPlanPhase phase : plan.getPhases()) {
            String name = phase.getName();
            if (name != null && !seen.add(name)) {
                throw new NopAiAgentException(
                        "nop.ai.agent.plan.duplicate-phase-name: phase name '" + name
                                + "' appears more than once in the plan");
            }
        }
    }

    private void validateDag(AgentPlan plan) {
        PlanDagBuilder dagBuilder = new PlanDagBuilder();

        GraphTaskStepModel graph = dagBuilder.buildDag(plan);
        if (graph == null) {
            throw new NopAiAgentException(
                    "nop.ai.agent.plan.dag-build-failed: PlanDagBuilder returned null");
        }
    }
}
