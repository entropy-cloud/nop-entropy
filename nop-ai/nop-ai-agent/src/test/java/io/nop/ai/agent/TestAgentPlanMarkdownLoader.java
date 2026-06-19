package io.nop.ai.agent;

import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
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
 * End-to-end test: load a {@code .agent-plan.md} file via the full
 * {@link ResourceComponentManager} chain (markdown -> record-mapping -> XNode ->
 * xdef validation -> {@link AgentPlan}). This exercises the full path that was
 * broken by the {@code mappingName} hyphen bug (audit 10-01) and the record-mapping
 * field name drift (audit 10-02).
 */
public class TestAgentPlanMarkdownLoader {

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testLoadAgentPlanFromMarkdown() {
        Object result = ResourceComponentManager.instance()
                .loadComponentModel("/test/test-agent-plan.agent-plan.md");

        assertNotNull(result, "loadComponentModel must return a non-null result");
        assertTrue(result instanceof AgentPlan,
                "Expected AgentPlan but got " + result.getClass().getName());

        AgentPlan plan = (AgentPlan) result;
        assertEquals("Test Agent Plan", plan.getTitle());
        assertEquals(io.nop.ai.agent.model.AgentExecStatus.pending, plan.getStatus());
        assertEquals("Verify end-to-end markdown plan loading", plan.getGoal());
        assertNotNull(plan.getPurpose(), "purpose should be loaded from 计划概述");

        assertNotNull(plan.getPhases(), "phases should not be null");
        assertEquals(1, plan.getPhases().size(), "should have exactly one phase");

        AgentPlanPhase phase = plan.getPhases().get(0);
        assertEquals("Phase 1", phase.getName());

        assertNotNull(phase.getTasks(), "phase tasks should not be null");
        assertEquals(1, phase.getTasks().size(), "should have exactly one task");

        AgentPlanTaskModel task = phase.getTasks().get(0);
        assertEquals("T001", task.getTaskNo());
        assertEquals("First task", task.getTitle());
        assertEquals(io.nop.ai.agent.model.AgentExecStatus.pending, task.getStatus());
    }
}
