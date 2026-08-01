package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanCriterion;
import io.nop.ai.agent.plan.model.AgentPlanGate;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import io.nop.ai.agent.plan.model.GateOnFail;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for {@link PlanExecutor}: the host drives a real plan
 * state machine (consuming {@link PlanScheduler} + {@link PlanRunner}),
 * records real {@code AgentPlanError}-equivalent state, and routes
 * stagnation through {@link StagnationDetector} → {@link PlanReplanner}.
 *
 * <p>These tests satisfy:
 * <ul>
 *   <li><b>End-to-end (Rule #22)</b>: load plan → host drives → inject
 *       stagnation via real state machine → detector event → replanner
 *       decision → observable escalated status.</li>
 *   <li><b>Wiring (Rule #23)</b>: counting spy collaborators verify the
 *       detector is driven by host events and the replanner by detector
 *       events.</li>
 *   <li><b>Idempotency</b>: same stagnation state yields the same decision.</li>
 * </ul>
 */
public class TestPlanExecutorEndToEnd {

    private static AgentPlanTaskModel task(String taskNo, String... dependsOn) {
        AgentPlanTaskModel t = new AgentPlanTaskModel();
        t.setTaskNo(taskNo);
        t.setTitle("Task " + taskNo);
        t.setStatus(AgentExecStatus.pending);
        if (dependsOn != null && dependsOn.length > 0) {
            t.setDependsOn(new HashSet<>(Arrays.asList(dependsOn)));
        }
        return t;
    }

    private static AgentPlan planWithPhases(AgentPlanPhase... phases) {
        AgentPlan plan = new AgentPlan();
        plan.setStatus(AgentExecStatus.pending);
        for (AgentPlanPhase p : phases) {
            plan.addPhase(p);
        }
        return plan;
    }

    private static AgentPlanPhase phase(String name, AgentPlanTaskModel... tasks) {
        AgentPlanPhase p = new AgentPlanPhase();
        p.setName(name);
        p.setStatus(AgentExecStatus.pending);
        for (AgentPlanTaskModel t : tasks) {
            p.addTask(t);
        }
        return p;
    }

    private static AgentPlanCriterion criterion(String id, boolean completed, boolean required, boolean blocking) {
        AgentPlanCriterion c = new AgentPlanCriterion();
        c.setId(id);
        c.setCompleted(completed);
        c.setRequired(required);
        c.setBlocking(blocking);
        return c;
    }

    private static AgentPlanPhase phaseWithFailingGate(String name) {
        AgentPlanPhase p = phase(name, task("A"));
        AgentPlanGate gate = new AgentPlanGate();
        gate.setOnFail(GateOnFail.retry);
        gate.setMaxRetries(1);
        gate.addCriterion(criterion("g1", false, true, false));
        p.setGate(gate);
        return p;
    }

    private static TaskRunner alwaysSuccess() {
        return t -> TaskOutcome.success();
    }

    private static TaskRunner alwaysFail() {
        return t -> TaskOutcome.failure("boom");
    }

    static final class CountingDetector extends StagnationDetector {
        final AtomicInteger detectCalls = new AtomicInteger();

        CountingDetector(int staleTaskCycles, int maxErrorsPerTask) {
            super(staleTaskCycles, maxErrorsPerTask);
        }

        @Override
        public List<StagnationEvent> detect(PlanExecutionState state) {
            detectCalls.incrementAndGet();
            return super.detect(state);
        }
    }

    static final class CountingReplanner extends PlanReplanner {
        final AtomicInteger decideCalls = new AtomicInteger();
        final AtomicInteger applyCalls = new AtomicInteger();

        @Override
        public ReplanDecision decide(List<StagnationEvent> events) {
            decideCalls.incrementAndGet();
            return super.decide(events);
        }

        @Override
        public void apply(ReplanDecision decision, PlanExecutionState state) {
            applyCalls.incrementAndGet();
            super.apply(decision, state);
        }
    }

    @Test
    public void happyPath_allTasksSucceed_planCompletes() {
        AgentPlan plan = planWithPhases(
                phase("P1", task("A"), task("B", "A")),
                phase("P2", task("C")));

        PlanExecutionResult result = new PlanExecutor(alwaysSuccess(),
                new StagnationDetector(3, 3), new PlanReplanner()).execute(plan);

        assertTrue(result.isCompleted());
        assertEquals(3, result.getTasksCompleted());
        assertTrue(result.getEventsObserved().isEmpty());
        assertTrue(result.getDecisionsEnacted().isEmpty());
    }

    @Test
    public void taskStalled_viaRealFailures_escalatesEndToEnd() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        CountingDetector detector = new CountingDetector(2, 10);
        CountingReplanner replanner = new CountingReplanner();

        PlanExecutionResult result = new PlanExecutor(alwaysFail(), detector, replanner).execute(plan);

        assertTrue(result.isEscalated());
        assertTrue(result.getErrorsRecorded() >= 2,
                "host must record real errors; got " + result.getErrorsRecorded());

        Set<StagnationSignalType> signals = new HashSet<>();
        for (StagnationEvent e : result.getEventsObserved()) {
            signals.add(e.getSignalType());
        }
        assertTrue(signals.contains(StagnationSignalType.TASK_STALLED),
                "stagnation must come from real state machine, not synthesized; got " + signals);

        assertEquals(ReplanDecision.ESCALATE, result.getDecisionsEnacted().get(0));
        assertTrue(detector.detectCalls.get() > 0, "detector must be driven by host (wiring)");
        assertTrue(replanner.decideCalls.get() > 0, "replanner must be driven by detector events (wiring)");
        assertTrue(replanner.applyCalls.get() > 0, "decision must be enacted (wiring)");
    }

    @Test
    public void repeatedErrors_viaRealFailures_escalatesEndToEnd() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        CountingDetector detector = new CountingDetector(10, 2);
        CountingReplanner replanner = new CountingReplanner();

        PlanExecutionResult result = new PlanExecutor(alwaysFail(), detector, replanner).execute(plan);

        assertTrue(result.isEscalated());
        assertTrue(result.getErrorsRecorded() >= 2);

        Set<StagnationSignalType> signals = new HashSet<>();
        for (StagnationEvent e : result.getEventsObserved()) {
            signals.add(e.getSignalType());
        }
        assertTrue(signals.contains(StagnationSignalType.REPEATED_ERRORS),
                "expected REPEATED_ERRORS from accumulated runtime errors; got " + signals);
    }

    @Test
    public void gateExhausted_viaRealGateCheck_escalatesEndToEnd() {
        AgentPlan plan = planWithPhases(phaseWithFailingGate("P1"));
        CountingDetector detector = new CountingDetector(5, 5);
        CountingReplanner replanner = new CountingReplanner();

        PlanExecutionResult result = new PlanExecutor(alwaysSuccess(), detector, replanner).execute(plan);

        assertTrue(result.isEscalated(),
                "exhausted gate must escalate; got " + result.getFinalStatus());

        Set<StagnationSignalType> signals = new HashSet<>();
        for (StagnationEvent e : result.getEventsObserved()) {
            signals.add(e.getSignalType());
        }
        assertTrue(signals.contains(StagnationSignalType.GATE_EXHAUSTED),
                "expected GATE_EXHAUSTED from real PlanRunner.checkGate; got " + signals);
        assertTrue(replanner.applyCalls.get() > 0);
    }

    @Test
    public void idempotency_sameStagnationState_sameDecision() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result1 = new PlanExecutor(alwaysFail(),
                new StagnationDetector(2, 10), replanner).execute(plan);
        PlanExecutionResult result2 = new PlanExecutor(alwaysFail(),
                new StagnationDetector(2, 10), replanner).execute(plan);

        assertEquals(result1.getFinalStatus(), result2.getFinalStatus());
        assertEquals(result1.getDecisionsEnacted().get(0), result2.getDecisionsEnacted().get(0));

        List<StagnationEvent> events1 = new ArrayList<>(result1.getEventsObserved());
        List<StagnationEvent> events2 = new ArrayList<>(result2.getEventsObserved());
        assertEquals(replanner.decide(events1), replanner.decide(events2),
                "same stagnation state must yield same decision (idempotency)");
    }

    @Test
    public void dagDependency_respected() {
        AgentPlan plan = planWithPhases(phase("P1",
                task("A"), task("B", "A")));
        AtomicInteger bRuns = new AtomicInteger();
        TaskRunner runner = t -> {
            if ("B".equals(t.getTaskNo())) bRuns.incrementAndGet();
            return TaskOutcome.success();
        };

        PlanExecutionResult result = new PlanExecutor(runner,
                new StagnationDetector(3, 3), new PlanReplanner()).execute(plan);

        assertTrue(result.isCompleted());
        assertEquals(2, result.getTasksCompleted());
        assertEquals(1, bRuns.get(), "B must run exactly once after A completes");
    }

    @Test
    public void doesNotMutateFrozenTemplate_loadedPlanStaysIntact() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        plan.freeze(true);

        PlanExecutionResult result = new PlanExecutor(alwaysFail(),
                new StagnationDetector(2, 10), new PlanReplanner()).execute(plan);

        assertTrue(result.isEscalated());
        assertTrue(plan.frozen(), "loaded template must remain frozen (freeze ruling)");
    }
}
