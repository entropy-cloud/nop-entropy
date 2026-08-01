package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.plan.model.AgentPlanCriterion;
import io.nop.ai.agent.plan.model.AgentPlanGate;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.GateOnFail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link PlanRunner#checkGate} covering every judgment
 * branch defined in plan W1-1 (design §14.1 Gate 门控).
 *
 * <p>These tests drive plan phase instances through the gate evaluation and
 * verify the structured {@link GateCheckResult} for each semantic branch:
 * <ul>
 *   <li>All required satisfied → PASSED</li>
 *   <li>Required unsatisfied → fail (per on-fail action)</li>
 *   <li>Blocking unsatisfied → hard failure</li>
 *   <li>on-fail=retry → RETRY (attempt+1); at max-retries → RETRY_EXHAUSTED</li>
 *   <li>on-fail=block → BLOCKED</li>
 *   <li>on-fail=escalate → ESCALATED</li>
 *   <li>require-explicit-verdict=true, no verdict → EXPLICIT_VERDICT_REQUIRED (no silent pass)</li>
 * </ul>
 */
public class TestPlanRunnerGateSemantics {

    // ========================================================================
    // Helpers
    // ========================================================================

    private static AgentPlanCriterion criterion(String id, boolean completed,
                                                 boolean required, boolean blocking) {
        AgentPlanCriterion c = new AgentPlanCriterion();
        c.setId(id);
        c.setCompleted(completed);
        c.setRequired(required);
        c.setBlocking(blocking);
        return c;
    }

    private static AgentPlanGate gate(GateOnFail onFail, Integer maxRetries,
                                       Boolean requireVerdict, Boolean verdict,
                                       AgentPlanCriterion... criteria) {
        AgentPlanGate gate = new AgentPlanGate();
        gate.setOnFail(onFail);
        if (maxRetries != null) gate.setMaxRetries(maxRetries);
        if (requireVerdict != null) gate.setRequireExplicitVerdict(requireVerdict);
        if (verdict != null) gate.setVerdict(verdict);
        for (AgentPlanCriterion c : criteria) {
            gate.addCriterion(c);
        }
        return gate;
    }

    private static AgentPlanPhase phaseWithGate(AgentPlanGate gate) {
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");
        phase.setGate(gate);
        return phase;
    }

    // ========================================================================
    // PASSED cases
    // ========================================================================

    @Test
    public void testNoGate_meansPassed() {
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");

        GateCheckResult result = new PlanRunner().checkGate(phase);

        assertTrue(result.isPassed());
        assertEquals(GateCheckResult.Outcome.PASSED, result.getOutcome());
    }

    @Test
    public void testAllRequiredSatisfied_meansPassed() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, null, null,
                criterion("c1", true, true, false),
                criterion("c2", true, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertTrue(result.isPassed());
    }

    @Test
    public void testOptionalCriterionUnsatisfied_meansPassed() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, null, null,
                criterion("c1", true, true, false),
                criterion("c2", false, false, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertTrue(result.isPassed());
    }

    // ========================================================================
    // Required unsatisfied → fail per on-fail
    // ========================================================================

    @Test
    public void testRequiredUnsatisfied_onFailRetry_meansRetry() {
        AgentPlanGate gate = gate(GateOnFail.retry, 3, null, null,
                criterion("c1", false, true, false));

        PlanRunner runner = new PlanRunner();

        GateCheckResult r1 = runner.checkGate(phaseWithGate(gate), 1);
        assertEquals(GateCheckResult.Outcome.RETRY, r1.getOutcome());
        assertFalse(r1.isPassed());
        assertEquals(1, r1.getUnsatisfiedRequired().size());

        GateCheckResult r2 = runner.checkGate(phaseWithGate(gate), 2);
        assertEquals(GateCheckResult.Outcome.RETRY, r2.getOutcome());
    }

    @Test
    public void testRequiredUnsatisfied_onFailRetry_atMaxRetries_meansExhausted() {
        AgentPlanGate gate = gate(GateOnFail.retry, 2, null, null,
                criterion("c1", false, true, false));

        PlanRunner runner = new PlanRunner();

        assertEquals(GateCheckResult.Outcome.RETRY, runner.checkGate(phaseWithGate(gate), 1).getOutcome());
        assertEquals(GateCheckResult.Outcome.RETRY, runner.checkGate(phaseWithGate(gate), 2).getOutcome());
        assertEquals(GateCheckResult.Outcome.RETRY_EXHAUSTED, runner.checkGate(phaseWithGate(gate), 3).getOutcome());
    }

    @Test
    public void testRequiredUnsatisfied_onFailBlock_meansBlocked() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, null, null,
                criterion("c1", false, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertEquals(GateCheckResult.Outcome.BLOCKED, result.getOutcome());
        assertFalse(result.isPassed());
    }

    @Test
    public void testRequiredUnsatisfied_onFailEscalate_meansEscalated() {
        AgentPlanGate gate = gate(GateOnFail.escalate, 0, null, null,
                criterion("c1", false, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertEquals(GateCheckResult.Outcome.ESCALATED, result.getOutcome());
        assertFalse(result.isPassed());
    }

    // ========================================================================
    // Blocking unsatisfied → hard failure (regardless of required)
    // ========================================================================

    @Test
    public void testBlockingUnsatisfied_meansHardBlocked() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, null, null,
                criterion("c1", true, true, false),
                criterion("c2", false, false, true));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertEquals(GateCheckResult.Outcome.BLOCKED, result.getOutcome());
        assertTrue(result.isHardBlocked());
        assertFalse(result.isPassed());
        assertEquals(1, result.getUnsatisfiedBlocking().size());
    }

    @Test
    public void testBlockingUnsatisfied_withEscalate() {
        AgentPlanGate gate = gate(GateOnFail.escalate, 0, null, null,
                criterion("c1", false, false, true));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertEquals(GateCheckResult.Outcome.ESCALATED, result.getOutcome());
        assertTrue(result.isHardBlocked());
    }

    // ========================================================================
    // require-explicit-verdict = true → no silent auto-pass
    // ========================================================================

    @Test
    public void testRequireExplicitVerdict_noVerdict_noSilentPass() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, true, null,
                criterion("c1", true, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertEquals(GateCheckResult.Outcome.EXPLICIT_VERDICT_REQUIRED, result.getOutcome());
        assertFalse(result.isPassed());
    }

    @Test
    public void testRequireExplicitVerdict_verdictFalse_noSilentPass() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, true, false,
                criterion("c1", true, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertEquals(GateCheckResult.Outcome.EXPLICIT_VERDICT_REQUIRED, result.getOutcome());
        assertFalse(result.isPassed());
    }

    @Test
    public void testRequireExplicitVerdict_verdictTrue_passesWhenCriteriaMet() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, true, true,
                criterion("c1", true, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertTrue(result.isPassed());
    }

    @Test
    public void testRequireExplicitVerdict_verdictTrue_butCriteriaNotMet_stillFails() {
        AgentPlanGate gate = gate(GateOnFail.block, 0, true, true,
                criterion("c1", false, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertFalse(result.isPassed());
        assertEquals(GateCheckResult.Outcome.BLOCKED, result.getOutcome());
    }

    @Test
    public void testRequireExplicitVerdict_noVerdict_reportsUnsatisfiedCriteria() {
        AgentPlanGate gate = gate(GateOnFail.retry, 2, true, null,
                criterion("c1", false, true, false),
                criterion("c2", false, false, true));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate), 1);

        assertEquals(GateCheckResult.Outcome.EXPLICIT_VERDICT_REQUIRED, result.getOutcome());
        assertEquals(1, result.getUnsatisfiedRequired().size());
        assertEquals(1, result.getUnsatisfiedBlocking().size());
        assertTrue(result.isHardBlocked());
    }

    // ========================================================================
    // Default on-fail when not specified
    // ========================================================================

    @Test
    public void testDefaultOnFail_isBlock() {
        AgentPlanGate gate = new AgentPlanGate();
        gate.addCriterion(criterion("c1", false, true, false));

        GateCheckResult result = new PlanRunner().checkGate(phaseWithGate(gate));

        assertEquals(GateCheckResult.Outcome.BLOCKED, result.getOutcome());
    }
}
