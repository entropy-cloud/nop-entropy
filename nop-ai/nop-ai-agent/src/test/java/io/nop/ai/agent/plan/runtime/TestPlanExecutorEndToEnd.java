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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        CountingReplanner() {
            super();
        }

        CountingReplanner(ReplanPolicy policy) {
            super(policy);
        }

        @Override
        public ReplanDecisionResult decide(List<StagnationEvent> events) {
            decideCalls.incrementAndGet();
            return super.decide(events);
        }

        @Override
        public void apply(ReplanDecisionResult decision, PlanExecutionState state) {
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

        assertEquals(ReplanDecision.ESCALATE, result.getDecisionsEnacted().get(0).getType());
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
        assertEquals(result1.getDecisionsEnacted().get(0).getType(), result2.getDecisionsEnacted().get(0).getType());

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

    // ================= ROLLBACK_PHASE end-to-end =================

    private static ReplanPolicy rollbackTo(String from, String to) {
        Map<String, String> map = new HashMap<>();
        map.put(from, to);
        return new ReplanPolicy(map, Collections.emptyMap());
    }

    private static AgentPlanTaskModel childTask(String taskNo) {
        AgentPlanTaskModel t = new AgentPlanTaskModel();
        t.setTaskNo(taskNo);
        t.setTitle("child " + taskNo);
        return t;
    }

    private static ReplanPolicy splitPolicyFor(String parent, String... children) {
        java.util.List<AgentPlanTaskModel> list = new java.util.ArrayList<>();
        for (String c : children) {
            list.add(childTask(c));
        }
        Map<String, SplitSpec> specs = new HashMap<>();
        specs.put(parent, new SplitSpec(parent, list));
        return new ReplanPolicy(Collections.emptyMap(), specs);
    }

    /**
     * End-to-end ROLLBACK (Rule #22 + #23): a two-phase plan where P2's task
     * stalls, the replanner (rollback policy P2→P1) produces ROLLBACK_PHASE,
     * the executor does NOT terminate — it re-enters P1, re-advances, and on
     * the second visit to P2 the task succeeds (post-rollback flip), so the
     * plan completes. Asserts: decision type ROLLBACK_PHASE recorded, plan
     * completes (not escalated), wiring (apply invoked).
     */
    @Test
    public void rollbackPhase_endToEnd_recoversAndCompletes() {
        AgentPlan plan = planWithPhases(
                phase("P1", task("A")),
                phase("P2", task("B")));
        // Task B fails for the first 2 attempts (drives TASK_STALLED), then succeeds.
        AtomicInteger bAttempts = new AtomicInteger();
        TaskRunner runner = t -> {
            if ("B".equals(t.getTaskNo()) && bAttempts.incrementAndGet() <= 2) {
                return TaskOutcome.failure("stall");
            }
            return TaskOutcome.success();
        };
        StagnationDetector detector = new StagnationDetector(2, 10);
        CountingReplanner replanner = new CountingReplanner(rollbackTo("P2", "P1"));

        PlanExecutionResult result = new PlanExecutor(runner, detector, replanner).execute(plan);

        assertTrue(result.isCompleted(),
                "ROLLBACK must recover and let the plan complete; got " + result.getFinalStatus()
                        + " decisions=" + result.getDecisionsEnacted());
        assertFalse(result.getDecisionsEnacted().isEmpty(),
                "at least one replan decision must be recorded");

        boolean sawRollback = false;
        for (ReplanDecisionResult d : result.getDecisionsEnacted()) {
            if (d.getType() == ReplanDecision.ROLLBACK_PHASE) {
                sawRollback = true;
                assertEquals("P1", d.getTargetPhase(), "rollback target must be P1");
                assertEquals(StagnationSignalType.TASK_STALLED, d.getTriggerSignal());
                assertEquals("B", d.getTargetTaskNo());
            }
        }
        assertTrue(sawRollback, "a ROLLBACK_PHASE decision must have been enacted");
        assertTrue(replanner.applyCalls.get() > 0, "apply must be invoked (wiring)");
        assertTrue(bAttempts.get() >= 3, "B must be re-attempted after the rollback and succeed");
    }

    /**
     * End-to-end GATE_EXHAUSTED → ROLLBACK: P2 has a gate whose criteria are
     * unsatisfied on the first pass (gate exhausts) → ROLLBACK to P1 → on
     * re-entry the gate criteria are programmatically satisfied so P2 passes.
     * This proves ROLLBACK wiring through the gate path.
     */
    @Test
    public void rollbackPhase_viaGateExhaustion_endToEnd() {
        // P2 gate has a single required criterion; we flip it to completed after the first
        // gate-exhaustion so the re-entry passes.
        AgentPlanCriterion crit = criterion("g1", false, true, false);
        AgentPlanPhase p2 = phase("P2", task("B"));
        AgentPlanGate gate = new AgentPlanGate();
        gate.setOnFail(GateOnFail.retry);
        gate.setMaxRetries(1);
        gate.addCriterion(crit);
        p2.setGate(gate);

        AgentPlan plan = planWithPhases(phase("P1", task("A")), p2);
        AtomicInteger aRuns = new AtomicInteger();
        TaskRunner runner = t -> {
            if ("A".equals(t.getTaskNo())) {
                aRuns.incrementAndGet();
            }
            // When P1 is re-run after rollback, satisfy P2's gate criterion.
            if ("A".equals(t.getTaskNo()) && aRuns.get() >= 2 && !crit.getCompleted()) {
                crit.setCompleted(true);
            }
            return TaskOutcome.success();
        };
        CountingReplanner replanner = new CountingReplanner(rollbackTo("P2", "P1"));

        PlanExecutionResult result = new PlanExecutor(runner, new StagnationDetector(5, 5), replanner).execute(plan);

        assertTrue(result.isCompleted(),
                "gate-exhaustion ROLLBACK must recover and complete; got " + result.getFinalStatus()
                        + " decisions=" + result.getDecisionsEnacted());
        assertTrue(result.getDecisionsEnacted().stream()
                        .anyMatch(d -> d.getType() == ReplanDecision.ROLLBACK_PHASE),
                "a ROLLBACK_PHASE decision must be enacted");
        assertTrue(aRuns.get() >= 2, "P1 must be re-run after rollback");
        assertTrue(replanner.applyCalls.get() > 0);
    }

    /**
     * Cycle-safety (Minimum Rules #24-ish / computeSafetyBound reuse): a plan
     * whose rollback policy can never converge (task always stalls, rolls
     * back to its own phase forever) must hit the recovery cycle bound and
     * throw IllegalStateException rather than spin forever.
     */
    @Test
    public void rollbackPhase_infiniteLoop_hitsCycleSafetyBound() {
        // P1 rolls back to itself; task A always fails → TASK_STALLED → ROLLBACK(P1→P1) forever.
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        StagnationDetector detector = new StagnationDetector(1, 10);
        CountingReplanner replanner = new CountingReplanner(rollbackTo("P1", "P1"));

        assertThrows(IllegalStateException.class,
                () -> new PlanExecutor(alwaysFail(), detector, replanner).execute(plan),
                "a non-converging ROLLBACK policy must hit the cycle-safety bound and fail fast");
    }

    /**
     * Zero-regression: a plan with NO rollback policy behaves exactly as
     * before (stall → ESCALATE → terminal), even though the executor now
     * supports recoverable decisions.
     */
    @Test
    public void noRollbackPolicy_stallStillEscalates_zeroRegression() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")), phase("P2", task("B")));
        PlanExecutionResult result = new PlanExecutor(alwaysFail(),
                new StagnationDetector(2, 10), new PlanReplanner()).execute(plan);

        assertTrue(result.isEscalated());
        assertFalse(result.getDecisionsEnacted().stream()
                        .anyMatch(d -> d.getType() == ReplanDecision.ROLLBACK_PHASE
                                || d.getType() == ReplanDecision.SPLIT_TASK),
                "no recoverable decisions without a policy");
    }

    // ================= SPLIT_TASK end-to-end =================

    /**
     * End-to-end SPLIT (Rule #22 + #23): a single-phase plan where task A
     * stalls; the replanner (split policy A→{A.1,A.2}) produces SPLIT_TASK;
     * the executor inserts runtime sub-tasks, the scheduler returns them, the
     * phase filter admits them, and they are scheduled+executed by the stub
     * TaskRunner. Asserts: SPLIT decision recorded, children actually ran
     * (wiring), frozen template unchanged, plan completes.
     */
    @Test
    public void splitTask_endToEnd_subtasksScheduledAndRun() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        // A fails the first attempt (drives TASK_STALLED); once split, children succeed.
        AtomicInteger aRuns = new AtomicInteger();
        Set<String> ran = new HashSet<>();
        TaskRunner runner = t -> {
            String no = t.getTaskNo();
            ran.add(no);
            if ("A".equals(no) && aRuns.incrementAndGet() <= 1) {
                return TaskOutcome.failure("stall");
            }
            return TaskOutcome.success();
        };
        StagnationDetector detector = new StagnationDetector(1, 10);
        CountingReplanner replanner = new CountingReplanner(splitPolicyFor("A", "A.1", "A.2"));

        PlanExecutionResult result = new PlanExecutor(runner, detector, replanner).execute(plan);

        assertTrue(result.isCompleted(),
                "SPLIT must recover and complete; got " + result.getFinalStatus()
                        + " decisions=" + result.getDecisionsEnacted());
        assertTrue(result.getDecisionsEnacted().stream()
                        .anyMatch(d -> d.getType() == ReplanDecision.SPLIT_TASK),
                "a SPLIT_TASK decision must be enacted");
        // Wiring: sub-tasks were actually returned by the scheduler, admitted by the phase
        // filter, and executed by the TaskRunner (not dead nodes).
        assertTrue(ran.contains("A.1"), "sub-task A.1 must be scheduled and run (wiring)");
        assertTrue(ran.contains("A.2"), "sub-task A.2 must be scheduled and run (wiring)");
        // Frozen template unchanged.
        assertEquals(1, plan.getPhases().get(0).getTasks().size(),
                "frozen template task list must be unchanged after SPLIT");
        assertTrue(replanner.applyCalls.get() > 0, "apply must be invoked (wiring)");
    }

    /**
     * Scheduler structural-source verification (anti-hollow): after SPLIT, the
     * scheduler's getReadyTasks returns the runtime sub-task nodes — not just
     * frozen-template tasks.
     */
    @Test
    public void splitTask_schedulerReturnsRuntimeSubtasks() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);
        PlanScheduler sched = new PlanScheduler();

        // Before SPLIT: only A is visible.
        assertEquals(1, sched.getReadyTasks(plan, state.statusProvider(), state.runtimeTaskOverlay()).size());

        // Enact SPLIT manually.
        new PlanReplanner(splitPolicyFor("A", "A.1", "A.2")).apply(
                ReplanDecisionResult.split("A", StagnationSignalType.TASK_STALLED, "r"), state);

        // After SPLIT: A is completed (terminal, excluded); A.1 + A.2 are ready.
        List<AgentPlanTaskModel> ready = sched.getReadyTasks(plan, state.statusProvider(), state.runtimeTaskOverlay());
        Set<String> readyNos = new HashSet<>();
        for (AgentPlanTaskModel t : ready) {
            readyNos.add(t.getTaskNo());
        }
        assertTrue(readyNos.contains("A.1"), "scheduler must return runtime sub-task A.1");
        assertTrue(readyNos.contains("A.2"), "scheduler must return runtime sub-task A.2");
        assertFalse(readyNos.contains("A"), "completed split parent must not be re-returned");
    }

    /**
     * Executor phase-filter verification (anti-hollow): runtime sub-tasks are
     * admitted by readyTasksForPhase (via phaseTaskNos including overlay).
     */
    @Test
    public void splitTask_phaseFilterAdmitsRuntimeSubtasks() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);
        new PlanReplanner(splitPolicyFor("A", "A.1")).apply(
                ReplanDecisionResult.split("A", StagnationSignalType.TASK_STALLED, "r"), state);

        assertTrue(state.phaseTaskNos("P1").contains("A.1"),
                "phaseTaskNos must include runtime sub-tasks so the phase filter admits them");
    }

    /**
     * Zero-regression for SPLIT: scheduler behavior on a plan with no overlay
     * is identical via both overloads (3-arg with empty overlay == 2-arg).
     */
    @Test
    public void splitTask_noOverlay_schedulerZeroRegression() {
        AgentPlan plan = planWithPhases(phase("P1", task("A"), task("B", "A")));
        PlanExecutionState state = new PlanExecutionState(plan);
        PlanScheduler sched = new PlanScheduler();

        List<AgentPlanTaskModel> viaTwoArg = sched.getReadyTasks(plan, state.statusProvider());
        List<AgentPlanTaskModel> viaThreeArg = sched.getReadyTasks(plan, state.statusProvider(),
                state.runtimeTaskOverlay());

        assertEquals(viaTwoArg.size(), viaThreeArg.size(),
                "empty overlay must not change scheduler output (zero regression)");
    }
}
