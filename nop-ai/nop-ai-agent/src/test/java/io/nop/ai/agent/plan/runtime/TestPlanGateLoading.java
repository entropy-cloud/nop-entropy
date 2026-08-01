package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanGate;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.GateOnFail;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test: load a {@code .agent-plan.xml} file containing a
 * {@code <gate>} element through the full xdef loading chain, then evaluate
 * the gate with {@link PlanRunner}. This verifies that the gate model
 * (design §14.1) is correctly generated from the xdef and that the runtime
 * gate checker can consume it.
 */
public class TestPlanGateLoading {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testLoadPlanWithGate_andCheckGatePassed() {
        Object result = ResourceComponentManager.instance()
                .loadComponentModel("/test/test-gate-plan.agent-plan.xml");

        assertNotNull(result);
        assertTrue(result instanceof AgentPlan);

        AgentPlan plan = (AgentPlan) result;
        assertEquals(1, plan.getPhases().size());

        AgentPlanPhase phase = plan.getPhases().get(0);
        assertEquals("build", phase.getName());

        AgentPlanGate gate = phase.getGate();
        assertNotNull(gate, "gate should be loaded from XML");
        assertEquals(GateOnFail.retry, gate.getOnFail());
        assertEquals(2, gate.getMaxRetries());
        assertTrue(gate.getRequireExplicitVerdict());
        assertTrue(gate.getVerdict());
        assertEquals(2, gate.getCriteria().size());

        GateCheckResult checkResult = new PlanRunner().checkGate(phase);
        assertTrue(checkResult.isPassed(),
                "gate should pass: all required satisfied + verdict=true");
    }
}
