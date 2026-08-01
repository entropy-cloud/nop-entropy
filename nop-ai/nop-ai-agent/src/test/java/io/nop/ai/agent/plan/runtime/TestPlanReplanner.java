package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link PlanReplanner}: decision mapping (now a
 * result object with payload), policy-driven ROLLBACK_PHASE triggers +
 * real enactment, idempotency (pure function), and fast-fail for ABORT
 * (Minimum Rules #24 — no silent skip).
 */
public class TestPlanReplanner {

    private static AgentPlan singlePhasePlan() {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase phase = new AgentPlanPhase();
        phase.setName("P1");
        AgentPlanTaskModel task = new AgentPlanTaskModel();
        task.setTaskNo("A");
        phase.addTask(task);
        plan.addPhase(phase);
        return plan;
    }

    private static AgentPlan twoPhasePlan() {
        AgentPlan plan = new AgentPlan();
        AgentPlanPhase p1 = new AgentPlanPhase();
        p1.setName("P1");
        AgentPlanTaskModel a = new AgentPlanTaskModel();
        a.setTaskNo("A");
        p1.addTask(a);
        plan.addPhase(p1);
        AgentPlanPhase p2 = new AgentPlanPhase();
        p2.setName("P2");
        AgentPlanTaskModel b = new AgentPlanTaskModel();
        b.setTaskNo("B");
        p2.addTask(b);
        plan.addPhase(p2);
        return plan;
    }

    private static ReplanPolicy rollbackPolicy(String from, String to) {
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

    private static ReplanPolicy splitPolicy(String parent, String... childNos) {
        Map<String, SplitSpec> specs = new HashMap<>();
        java.util.List<AgentPlanTaskModel> children = new java.util.ArrayList<>();
        for (String no : childNos) {
            children.add(childTask(no));
        }
        specs.put(parent, new SplitSpec(parent, children));
        return new ReplanPolicy(Collections.emptyMap(), specs);
    }

    // ---------------- decision mapping (default escalate-only policy) ----------------

    @Test
    public void gateExhausted_decidesEscalate_byDefault() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.GATE_EXHAUSTED, "P1", null, 2, "r");

        ReplanDecisionResult r = new PlanReplanner().decide(event);

        assertEquals(ReplanDecision.ESCALATE, r.getType());
        assertEquals("P1", r.getTargetPhase());
        assertEquals(StagnationSignalType.GATE_EXHAUSTED, r.getTriggerSignal());
    }

    @Test
    public void taskStalled_decidesEscalate_byDefault() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.TASK_STALLED, "P1", "A", 3, "r");

        ReplanDecisionResult r = new PlanReplanner().decide(event);

        assertEquals(ReplanDecision.ESCALATE, r.getType());
        assertEquals("A", r.getTargetTaskNo());
    }

    @Test
    public void repeatedErrors_decidesEscalate_byDefault() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.REPEATED_ERRORS, "P1", "A", 3, "r");

        assertEquals(ReplanDecision.ESCALATE, new PlanReplanner().decide(event).getType());
    }

    @Test
    public void emptyBatch_decidesContinue() {
        ReplanDecisionResult r1 = new PlanReplanner().decide(Collections.emptyList());
        ReplanDecisionResult r2 = new PlanReplanner().decide((List<StagnationEvent>) null);

        assertEquals(ReplanDecision.CONTINUE, r1.getType());
        assertEquals(ReplanDecision.CONTINUE, r2.getType());
        assertNull(r1.getTargetPhase());
    }

    @Test
    public void nonEmptyBatch_decidesEscalate_byDefault() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.TASK_STALLED, "P1", "A", 3, "r");

        assertEquals(ReplanDecision.ESCALATE, new PlanReplanner().decide(Arrays.asList(event)).getType());
    }

    @Test
    public void decision_isIdempotent_sameEventSameResult() {
        PlanReplanner replanner = new PlanReplanner();
        StagnationEvent event = new StagnationEvent(StagnationSignalType.REPEATED_ERRORS, "P1", "A", 5, "r1");

        ReplanDecisionResult d1 = replanner.decide(event);
        ReplanDecisionResult d2 = replanner.decide(event);
        ReplanDecisionResult d3 = replanner.decide(
                new StagnationEvent(StagnationSignalType.REPEATED_ERRORS, "P1", "A", 5, "different reason"));

        assertEquals(d1, d2, "same event must always yield same decision result");
        assertEquals(d1, d3, "same structural state (same idempotency key) must yield same decision result");
        assertEquals(d1.idempotencyKey(), d3.idempotencyKey());
        assertEquals(ReplanDecision.ESCALATE, d1.getType());
    }

    // ---------------- policy-driven ROLLBACK_PHASE decision ----------------

    @Test
    public void gateExhausted_onRollbackEligiblePhase_decidesRollback() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.GATE_EXHAUSTED, "P2", null, 2, "r");
        PlanReplanner replanner = new PlanReplanner(rollbackPolicy("P2", "P1"));

        ReplanDecisionResult r = replanner.decide(event);

        assertEquals(ReplanDecision.ROLLBACK_PHASE, r.getType());
        assertEquals("P1", r.getTargetPhase(), "rollback target must be the registered preceding phase");
        assertEquals(StagnationSignalType.GATE_EXHAUSTED, r.getTriggerSignal());
        assertTrue(r.isRecoverable());
        assertFalse(r.isTerminal());
    }

    @Test
    public void taskStalled_onRollbackEligiblePhase_decidesRollback() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.TASK_STALLED, "P2", "B", 3, "r");
        PlanReplanner replanner = new PlanReplanner(rollbackPolicy("P2", "P1"));

        ReplanDecisionResult r = replanner.decide(event);

        assertEquals(ReplanDecision.ROLLBACK_PHASE, r.getType());
        assertEquals("P1", r.getTargetPhase());
        assertEquals("B", r.getTargetTaskNo());
    }

    @Test
    public void repeatedErrors_onRollbackEligiblePhase_decidesRollback() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.REPEATED_ERRORS, "P2", "B", 5, "r");
        PlanReplanner replanner = new PlanReplanner(rollbackPolicy("P2", "P1"));

        assertEquals(ReplanDecision.ROLLBACK_PHASE, replanner.decide(event).getType());
    }

    @Test
    public void rollbackDecision_isIdempotent() {
        PlanReplanner replanner = new PlanReplanner(rollbackPolicy("P2", "P1"));
        StagnationEvent e = new StagnationEvent(StagnationSignalType.GATE_EXHAUSTED, "P2", null, 2, "r");

        ReplanDecisionResult d1 = replanner.decide(e);
        ReplanDecisionResult d2 = replanner.decide(e);

        assertEquals(d1, d2);
        assertEquals(d1.idempotencyKey(), d2.idempotencyKey());
    }

    @Test
    public void batch_escalateBeatsRollback_onPrecedence() {
        // One event maps to ROLLBACK, another (non-eligible phase) to ESCALATE -> batch escalates.
        StagnationEvent rollback = new StagnationEvent(StagnationSignalType.GATE_EXHAUSTED, "P2", null, 2, "r");
        StagnationEvent escalate = new StagnationEvent(StagnationSignalType.TASK_STALLED, "P1", "A", 3, "r");
        PlanReplanner replanner = new PlanReplanner(rollbackPolicy("P2", "P1"));

        ReplanDecisionResult r = replanner.decide(Arrays.asList(rollback, escalate));

        assertEquals(ReplanDecision.ESCALATE, r.getType(), "ESCALATE must win over ROLLBACK on precedence");
    }

    // ---------------- enactment ----------------

    @Test
    public void apply_escalate_setsStatusEscalated() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());

        new PlanReplanner().apply(ReplanDecisionResult.escalate(
                StagnationSignalType.TASK_STALLED, "P1", null, "r"), state);

        assertEquals(AgentExecStatus.escalated, state.getPlanStatus());
        assertEquals(AgentExecStatus.escalated, state.getPhaseStatus("P1"));
    }

    @Test
    public void apply_continue_isNoOp() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());
        state.setPlanStatus(AgentExecStatus.running);

        assertDoesNotThrow(() -> new PlanReplanner().apply(ReplanDecisionResult.continueResult(), state));

        assertEquals(AgentExecStatus.running, state.getPlanStatus(),
                "CONTINUE must not mutate state");
    }

    @Test
    public void apply_rollbackPhase_enactsRealRollback() {
        AgentPlan plan = twoPhasePlan();
        PlanExecutionState state = new PlanExecutionState(plan);
        // Simulate P1 completed, P2 stalled.
        state.setTaskStatus("A", AgentExecStatus.completed);
        state.setPhaseStatus("P1", AgentExecStatus.completed);
        state.setCurrentPhase("P2");
        state.setPhaseStatus("P2", AgentExecStatus.escalated);
        state.incrementConsecutiveFailures("B");
        state.incrementConsecutiveFailures("B");
        state.recordError("B", 1, "boom");
        state.recordError("B", 2, "boom2");
        state.markGateExhausted("P2", 2);

        ReplanDecisionResult decision = ReplanDecisionResult.rollback(
                "P1", "B", StagnationSignalType.TASK_STALLED, "rollback P2 -> P1");
        new PlanReplanner(rollbackPolicy("P2", "P1")).apply(decision, state);

        // Target phase (P1) reset: A back to pending, phase status pending.
        assertEquals(AgentExecStatus.pending, state.getTaskStatus("A"),
                "ROLLBACK must reset target-phase completed tasks to pending");
        assertEquals(AgentExecStatus.pending, state.getPhaseStatus("P1"));
        // Source phase (P2) stagnation state cleared: errors resolved, counters zeroed, gate marker cleared.
        assertEquals(0, state.countUnresolvedErrors("B"),
                "ROLLBACK must resolve source-phase task errors (first writer of resolvedAt)");
        assertEquals(0, state.getConsecutiveFailures("B"),
                "ROLLBACK must zero source-phase consecutive-failure counters");
        assertFalse(state.isGateExhausted("P2"),
                "ROLLBACK must clear the source-phase gate-exhausted marker (break detect loop)");
        assertFalse(state.getErrors().isEmpty());
        assertNotNull(state.getErrors().get(0).getResolvedAt(),
                "AgentPlanError.resolvedAt must be written by ROLLBACK (first business writer)");
        // currentPhase moved back.
        assertEquals("P1", state.getCurrentPhase());
    }

    @Test
    public void apply_rollbackPhase_unknownTarget_throwsNoSilentSkip() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());
        ReplanDecisionResult decision = ReplanDecisionResult.rollback(
                "NOPE", null, StagnationSignalType.GATE_EXHAUSTED, "r");

        assertThrows(IllegalArgumentException.class,
                () -> new PlanReplanner().apply(decision, state),
                "ROLLBACK to an unknown phase must fail fast, not silently no-op");
    }

    @Test
    public void taskStalled_onSplitEligibleTask_decidesSplit() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.TASK_STALLED, "P1", "A", 3, "r");
        PlanReplanner replanner = new PlanReplanner(splitPolicy("A", "A.1", "A.2"));

        ReplanDecisionResult r = replanner.decide(event);

        assertEquals(ReplanDecision.SPLIT_TASK, r.getType());
        assertEquals("A", r.getTargetTaskNo());
        assertEquals(StagnationSignalType.TASK_STALLED, r.getTriggerSignal());
        assertTrue(r.isRecoverable());
    }

    @Test
    public void apply_splitTask_enactsRealSplit() {
        AgentPlan plan = singlePhasePlan();
        PlanExecutionState state = new PlanExecutionState(plan);
        // Simulate parent A stalled.
        state.setTaskStatus("A", AgentExecStatus.pending);
        state.incrementConsecutiveFailures("A");
        state.incrementConsecutiveFailures("A");
        state.recordError("A", 1, "boom");

        ReplanDecisionResult decision = ReplanDecisionResult.split(
                "A", StagnationSignalType.TASK_STALLED, "split A");
        new PlanReplanner(splitPolicy("A", "A.1", "A.2")).apply(decision, state);

        // Parent marked split + completed; errors resolved; counters zeroed.
        assertTrue(state.isSplitParent("A"), "parent must be marked as a split placeholder");
        assertEquals(AgentExecStatus.completed, state.getTaskStatus("A"),
                "split parent is treated as completed so it does not re-stall");
        assertEquals(0, state.countUnresolvedErrors("A"));
        assertEquals(0, state.getConsecutiveFailures("A"));
        // Children registered as runtime overlay under the parent's phase (P1), pending.
        assertTrue(state.isRuntimeTask("A.1"));
        assertTrue(state.isRuntimeTask("A.2"));
        assertEquals(AgentExecStatus.pending, state.getTaskStatus("A.1"));
        assertEquals(AgentExecStatus.pending, state.getTaskStatus("A.2"));
        assertEquals("P1", state.phaseOwningTask("A.1"));
        assertTrue(state.phaseTaskNos("P1").contains("A.1"),
                "phase filter must admit runtime sub-tasks");
        // Frozen template is not mutated: original task list unchanged.
        assertEquals(1, plan.getPhases().get(0).getTasks().size(),
                "frozen template task list must not be mutated by SPLIT");
    }

    @Test
    public void apply_splitTask_withoutSpec_throwsNoSilentSkip() {
        // No SplitSpec registered for the task → programming error, fail fast.
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());
        ReplanDecisionResult decision = ReplanDecisionResult.split(
                "A", StagnationSignalType.TASK_STALLED, "r");
        assertThrows(IllegalStateException.class,
                () -> new PlanReplanner().apply(decision, state),
                "SPLIT without a registered spec must fail fast, not silently no-op");
    }

    @Test
    public void apply_abort_throwsUnsupported_noSilentSkip() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> new PlanReplanner().apply(ReplanDecisionResult.abort("r"), state));
        assertTrue(ex.getMessage().contains("ABORT"));
    }

    @Test
    public void decide_neverProducesAbort() {
        // ABORT enactment is out-of-scope; decide() must never produce it for any signal.
        PlanReplanner replanner = new PlanReplanner(rollbackPolicy("P2", "P1"));
        for (StagnationSignalType s : StagnationSignalType.values()) {
            StagnationEvent e = new StagnationEvent(s, "P2", "B", 1, "r");
            assertTrue(replanner.decide(e).getType() != ReplanDecision.ABORT,
                    "decide() must never produce ABORT for signal " + s);
        }
    }

    @Test
    public void decideAndApply_enactsAndReturnsResult() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());
        PlanReplanner replanner = new PlanReplanner();

        ReplanDecisionResult r = replanner.decideAndApply(
                Collections.singletonList(new StagnationEvent(StagnationSignalType.TASK_STALLED, "P1", "A", 3, "r")),
                state);

        assertEquals(ReplanDecision.ESCALATE, r.getType());
        assertEquals(AgentExecStatus.escalated, state.getPlanStatus());
    }
}
