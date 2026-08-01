package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end <b>wiring verification</b> (Minimum Rules #23 — Anti-Hollow):
 * verifies that {@link AgentPlanValidator} is <b>really hooked into the plan
 * loading path</b> via {@link io.nop.api.core.util.INeedInit#init()} on
 * {@link AgentPlan}.
 *
 * <p>These tests load plans through the full
 * {@link ResourceComponentManager} chain (the real loading path used in
 * production) and assert that:
 * <ul>
 *   <li>A <b>cyclic</b> plan is <b>rejected</b> at load time (not silently
 *       accepted).</li>
 *   <li>A plan with <b>dangling dependsOn</b> is <b>rejected</b> at load
 *       time.</li>
 *   <li>A <b>valid</b> cross-phase plan loads successfully.</li>
 * </ul>
 *
 * <p>If the validator were an isolated component not connected to the loading
 * path, cyclic/dangling plans would load silently — this test would fail.
 */
public class TestAgentPlanValidatorLoading {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testCyclicPlan_rejectedAtLoadTime() {
        Exception ex = assertThrows(Exception.class, () ->
                ResourceComponentManager.instance()
                        .loadComponentModel("/test/test-cyclic-plan.agent-plan.xml"));

        assertTrue(containsCycleMessage(ex),
                "cyclic plan should be rejected at load time; got: " + ex.getMessage());
    }

    @Test
    public void testDanglingDepsPlan_rejectedAtLoadTime() {
        Exception ex = assertThrows(Exception.class, () ->
                ResourceComponentManager.instance()
                        .loadComponentModel("/test/test-dangling-deps-plan.agent-plan.xml"));

        assertTrue(ex.getMessage().contains("dangling") || ex.getMessage().contains("non-existent")
                || containsNopAiAgentException(ex),
                "dangling deps plan should be rejected at load time; got: " + ex.getMessage());
    }

    @Test
    public void testValidCrossPhasePlan_loadsSuccessfully() {
        Object result = ResourceComponentManager.instance()
                .loadComponentModel("/test/test-cross-phase-dag-plan.agent-plan.xml");

        assertNotNull(result);
        assertTrue(result instanceof AgentPlan);

        AgentPlan plan = (AgentPlan) result;
        assertTrue(plan.getPhases().size() >= 2);
    }

    private static boolean containsCycleMessage(Throwable e) {
        while (e != null) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("cycle") || msg.contains("Cycle")
                    || msg.contains("LOOP") || msg.contains("loop"))) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private static boolean containsNopAiAgentException(Throwable e) {
        while (e != null) {
            if (e instanceof NopAiAgentException) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }
}
