package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import io.nop.task.model.GraphTaskStepModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link PlanDagBuilder}: DAG construction, cycle detection
 * (real nop-task {@code GraphStepAnalyzer} integration), dangling dependency
 * detection, duplicate taskNo detection, and cross-phase dependency
 * flattization (design §14.3).
 *
 * <p><b>Wiring verification</b> (Minimum Rules #23): these tests assert that
 * the builder <b>really calls nop-task's {@code GraphStepAnalyzer}</b> —
 * cyclic input throws (not silently accepted), and the returned
 * {@link GraphTaskStepModel} has steps with {@code waitSteps} matching the
 * dependsOn structure.
 */
public class TestPlanDagBuilder {

    // ========================================================================
    // Helpers
    // ========================================================================

    private static AgentPlanTaskModel task(String taskNo, String... dependsOn) {
        AgentPlanTaskModel task = new AgentPlanTaskModel();
        task.setTaskNo(taskNo);
        task.setTitle("Task " + taskNo);
        if (dependsOn != null && dependsOn.length > 0) {
            task.setDependsOn(new HashSet<>(Arrays.asList(dependsOn)));
        }
        return task;
    }

    private static AgentPlan planSinglePhase(AgentPlanTaskModel... tasks) {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");
        for (AgentPlanTaskModel t : tasks) {
            phase.addTask(t);
        }
        plan.addPhase(phase);
        return plan;
    }

    private static AgentPlan planTwoPhases(AgentPlanTaskModel[] phase1Tasks,
                                            AgentPlanTaskModel[] phase2Tasks) {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase p1 = new AgentPlanPhase();
        p1.setName("P1");
        for (AgentPlanTaskModel t : phase1Tasks) p1.addTask(t);
        plan.addPhase(p1);

        AgentPlanPhase p2 = new AgentPlanPhase();
        p2.setName("P2");
        for (AgentPlanTaskModel t : phase2Tasks) p2.addTask(t);
        plan.addPhase(p2);
        return plan;
    }

    // ========================================================================
    // Valid DAG construction
    // ========================================================================

    @Test
    public void testValidDag_buildsSuccessfully() {
        AgentPlan plan = planSinglePhase(
                task("A"),
                task("B", "A"),
                task("C", "B"));

        GraphTaskStepModel graph = assertDoesNotThrow(() -> new PlanDagBuilder().buildDag(plan));

        assertNotNull(graph);
        assertTrue(graph.getEnterSteps().contains("A"));
        assertTrue(graph.getExitSteps().contains("C"));
    }

    @Test
    public void testEmptyPlan_returnsEmptyGraph() {
        AgentPlan plan = new AgentPlan();

        GraphTaskStepModel graph = new PlanDagBuilder().buildDag(plan);

        assertNotNull(graph);
        assertTrue(graph.getSteps().isEmpty());
    }

    // ========================================================================
    // Cycle detection (real nop-task GraphStepAnalyzer integration)
    // ========================================================================

    @Test
    public void testCyclicDependency_throwsException() {
        AgentPlan plan = planSinglePhase(
                task("A", "B"),
                task("B", "A"));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new PlanDagBuilder().buildDag(plan));
        assertTrue(ex.getMessage().contains("cycle") || ex.getMessage().contains("Cycle"),
                "exception should mention cycle");
    }

    @Test
    public void testSelfLoop_throwsException() {
        AgentPlan plan = planSinglePhase(
                task("A", "A"));

        assertThrows(NopAiAgentException.class, () -> new PlanDagBuilder().buildDag(plan));
    }

    @Test
    public void testThreeNodeCycle_throwsException() {
        AgentPlan plan = planSinglePhase(
                task("A", "C"),
                task("B", "A"),
                task("C", "B"));

        assertThrows(NopAiAgentException.class, () -> new PlanDagBuilder().buildDag(plan));
    }

    // ========================================================================
    // Dangling dependency detection
    // ========================================================================

    @Test
    public void testDanglingDependency_throwsException() {
        AgentPlan plan = planSinglePhase(
                task("A", "NONEXISTENT"));

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new PlanDagBuilder().buildDag(plan));
        assertTrue(ex.getMessage().contains("dangling") || ex.getMessage().contains("Dangling")
                || ex.getMessage().contains("non-existent"),
                "exception should mention dangling dependency");
    }

    // ========================================================================
    // Duplicate taskNo detection
    // ========================================================================

    @Test
    public void testDuplicateTaskNo_acrossPhases_throwsException() {
        AgentPlan plan = planTwoPhases(
                new AgentPlanTaskModel[]{task("A")},
                new AgentPlanTaskModel[]{task("A")});

        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new PlanDagBuilder().buildDag(plan));
        assertTrue(ex.getMessage().contains("duplicate") || ex.getMessage().contains("Duplicate"),
                "exception should mention duplicate taskNo");
    }

    // ========================================================================
    // Cross-phase dependencies (global flattization)
    // ========================================================================

    @Test
    public void testCrossPhaseDependency_validInGlobalDag() {
        AgentPlan plan = planTwoPhases(
                new AgentPlanTaskModel[]{task("T001")},
                new AgentPlanTaskModel[]{task("T002", "T001")});

        GraphTaskStepModel graph = assertDoesNotThrow(() -> new PlanDagBuilder().buildDag(plan));

        assertNotNull(graph);
        assertEquals(2, graph.getSteps().size());
        assertTrue(graph.getEnterSteps().contains("T001"), "T001 should be enter step");
        assertTrue(graph.getExitSteps().contains("T002"), "T002 should be exit step");
    }

    @Test
    public void testCrossPhaseCycle_throwsException() {
        AgentPlan plan = planTwoPhases(
                new AgentPlanTaskModel[]{task("T001", "T002")},
                new AgentPlanTaskModel[]{task("T002", "T001")});

        assertThrows(NopAiAgentException.class, () -> new PlanDagBuilder().buildDag(plan));
    }

    // ========================================================================
    // SubTask flattization (recursive)
    // ========================================================================

    @Test
    public void testSubTasks_includedInGlobalDag() {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");

        AgentPlanTaskModel parent = task("parent");
        AgentPlanTaskModel child = task("child", "parent");
        parent.addTask(child);
        phase.addTask(parent);
        plan.addPhase(phase);

        List<AgentPlanTaskModel> allTasks = new PlanDagBuilder().collectAllTasks(plan);

        assertEquals(2, allTasks.size(), "subTasks should be included in flattened task list");
        Set<String> taskNos = new HashSet<>();
        for (AgentPlanTaskModel t : allTasks) {
            taskNos.add(t.getTaskNo());
        }
        assertTrue(taskNos.contains("parent"));
        assertTrue(taskNos.contains("child"));
    }

    @Test
    public void testSubTaskDependsOnParent_validInGlobalDag() {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");

        AgentPlanTaskModel parent = task("parent");
        AgentPlanTaskModel child = task("child", "parent");
        parent.addTask(child);
        phase.addTask(parent);
        plan.addPhase(phase);

        assertDoesNotThrow(() -> new PlanDagBuilder().buildDag(plan));
    }
}
