package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for {@link PlanReplanner}: decision mapping,
 * idempotency (pure function), and fast-fail enactment for the
 * defined-but-deferred decisions (Minimum Rules #24 — no silent skip).
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

    @Test
    public void gateExhausted_decidesEscalate() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.GATE_EXHAUSTED, "P1", null, 2, "r");

        assertEquals(ReplanDecision.ESCALATE, new PlanReplanner().decide(event));
    }

    @Test
    public void taskStalled_decidesEscalate() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.TASK_STALLED, null, "A", 3, "r");

        assertEquals(ReplanDecision.ESCALATE, new PlanReplanner().decide(event));
    }

    @Test
    public void repeatedErrors_decidesEscalate() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.REPEATED_ERRORS, null, "A", 3, "r");

        assertEquals(ReplanDecision.ESCALATE, new PlanReplanner().decide(event));
    }

    @Test
    public void emptyBatch_decidesContinue() {
        assertEquals(ReplanDecision.CONTINUE, new PlanReplanner().decide(Collections.emptyList()));
        assertEquals(ReplanDecision.CONTINUE,
                new PlanReplanner().decide((List<StagnationEvent>) null));
    }

    @Test
    public void nonEmptyBatch_decidesEscalate() {
        StagnationEvent event = new StagnationEvent(StagnationSignalType.TASK_STALLED, null, "A", 3, "r");

        assertEquals(ReplanDecision.ESCALATE, new PlanReplanner().decide(Arrays.asList(event)));
    }

    @Test
    public void decision_isIdempotent_sameEventSameDecision() {
        PlanReplanner replanner = new PlanReplanner();
        StagnationEvent event = new StagnationEvent(StagnationSignalType.REPEATED_ERRORS, null, "A", 5, "r1");

        ReplanDecision d1 = replanner.decide(event);
        ReplanDecision d2 = replanner.decide(event);
        ReplanDecision d3 = replanner.decide(new StagnationEvent(StagnationSignalType.REPEATED_ERRORS, null, "A", 5, "different reason"));

        assertEquals(d1, d2, "same event must always yield same decision");
        assertEquals(d1, d3, "same structural state (same idempotency key) must yield same decision");
        assertEquals(ReplanDecision.ESCALATE, d1);
    }

    @Test
    public void apply_escalate_setsStatusEscalated() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());

        new PlanReplanner().apply(ReplanDecision.ESCALATE, state);

        assertEquals(AgentExecStatus.escalated, state.getPlanStatus());
        assertEquals(AgentExecStatus.escalated, state.getPhaseStatus("P1"));
    }

    @Test
    public void apply_continue_isNoOp() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());
        state.setPlanStatus(AgentExecStatus.running);

        assertDoesNotThrow(() -> new PlanReplanner().apply(ReplanDecision.CONTINUE, state));

        assertEquals(AgentExecStatus.running, state.getPlanStatus(),
                "CONTINUE must not mutate state");
    }

    @Test
    public void apply_rollbackPhase_throwsUnsupported_noSilentSkip() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> new PlanReplanner().apply(ReplanDecision.ROLLBACK_PHASE, state));
        assertTrue(ex.getMessage().contains("ROLLBACK_PHASE"));
    }

    @Test
    public void apply_splitTask_throwsUnsupported_noSilentSkip() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> new PlanReplanner().apply(ReplanDecision.SPLIT_TASK, state));
        assertTrue(ex.getMessage().contains("SPLIT_TASK"));
    }

    @Test
    public void apply_abort_throwsUnsupported_noSilentSkip() {
        PlanExecutionState state = new PlanExecutionState(singlePhasePlan());

        assertThrows(UnsupportedOperationException.class,
                () -> new PlanReplanner().apply(ReplanDecision.ABORT, state));
    }
}
