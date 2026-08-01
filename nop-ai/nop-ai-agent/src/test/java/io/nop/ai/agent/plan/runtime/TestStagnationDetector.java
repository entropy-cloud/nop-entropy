package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanError;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link StagnationDetector} covering each of the
 * three plan-level stagnation signals defined in design §14.4.1.
 */
public class TestStagnationDetector {

    private static AgentPlanTaskModel task(String taskNo) {
        AgentPlanTaskModel t = new AgentPlanTaskModel();
        t.setTaskNo(taskNo);
        t.setTitle("Task " + taskNo);
        return t;
    }

    private static AgentPlan singlePhasePlan(AgentPlanTaskModel... tasks) {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");
        for (AgentPlanTaskModel t : tasks) {
            phase.addTask(t);
        }
        plan.addPhase(phase);
        return plan;
    }

    private static void addUnresolvedError(PlanExecutionState state, String taskNo) {
        AgentPlanError error = new AgentPlanError();
        error.setId("err-" + taskNo + "-" + System.nanoTime());
        error.setRelatedTaskNo(taskNo);
        state.recordError(taskNo, 1, "failure");
    }

    @Test
    public void noStagnation_returnsEmpty() {
        AgentPlan plan = singlePhasePlan(task("A"));
        PlanExecutionState state = new PlanExecutionState(plan);

        List<StagnationEvent> events = new StagnationDetector(3, 3).detect(state);

        assertTrue(events.isEmpty());
    }

    @Test
    public void gateExhausted_emitsGateExhaustedEvent() {
        AgentPlan plan = singlePhasePlan(task("A"));
        PlanExecutionState state = new PlanExecutionState(plan);
        state.markGateExhausted("P1", 3);

        List<StagnationEvent> events = new StagnationDetector(3, 3).detect(state);

        assertEquals(1, events.size());
        assertEquals(StagnationSignalType.GATE_EXHAUSTED, events.get(0).getSignalType());
        assertEquals("P1", events.get(0).getTargetPhase());
        assertEquals(3, events.get(0).getCount());
    }

    @Test
    public void taskStalled_emitsTaskStalledEvent_onlyForNonTerminalTasks() {
        AgentPlan plan = singlePhasePlan(task("A"));
        PlanExecutionState state = new PlanExecutionState(plan);
        state.setTaskStatus("A", AgentExecStatus.pending);
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");

        List<StagnationEvent> events = new StagnationDetector(3, 5).detect(state);

        assertEquals(1, events.size());
        assertEquals(StagnationSignalType.TASK_STALLED, events.get(0).getSignalType());
        assertEquals("A", events.get(0).getTargetTaskNo());
        assertEquals(3, events.get(0).getCount());
    }

    @Test
    public void taskStalled_belowThreshold_isNotStagnation() {
        AgentPlan plan = singlePhasePlan(task("A"));
        PlanExecutionState state = new PlanExecutionState(plan);
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");

        List<StagnationEvent> events = new StagnationDetector(3, 5).detect(state);

        assertTrue(events.isEmpty());
    }

    @Test
    public void taskStalled_terminalTask_isNotStalled() {
        AgentPlan plan = singlePhasePlan(task("A"));
        PlanExecutionState state = new PlanExecutionState(plan);
        state.setTaskStatus("A", AgentExecStatus.failed);
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");

        List<StagnationEvent> events = new StagnationDetector(3, 5).detect(state);

        boolean hasTaskStalled = events.stream()
                .anyMatch(e -> e.getSignalType() == StagnationSignalType.TASK_STALLED);
        assertTrue(!hasTaskStalled, "terminal task must not be reported as TASK_STALLED");
    }

    @Test
    public void repeatedErrors_emitsRepeatedErrorsEvent() {
        AgentPlan plan = singlePhasePlan(task("A"));
        PlanExecutionState state = new PlanExecutionState(plan);
        addUnresolvedError(state, "A");
        addUnresolvedError(state, "A");
        addUnresolvedError(state, "A");

        List<StagnationEvent> events = new StagnationDetector(5, 3).detect(state);

        assertEquals(1, events.size());
        assertEquals(StagnationSignalType.REPEATED_ERRORS, events.get(0).getSignalType());
        assertEquals("A", events.get(0).getTargetTaskNo());
        assertEquals(3, events.get(0).getCount());
    }

    @Test
    public void repeatedErrors_belowThreshold_isNotStagnation() {
        AgentPlan plan = singlePhasePlan(task("A"));
        PlanExecutionState state = new PlanExecutionState(plan);
        addUnresolvedError(state, "A");
        addUnresolvedError(state, "A");

        List<StagnationEvent> events = new StagnationDetector(5, 3).detect(state);

        assertTrue(events.isEmpty());
    }

    @Test
    public void multipleSignals_allReported() {
        AgentPlan plan = singlePhasePlan(task("A"), task("B"));
        PlanExecutionState state = new PlanExecutionState(plan);
        state.markGateExhausted("P1", 2);
        state.setTaskStatus("A", AgentExecStatus.pending);
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");
        addUnresolvedError(state, "B");
        addUnresolvedError(state, "B");
        addUnresolvedError(state, "B");

        List<StagnationEvent> events = new StagnationDetector(3, 3).detect(state);

        Set<StagnationSignalType> types = new HashSet<>();
        for (StagnationEvent e : events) {
            types.add(e.getSignalType());
        }
        assertTrue(types.contains(StagnationSignalType.GATE_EXHAUSTED));
        assertTrue(types.contains(StagnationSignalType.TASK_STALLED));
        assertTrue(types.contains(StagnationSignalType.REPEATED_ERRORS));
    }

    @Test
    public void idempotencyKey_excludesTimeAndReason() {
        StagnationEvent e1 = new StagnationEvent(StagnationSignalType.TASK_STALLED, null, "A", 3, "reason-at-time-T1");
        StagnationEvent e2 = new StagnationEvent(StagnationSignalType.TASK_STALLED, null, "A", 3, "different-reason-at-T2");

        assertEquals(e1.idempotencyKey(), e2.idempotencyKey(),
                "same structural state must yield same idempotency key regardless of reason/time");
        assertEquals(e1, e2);
    }
}
