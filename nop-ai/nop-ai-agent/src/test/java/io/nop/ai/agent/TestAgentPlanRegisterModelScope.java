package io.nop.ai.agent;

import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the scope-consistency fix (plan 275): the production (main) register-model for
 * {@code agent-plan} must not reference test-scope dependencies. Before this fix, the main
 * {@code agent-plan.register-model.xml} declared a {@code .agent-plan.md} loader whose
 * {@code class} pointed to {@code io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory}
 * from the test-scoped {@code nop-record-mapping} module. In production (classpath without
 * {@code nop-record-mapping}), {@code RegisterModelDiscovery} caught the resulting
 * {@code NoClassDefFoundError} and logged {@code nop.register-model.ignore-invalid-loader},
 * silently skipping the loader -- a Silent No-Op.
 *
 * <p>This test asserts the invariant holds against the live classpath: the main register-model
 * resource contains no reference to test-scope classes, while the test-only register-model delta
 * (separate file, same {@code name="agent-plan"}) carries the markdown loader. It also verifies
 * that the production format ({@code .agent-plan.xml}) loads end-to-end.
 */
public class TestAgentPlanRegisterModelScope {

    private static final String MAIN_REGISTER_MODEL = "/nop/core/registry/agent-plan.register-model.xml";
    private static final String TEST_REGISTER_MODEL = "/nop/core/registry/agent-plan-md.register-model.xml";

    @BeforeAll
    public static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMainRegisterModelHasNoTestScopeReferences() {
        IResource mainModel = VirtualFileSystem.instance().getResource(MAIN_REGISTER_MODEL);
        assertTrue(mainModel.exists(),
                "main register-model must exist on classpath: " + MAIN_REGISTER_MODEL);

        String content = ResourceHelper.readText(mainModel);

        assertFalse(content.contains("record_mapping"),
                "main register-model must not reference test-scope nop-record-mapping classes");
        assertFalse(content.contains("MarkdownDslResourceLoaderFactory"),
                "main register-model must not reference test-scope MarkdownDsl loader factory");
        // The invariant: main register-model must contain no plain <loader> element (the only loader
        // type that carries a class= attribute and can thus silently no-op in production when the
        // class is missing). Only framework-provided <xdsl-loader> entries are allowed.
        // "<loader " does not match "<xdsl-loader " since the char after '<' differs.
        assertFalse(content.contains("<loader "),
                "main register-model must not declare any plain <loader> element "
                        + "(only xdsl-loader is allowed; a plain loader referencing a test-scope "
                        + "class would be silently skipped in production)");
    }

    @Test
    public void testTestRegisterModelCarriesMarkdownLoader() {
        IResource testModel = VirtualFileSystem.instance().getResource(TEST_REGISTER_MODEL);
        assertTrue(testModel.exists(),
                "test register-model delta must exist on test classpath: " + TEST_REGISTER_MODEL);

        String content = ResourceHelper.readText(testModel);
        assertTrue(content.contains("MarkdownDslResourceLoaderFactory"),
                "test register-model must declare the markdown loader");
        assertTrue(content.contains("agent-plan.md"),
                "test register-model must register the .agent-plan.md file type");
    }

    @Test
    public void testAgentPlanXmlLoadsInProductionFormat() {
        Object result = ResourceComponentManager.instance()
                .loadComponentModel("/test/test-agent-plan.agent-plan.xml");

        assertNotNull(result, "loadComponentModel must return a non-null result");
        assertTrue(result instanceof AgentPlan,
                "Expected AgentPlan but got " + result.getClass().getName());

        AgentPlan plan = (AgentPlan) result;
        assertEquals("Test Agent Plan XML", plan.getTitle());
        assertEquals(io.nop.ai.agent.model.AgentExecStatus.pending, plan.getStatus());
        assertEquals("Verify production .agent-plan.xml loading", plan.getGoal());
        assertNotNull(plan.getPurpose(), "purpose should be loaded");

        assertNotNull(plan.getPhases(), "phases should not be null");
        assertEquals(1, plan.getPhases().size(), "should have exactly one phase");

        AgentPlanPhase phase = plan.getPhases().get(0);
        assertEquals("Phase 1", phase.getName());

        assertNotNull(phase.getTasks(), "phase tasks should not be null");
        assertEquals(1, phase.getTasks().size(), "should have exactly one task");

        AgentPlanTaskModel task = phase.getTasks().get(0);
        assertEquals("T001", task.getTaskNo());
        assertEquals("First task", task.getTitle());
    }
}
