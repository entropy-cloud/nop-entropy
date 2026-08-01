package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.plan.model.AgentPlanCriterion;
import io.nop.ai.agent.plan.model.AgentPlanGate;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.GateOnFail;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime gate checker for Agent Plan phases (design §14.1 Gate 门控).
 *
 * <p>Evaluates a phase's {@link AgentPlanGate} against the current criterion
 * state and produces a structured {@link GateCheckResult} indicating whether
 * the gate is open, should retry, is blocked, or should escalate.
 *
 * <p><b>Gate judgment semantics</b> (implemented here, tested by
 * {@code TestPlanRunnerGateSemantics}):
 * <ul>
 *   <li>A criterion is "satisfied" when {@code completed == true}.</li>
 *   <li>The gate <b>passes</b> when all {@code required=true} criteria are
 *       satisfied.</li>
 *   <li>An unsatisfied {@code blocking=true} criterion causes a <b>hard
 *       failure</b> regardless of {@code required}.</li>
 *   <li>{@code on-fail=retry}: return to the phase for another attempt
 *       (bounded by {@code max-retries}; once exhausted → escalate).</li>
 *   <li>{@code on-fail=block}: block subsequent phases.</li>
 *   <li>{@code on-fail=escalate}: set status to
 *       {@link io.nop.ai.agent.model.AgentExecStatus#escalated}.</li>
 *   <li>{@code require-explicit-verdict=true}: the gate must NOT auto-pass
 *       on criterion completion alone — an explicit {@code verdict=true}
 *       record is required. Without it the gate returns
 *       {@link GateCheckResult.Outcome#EXPLICIT_VERDICT_REQUIRED}, never a
 *       silent pass (Minimum Rules #24).</li>
 * </ul>
 *
 * <p>This runner is stateless: all state is read from the phase/gate models
 * and the {@code attempt} parameter. The caller is responsible for tracking
 * and incrementing the attempt counter between retries.
 */
public class PlanRunner {

    /**
     * Evaluate the gate of the given phase.
     *
     * @param phase   the phase whose gate to check (non-null)
     * @param attempt the current attempt number (1-based; the first evaluation
     *                is attempt 1). Used to determine whether the retry budget
     *                is exhausted.
     * @return a structured gate-check result; never null
     */
    public GateCheckResult checkGate(AgentPlanPhase phase, int attempt) {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }

        AgentPlanGate gate = phase.getGate();
        if (gate == null) {
            return GateCheckResult.passed();
        }

        GateOnFail onFail = gate.getOnFail();
        if (onFail == null) {
            onFail = GateOnFail.block;
        }

        int maxRetries = gate.getMaxRetries() == null ? 0 : gate.getMaxRetries();

        boolean requireVerdict = Boolean.TRUE.equals(gate.getRequireExplicitVerdict());
        boolean hasVerdict = Boolean.TRUE.equals(gate.getVerdict());

        List<AgentPlanCriterion> unsatisfiedRequired = new ArrayList<>();
        List<AgentPlanCriterion> unsatisfiedBlocking = new ArrayList<>();

        for (AgentPlanCriterion criterion : gate.getCriteria()) {
            boolean satisfied = Boolean.TRUE.equals(criterion.getCompleted());

            if (!satisfied && Boolean.TRUE.equals(criterion.getBlocking())) {
                unsatisfiedBlocking.add(criterion);
            }
            if (!satisfied && Boolean.TRUE.equals(criterion.getRequired())) {
                unsatisfiedRequired.add(criterion);
            }
        }

        boolean hardBlocked = !unsatisfiedBlocking.isEmpty();
        boolean criteriaPass = unsatisfiedRequired.isEmpty() && !hardBlocked;

        if (requireVerdict && !hasVerdict) {
            return GateCheckResult.explicitVerdictRequired(attempt, maxRetries, onFail,
                    unsatisfiedRequired, unsatisfiedBlocking, hardBlocked);
        }

        if (criteriaPass) {
            return GateCheckResult.passed();
        }

        return GateCheckResult.failed(onFail, attempt, maxRetries,
                unsatisfiedRequired, unsatisfiedBlocking, hardBlocked);
    }

    /**
     * Convenience overload: evaluate the gate with attempt = 1.
     */
    public GateCheckResult checkGate(AgentPlanPhase phase) {
        return checkGate(phase, 1);
    }
}
