package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import io.nop.ai.agent.plan.model.TriggerRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link PlanScheduler} covering all 4 trigger rules
 * defined in plan W1-2 (design §14.2 Trigger Rule).
 *
 * <p>Each trigger rule is tested with scenarios that verify the ready-task
 * computation:
 * <ul>
 *   <li>{@code all_success} — ready only when all deps are completed</li>
 *   <li>{@code one_success} — ready when any one dep is completed</li>
 *   <li>{@code none_failed_min_one_success} — ready when ≥1 completed and 0 failed</li>
 *   <li>{@code all_done} — ready when all deps are in terminal state</li>
 * </ul>
 */
public class TestPlanScheduler {

    // ========================================================================
    // Helpers
    // ========================================================================

    private static AgentPlanTaskModel task(String taskNo, AgentExecStatus status,
                                            TriggerRule trigger, String... dependsOn) {
        AgentPlanTaskModel task = new AgentPlanTaskModel();
        task.setTaskNo(taskNo);
        task.setTitle("Task " + taskNo);
        task.setStatus(status);
        if (trigger != null) {
            task.setTriggerRule(trigger);
        }
        if (dependsOn != null && dependsOn.length > 0) {
            Set<String> deps = new HashSet<>(Arrays.asList(dependsOn));
            task.setDependsOn(deps);
        }
        return task;
    }

    private static AgentPlan plan(AgentPlanTaskModel... tasks) {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");
        for (AgentPlanTaskModel t : tasks) {
            phase.addTask(t);
        }
        plan.addPhase(phase);
        return plan;
    }

    private static Set<String> taskNos(List<AgentPlanTaskModel> tasks) {
        return tasks.stream().map(AgentPlanTaskModel::getTaskNo).collect(Collectors.toSet());
    }

    // ========================================================================
    // all_success (default)
    // ========================================================================

    @Test
    public void testAllSuccess_allDepsCompleted_meansReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.completed, null),
                task("C", AgentExecStatus.pending, TriggerRule.all_success, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(taskNos(ready).contains("C"), "C should be ready: all deps completed");
    }

    @Test
    public void testAllSuccess_someDepNotCompleted_meansNotReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.running, null),
                task("C", AgentExecStatus.pending, TriggerRule.all_success, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(!taskNos(ready).contains("C"), "C should NOT be ready: B not completed");
    }

    @Test
    public void testAllSuccess_defaultTriggerWhenNotSpecified() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.pending, null, "A"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(taskNos(ready).contains("B"), "B should be ready: A completed, default=all_success");
    }

    // ========================================================================
    // one_success
    // ========================================================================

    @Test
    public void testOneSuccess_anyDepCompleted_meansReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.failed, null),
                task("C", AgentExecStatus.pending, TriggerRule.one_success, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(taskNos(ready).contains("C"), "C should be ready: at least one dep (A) completed");
    }

    @Test
    public void testOneSuccess_noneCompleted_meansNotReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.running, null),
                task("B", AgentExecStatus.failed, null),
                task("C", AgentExecStatus.pending, TriggerRule.one_success, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(!taskNos(ready).contains("C"), "C should NOT be ready: no dep completed");
    }

    // ========================================================================
    // none_failed_min_one_success
    // ========================================================================

    @Test
    public void testNoneFailedMinOneSuccess_oneCompletedNoFailed_meansReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.running, null),
                task("C", AgentExecStatus.pending, TriggerRule.none_failed_min_one_success, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(taskNos(ready).contains("C"), "C should be ready: ≥1 completed, 0 failed");
    }

    @Test
    public void testNoneFailedMinOneSuccess_hasFailed_meansNotReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.failed, null),
                task("C", AgentExecStatus.pending, TriggerRule.none_failed_min_one_success, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(!taskNos(ready).contains("C"), "C should NOT be ready: B failed");
    }

    // ========================================================================
    // all_done
    // ========================================================================

    @Test
    public void testAllDone_allTerminal_meansReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.failed, null),
                task("C", AgentExecStatus.pending, TriggerRule.all_done, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(taskNos(ready).contains("C"), "C should be ready: all deps terminal");
    }

    @Test
    public void testAllDone_someNotTerminal_meansNotReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.running, null),
                task("C", AgentExecStatus.pending, TriggerRule.all_done, "A", "B"));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(!taskNos(ready).contains("C"), "C should NOT be ready: B not terminal");
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    public void testNoDeps_meansReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.pending, null),
                task("B", AgentExecStatus.pending, null));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertEquals(2, ready.size(), "both tasks with no deps should be ready");
    }

    @Test
    public void testTerminalTasks_notIncludedInReady() {
        AgentPlan plan = plan(
                task("A", AgentExecStatus.completed, null),
                task("B", AgentExecStatus.failed, null),
                task("C", AgentExecStatus.pending, null));

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertEquals(1, ready.size());
        assertEquals("C", ready.get(0).getTaskNo());
    }

    @Test
    public void testEmptyPlan_returnsEmptyReady() {
        AgentPlan plan = new AgentPlan();

        List<AgentPlanTaskModel> ready = new PlanScheduler().getReadyTasks(plan);

        assertTrue(ready.isEmpty());
    }
}
