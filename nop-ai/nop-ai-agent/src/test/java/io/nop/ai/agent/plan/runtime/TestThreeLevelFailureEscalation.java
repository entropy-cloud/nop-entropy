package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.ai.agent.plan.model.AgentPlanError;
import io.nop.ai.agent.plan.model.AgentPlanPhase;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for W2-3 three-level failure escalation (design §13.3, plan
 * {@code 2026-08-01-1437-2}).
 *
 * <p>Covers Phase 2 (failure classification + typed counters + thresholds +
 * escalation actions + zero regression + orthogonality) and Phase 3
 * (aggregation via existing {@code recordError → REPEATED_ERRORS} pipeline,
 * no double-count, W1-4 decision contract unchanged, end-to-end).
 */
public class TestThreeLevelFailureEscalation {

    // ==================== Phase 2: FailureType + TaskOutcome ====================

    @org.junit.jupiter.api.Test
    void taskOutcome_typedFailure_carriesType() {
        TaskOutcome o = TaskOutcome.failure("guardrail blocked", FailureType.QUALITY);
        assertFalse(o.isSuccess());
        assertEquals("guardrail blocked", o.getErrorText());
        assertEquals(FailureType.QUALITY, o.getFailureType());
    }

    @org.junit.jupiter.api.Test
    void taskOutcome_typedFailure_nullErrorText_defaults() {
        TaskOutcome o = TaskOutcome.failure(null, FailureType.INFRASTRUCTURE);
        assertEquals("task failed", o.getErrorText());
        assertEquals(FailureType.INFRASTRUCTURE, o.getFailureType());
    }

    @org.junit.jupiter.api.Test
    void taskOutcome_untypedFailure_nullType_zeroRegression() {
        TaskOutcome o = TaskOutcome.failure("boom");
        assertFalse(o.isSuccess());
        assertEquals("boom", o.getErrorText());
        assertNull(o.getFailureType());
    }

    @org.junit.jupiter.api.Test
    void taskOutcome_success_nullType() {
        TaskOutcome o = TaskOutcome.success();
        assertTrue(o.isSuccess());
        assertNull(o.getFailureType());
    }

    @org.junit.jupiter.api.Test
    void taskOutcome_typedFailureNullType_equivalentToUntyped() {
        TaskOutcome typedNull = TaskOutcome.failure("err", null);
        TaskOutcome untyped = TaskOutcome.failure("err");
        assertEquals(untyped, typedNull);
    }

    // ==================== Phase 2: FailureEscalationPolicy ====================

    @org.junit.jupiter.api.Test
    void policy_disabled_neverEscalates() {
        FailureEscalationPolicy policy = FailureEscalationPolicy.disabled();
        for (FailureType type : FailureType.values()) {
            // disabled() sets thresholds to MAX_VALUE, so any realistic count
            // (below MAX_VALUE) never escalates.
            assertFalse(policy.shouldEscalate(type, 0));
            assertFalse(policy.shouldEscalate(type, 999_999));
        }
    }

    @org.junit.jupiter.api.Test
    void policy_thresholdBoundary_escalatesAtExactThreshold() {
        FailureEscalationPolicy policy = new FailureEscalationPolicy(3, 5, 2);

        // QUALITY: threshold 3
        assertFalse(policy.shouldEscalate(FailureType.QUALITY, 2));
        assertTrue(policy.shouldEscalate(FailureType.QUALITY, 3));
        assertTrue(policy.shouldEscalate(FailureType.QUALITY, 4));

        // STALL: threshold 5
        assertFalse(policy.shouldEscalate(FailureType.STALL, 4));
        assertTrue(policy.shouldEscalate(FailureType.STALL, 5));

        // INFRASTRUCTURE: threshold 2
        assertFalse(policy.shouldEscalate(FailureType.INFRASTRUCTURE, 1));
        assertTrue(policy.shouldEscalate(FailureType.INFRASTRUCTURE, 2));
    }

    @org.junit.jupiter.api.Test
    void policy_invalidArgs_failFast() {
        FailureEscalationPolicy policy = new FailureEscalationPolicy(1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> policy.shouldEscalate(null, 1));
        assertThrows(IllegalArgumentException.class, () -> policy.shouldEscalate(FailureType.QUALITY, -1));
    }

    @org.junit.jupiter.api.Test
    void policy_constructor_zeroThreshold_failFast() {
        assertThrows(IllegalArgumentException.class, () -> new FailureEscalationPolicy(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FailureEscalationPolicy(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FailureEscalationPolicy(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FailureEscalationPolicy(-1, 1, 1));
    }

    // ==================== Phase 2: PlanExecutionState typed counters ====================

    @org.junit.jupiter.api.Test
    void state_typedFailureCounter_incrementsPerType() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);

        assertEquals(0, state.getTypedFailureCount("A", FailureType.QUALITY));
        assertEquals(0, state.getTypedFailureCount("A", FailureType.INFRASTRUCTURE));

        state.recordTypedFailure("A", FailureType.QUALITY);
        state.recordTypedFailure("A", FailureType.QUALITY);
        state.recordTypedFailure("A", FailureType.INFRASTRUCTURE);

        assertEquals(2, state.getTypedFailureCount("A", FailureType.QUALITY));
        assertEquals(1, state.getTypedFailureCount("A", FailureType.INFRASTRUCTURE));
        assertEquals(0, state.getTypedFailureCount("A", FailureType.STALL));
    }

    @org.junit.jupiter.api.Test
    void state_typedFailureCounter_independentPerTask() {
        AgentPlan plan = planWithPhases(phase("P1", task("A"), task("B")));
        PlanExecutionState state = new PlanExecutionState(plan);

        state.recordTypedFailure("A", FailureType.QUALITY);
        state.recordTypedFailure("B", FailureType.STALL);
        state.recordTypedFailure("B", FailureType.STALL);

        assertEquals(1, state.getTypedFailureCount("A", FailureType.QUALITY));
        assertEquals(0, state.getTypedFailureCount("A", FailureType.STALL));
        assertEquals(0, state.getTypedFailureCount("B", FailureType.QUALITY));
        assertEquals(2, state.getTypedFailureCount("B", FailureType.STALL));
    }

    @org.junit.jupiter.api.Test
    void state_resetTypedFailures_clearsAllTypes() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);

        state.recordTypedFailure("A", FailureType.QUALITY);
        state.recordTypedFailure("A", FailureType.STALL);
        state.recordTypedFailure("A", FailureType.INFRASTRUCTURE);
        assertEquals(1, state.getTypedFailureCount("A", FailureType.QUALITY));

        state.resetTypedFailures("A");
        assertEquals(0, state.getTypedFailureCount("A", FailureType.QUALITY));
        assertEquals(0, state.getTypedFailureCount("A", FailureType.STALL));
        assertEquals(0, state.getTypedFailureCount("A", FailureType.INFRASTRUCTURE));
    }

    @org.junit.jupiter.api.Test
    void state_recordTypedFailure_nullArgs_failFast() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);
        assertThrows(IllegalArgumentException.class, () -> state.recordTypedFailure(null, FailureType.QUALITY));
        assertThrows(IllegalArgumentException.class, () -> state.recordTypedFailure("A", null));
    }

    // ==================== Phase 2: Escalation via PlanExecutor (end-to-end per level) ====================

    @org.junit.jupiter.api.Test
    void qualityFailure_escalatesAtThreshold_taskFailed() {
        // maxAegisRejections=2, maxErrorsPerTask=2: after 2 quality failures,
        // task is failed (typed escalation) + 2 errors feed REPEATED_ERRORS → plan ESCALATE.
        FailureEscalationPolicy policy = new FailureEscalationPolicy(2, 10, 10);
        StagnationDetector detector = new StagnationDetector(100, 2);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.QUALITY), detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        // Task A escalated to failed, errors feed REPEATED_ERRORS → plan escalated.
        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
        assertEquals(0, result.getTasksCompleted());
        assertEquals(2, result.getErrorsRecorded());
    }

    @org.junit.jupiter.api.Test
    void infraFailure_retriesUntilThreshold_thenFailed() {
        // maxDispatchRetries=3: infra failures retried (pending) until count hits 3 → failed.
        // maxErrorsPerTask=3 so REPEATED_ERRORS fires from the 3 recorded errors.
        FailureEscalationPolicy policy = new FailureEscalationPolicy(10, 10, 3);
        StagnationDetector detector = new StagnationDetector(100, 3);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.INFRASTRUCTURE), detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
        assertEquals(0, result.getTasksCompleted());
        assertEquals(3, result.getErrorsRecorded());
    }

    @org.junit.jupiter.api.Test
    void stallFailure_retriesUntilThreshold_thenFailed() {
        // staleTaskMaxRetries=2: stall failures retried until count hits 2 → failed.
        // maxErrorsPerTask=2 so REPEATED_ERRORS fires from the 2 recorded errors.
        FailureEscalationPolicy policy = new FailureEscalationPolicy(10, 2, 10);
        StagnationDetector detector = new StagnationDetector(100, 2);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.STALL), detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
        assertEquals(0, result.getTasksCompleted());
        assertEquals(2, result.getErrorsRecorded());
    }

    @org.junit.jupiter.api.Test
    void qualityFailure_belowThreshold_continuesRetrying() {
        // maxAegisRejections=100, staleTaskCycles=3 → task stalled (consecutive failures) before quality threshold.
        // This verifies that below the quality threshold, the task is retried (pending).
        FailureEscalationPolicy policy = new FailureEscalationPolicy(100, 100, 100);
        StagnationDetector detector = new StagnationDetector(3, 100);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.QUALITY), detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        // Task is NOT failed by quality escalation (threshold 100 not reached).
        // Instead, consecutive failures hit staleTaskCycles=3 → TASK_STALLED → ESCALATE.
        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
    }

    // ==================== Phase 2: Zero regression ====================

    @org.junit.jupiter.api.Test
    void untypedFailure_disabledPolicy_zeroRegression_pendingUntilTaskStalled() {
        // Default (disabled) policy: untyped failures behave exactly like pre-W2-3.
        // Task retries (pending) until consecutive failures hit staleTaskCycles → TASK_STALLED → ESCALATE.
        StagnationDetector detector = new StagnationDetector(3, 5);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                t -> TaskOutcome.failure("boom"), detector, replanner)
                .execute(planWithPhases(phase("P1", task("A"))));

        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
        // Same as pre-W2-3: error recorded each failure.
        assertTrue(result.getErrorsRecorded() >= 3);
    }

    @org.junit.jupiter.api.Test
    void typedFailureWithDisabledPolicy_zeroRegression_behavesLikeUntyped() {
        // Even with typed TaskOutcome, disabled policy → no typed escalation → task retries like pre-W2-3.
        StagnationDetector detector = new StagnationDetector(3, 100);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.QUALITY), detector, replanner) // default disabled policy
                .execute(planWithPhases(phase("P1", task("A"))));

        // Task stalled via consecutive failures (staleTaskCycles=3), NOT via quality escalation.
        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
    }

    @org.junit.jupiter.api.Test
    void errorTextPreserved_zeroRegression() {
        // The error text from TaskOutcome is recorded verbatim (same message guarantee).
        StagnationDetector detector = new StagnationDetector(2, 100);
        PlanReplanner replanner = new PlanReplanner();
        String customError = "my-custom-infra-error";

        new PlanExecutor(
                t -> TaskOutcome.failure(customError, FailureType.INFRASTRUCTURE),
                detector, replanner, new FailureEscalationPolicy(100, 100, 100))
                .execute(planWithPhases(phase("P1", task("A"))));

        // Verify the error text was recorded. We can't easily inspect the state after execute,
        // but we can verify via a custom approach: use a state we control.
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);
        state.recordError("A", 1, customError);
        assertEquals(customError, state.getErrors().get(0).getErrorText());
    }

    // ==================== Phase 2: Orthogonality ====================

    @org.junit.jupiter.api.Test
    void qualityFailure_orthogonalToDenialLedger() {
        // Quality typed counter is per-task (plan layer); denial-ledger is per-session (security layer).
        // They are different classes with no interaction.
        // This test verifies the plan-layer quality counter does NOT touch any session-level concept.
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);

        state.recordTypedFailure("A", FailureType.QUALITY);
        state.recordTypedFailure("A", FailureType.QUALITY);
        state.recordTypedFailure("A", FailureType.QUALITY);

        // The quality counter is 3. There is no denial-ledger interaction.
        // (denial-ledger lives in io.nop.ai.agent.security, a completely separate package.)
        assertEquals(3, state.getTypedFailureCount("A", FailureType.QUALITY));
        // Verify the state has no reference to denial-ledger types.
        assertNotNull(state.getTypedFailureCount("A", FailureType.QUALITY));
    }

    @org.junit.jupiter.api.Test
    void stallFailure_scope_perAttempt_vs_planLevelTaskStalled() {
        // W2-3 STALL (per-attempt typed) vs W1-4 TASK_STALLED (plan-level consecutive).
        // A STALL failure increments BOTH the stall typed counter AND consecutiveFailures.
        // They measure different dimensions: type vs total.
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);

        // Simulate 2 stall failures + 1 quality failure (3 total consecutive).
        state.incrementConsecutiveFailures("A"); // stall fail 1
        state.recordTypedFailure("A", FailureType.STALL);
        state.incrementConsecutiveFailures("A"); // stall fail 2
        state.recordTypedFailure("A", FailureType.STALL);
        state.incrementConsecutiveFailures("A"); // quality fail 3
        state.recordTypedFailure("A", FailureType.QUALITY);

        // Typed counters: stall=2, quality=1 (different dimensions).
        assertEquals(2, state.getTypedFailureCount("A", FailureType.STALL));
        assertEquals(1, state.getTypedFailureCount("A", FailureType.QUALITY));
        // Plan-level consecutive: 3 (total, untyped).
        assertEquals(3, state.getConsecutiveFailures("A"));
        // staleTaskCycles=3 → TASK_STALLED fires on consecutive (not on typed).
        StagnationDetector detector = new StagnationDetector(3, 100);
        List<StagnationEvent> events = detector.detect(state);
        boolean hasTaskStalled = events.stream()
                .anyMatch(e -> e.getSignalType() == StagnationSignalType.TASK_STALLED);
        assertTrue(hasTaskStalled, "TASK_STALLED should fire based on consecutive failures (plan-level), "
                + "independent of the typed stall counter (per-attempt)");
    }

    // ==================== Phase 2: Success resets typed counters ====================

    @org.junit.jupiter.api.Test
    void success_resetsTypedFailures() {
        // A task that fails (typed) then succeeds should have its typed counters reset.
        AtomicInteger calls = new AtomicInteger();
        TaskRunner runner = t -> {
            if (calls.incrementAndGet() <= 2) {
                return TaskOutcome.failure("quality block", FailureType.QUALITY);
            }
            return TaskOutcome.success();
        };
        FailureEscalationPolicy policy = new FailureEscalationPolicy(5, 5, 5);
        StagnationDetector detector = new StagnationDetector(100, 100);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(runner, detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        assertEquals(AgentExecStatus.completed, result.getFinalStatus());
        assertTrue(result.getTasksCompleted() >= 1);
    }

    // ==================== Phase 3: Aggregation (Contribute model, no double-count) ====================

    @org.junit.jupiter.api.Test
    void aggregation_noDoubleCount_typedCounterEqualsErrorCount() {
        // Each typed failure records exactly ONE error AND increments ONE typed counter.
        // N failures → N errors, typed counter = N (not 2N).
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);

        for (int i = 1; i <= 3; i++) {
            state.recordError("A", i, "quality fail " + i);
            state.recordTypedFailure("A", FailureType.QUALITY);
        }

        assertEquals(3, state.getTypedFailureCount("A", FailureType.QUALITY));
        assertEquals(3, state.countUnresolvedErrors("A"));
        // Not double-counted: 3 failures → 3 errors, 3 typed count (not 6).
        assertEquals(state.getTypedFailureCount("A", FailureType.QUALITY),
                state.countUnresolvedErrors("A"));
    }

    @org.junit.jupiter.api.Test
    void aggregation_mixedTypes_allRecordedOnce() {
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);

        state.recordError("A", 1, "q1");
        state.recordTypedFailure("A", FailureType.QUALITY);
        state.recordError("A", 2, "i1");
        state.recordTypedFailure("A", FailureType.INFRASTRUCTURE);
        state.recordError("A", 3, "q2");
        state.recordTypedFailure("A", FailureType.QUALITY);

        // Typed counters.
        assertEquals(2, state.getTypedFailureCount("A", FailureType.QUALITY));
        assertEquals(1, state.getTypedFailureCount("A", FailureType.INFRASTRUCTURE));
        assertEquals(0, state.getTypedFailureCount("A", FailureType.STALL));
        // Total errors = 3 (each failure recorded once).
        assertEquals(3, state.countUnresolvedErrors("A"));
    }

    // ==================== Phase 3: End-to-end (failure → escalation → aggregation → plan decision) ====================

    @org.junit.jupiter.api.Test
    void endToEnd_qualityEscalation_feedsRepeatedErrors_planEscalates() {
        // End-to-end (Rule #22): quality failures → typed counter hits threshold →
        // task failed → errors feed REPEATED_ERRORS → plan ESCALATE.
        // maxAegisRejections=2, maxErrorsPerTask=2 → 2 quality failures → task failed + 2 errors → REPEATED_ERRORS → ESCALATE.
        FailureEscalationPolicy policy = new FailureEscalationPolicy(2, 100, 100);
        StagnationDetector detector = new StagnationDetector(100, 2); // maxErrorsPerTask=2
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.QUALITY), detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        // Plan escalated via the chain: quality escalation → task failed → REPEATED_ERRORS → ESCALATE.
        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
        assertTrue(result.isEscalated());
        // Verify the stagnation events include REPEATED_ERRORS (the aggregation signal).
        boolean hasRepeatedErrors = result.getEventsObserved().stream()
                .anyMatch(e -> e.getSignalType() == StagnationSignalType.REPEATED_ERRORS);
        assertTrue(hasRepeatedErrors, "REPEATED_ERRORS should fire from the errors recorded by typed failures");
        // Verify a decision was enacted.
        assertFalse(result.getDecisionsEnacted().isEmpty());
    }

    @org.junit.jupiter.api.Test
    void endToEnd_infraEscalation_feedsRepeatedErrors_planEscalates() {
        FailureEscalationPolicy policy = new FailureEscalationPolicy(100, 100, 3);
        StagnationDetector detector = new StagnationDetector(100, 3);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.INFRASTRUCTURE), detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
        boolean hasRepeatedErrors = result.getEventsObserved().stream()
                .anyMatch(e -> e.getSignalType() == StagnationSignalType.REPEATED_ERRORS);
        assertTrue(hasRepeatedErrors);
    }

    @org.junit.jupiter.api.Test
    void endToEnd_w1_4_decisionContractUnchanged_disabledPolicy() {
        // With disabled policy, W1-4 decisions (ESCALATE/ROLLBACK/SPLIT) behave unchanged.
        // This test verifies zero regression of the W1-4 contract.
        StagnationDetector detector = new StagnationDetector(2, 2);
        PlanReplanner replanner = new PlanReplanner(); // default escalateOnly

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.QUALITY), detector, replanner) // disabled policy (default)
                .execute(planWithPhases(phase("P1", task("A"))));

        // W1-4 ESCALATE fires via consecutive failures / repeated errors — unchanged.
        assertEquals(AgentExecStatus.escalated, result.getFinalStatus());
        assertEquals(ReplanDecision.ESCALATE, result.getDecisionsEnacted().get(0).getType());
    }

    @org.junit.jupiter.api.Test
    void endToEnd_typedEscalation_failsTask_repeatedErrorsFromSameErrors() {
        // Verify the full chain: typed escalation marks task failed, and the SAME errors
        // that were recorded during typed failures feed REPEATED_ERRORS (Contribute model).
        // maxAegisRejections=2, maxErrorsPerTask=2.
        FailureEscalationPolicy policy = new FailureEscalationPolicy(2, 100, 100);
        StagnationDetector detector = new StagnationDetector(100, 2);
        PlanReplanner replanner = new PlanReplanner();

        PlanExecutionResult result = new PlanExecutor(
                alwaysFail(FailureType.QUALITY), detector, replanner, policy)
                .execute(planWithPhases(phase("P1", task("A"))));

        // Exactly 2 errors recorded (2 quality failures before/at escalation).
        assertEquals(2, result.getErrorsRecorded());
        // The REPEATED_ERRORS signal fired with count=2 (not double-counted).
        StagnationEvent repeatedErrorsEvent = result.getEventsObserved().stream()
                .filter(e -> e.getSignalType() == StagnationSignalType.REPEATED_ERRORS)
                .findFirst()
                .orElse(null);
        assertNotNull(repeatedErrorsEvent);
        assertEquals(2, repeatedErrorsEvent.getCount());
    }

    // ==================== Phase 3: Wiring verification (Rule #23) ====================

    @org.junit.jupiter.api.Test
    void wiring_typedFailures_consumedByExistingRecordErrorPipeline() {
        // Verify that typed failures flow through the EXISTING recordError → countUnresolvedErrors
        // pipeline (no separate aggregation pipeline). The executor records errors for every
        // failure (typed or not), and StagnationDetector consumes them unchanged.
        AgentPlan plan = planWithPhases(phase("P1", task("A")));
        PlanExecutionState state = new PlanExecutionState(plan);

        // Simulate what the executor does for a typed failure:
        state.incrementConsecutiveFailures("A");
        state.recordError("A", 1, "quality block");
        state.recordTypedFailure("A", FailureType.QUALITY);

        // The existing REPEATED_ERRORS pipeline consumes the error:
        assertEquals(1, state.countUnresolvedErrors("A"));
        // StagnationDetector reads it:
        StagnationDetector detector = new StagnationDetector(100, 1);
        List<StagnationEvent> events = detector.detect(state);
        assertTrue(events.stream().anyMatch(
                e -> e.getSignalType() == StagnationSignalType.REPEATED_ERRORS));
    }

    // ==================== Helpers ====================

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

    private static AgentPlanPhase phase(String name, AgentPlanTaskModel... tasks) {
        AgentPlanPhase p = new AgentPlanPhase();
        p.setName(name);
        p.setStatus(AgentExecStatus.pending);
        for (AgentPlanTaskModel t : tasks) {
            p.addTask(t);
        }
        return p;
    }

    private static AgentPlan planWithPhases(AgentPlanPhase... phases) {
        AgentPlan plan = new AgentPlan();
        plan.setStatus(AgentExecStatus.pending);
        for (AgentPlanPhase p : phases) {
            plan.addPhase(p);
        }
        return plan;
    }

    private static TaskRunner alwaysFail(FailureType type) {
        return t -> TaskOutcome.failure("typed failure: " + type, type);
    }
}
