package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.model.AgentExecStatus;

import java.util.List;

/**
 * Produces idempotent {@link ReplanDecisionResult}s from stagnation events
 * and enacts them on the runtime execution state (design §14.4.2 / §14.4.3).
 *
 * <p><b>Decision (pure function)</b>: {@link #decide(StagnationEvent)} maps a
 * stagnation event to a decision result (type + payload) under the configured
 * {@link ReplanPolicy}. The mapping is a pure function of the event and the
 * policy — the same observable stagnation always yields the same result
 * (design §14.4.4 idempotency). The owning phase is expected to be carried on
 * the event itself (set by {@link StagnationDetector}); a missing phase
 * simply cannot match a rollback-eligible phase.
 *
 * <p><b>Trigger mapping</b> (design §14.4.2 trigger conditions):
 * <ul>
 *   <li>{@link StagnationSignalType#GATE_EXHAUSTED} on a rollback-eligible
 *       phase → {@link ReplanDecision#ROLLBACK_PHASE} (target = registered
 *       preceding phase); otherwise → {@link ReplanDecision#ESCALATE}.</li>
 *   <li>{@link StagnationSignalType#TASK_STALLED} on a split-eligible task →
 *       {@link ReplanDecision#SPLIT_TASK} (Phase 2); on a task owned by a
 *       rollback-eligible phase → ROLLBACK_PHASE; otherwise → ESCALATE.</li>
 *   <li>{@link StagnationSignalType#REPEATED_ERRORS} on a task owned by a
 *       rollback-eligible phase → ROLLBACK_PHASE; otherwise → ESCALATE.</li>
 * </ul>
 * With {@link ReplanPolicy#escalateOnly()} (the default), every non-empty
 * signal escalates — identical to the legacy first-cut behavior (zero
 * regression).
 *
 * <p><b>Enactment</b>: {@link #apply(ReplanDecisionResult, PlanExecutionState)}
 * mutates the execution state. {@code ESCALATE} sets the plan and current
 * phase to {@link AgentExecStatus#escalated}; {@code CONTINUE} is a no-op;
 * {@code ROLLBACK_PHASE} resets the target phase's task statuses, clears the
 * source phase's stagnation state (errors resolved + failure counters + gate
 * marker) to break the detect→rollback loop, and moves the current phase back;
 * {@code SPLIT_TASK} is enacted in Phase 2; {@code ABORT} fails fast
 * (out-of-scope successor).
 */
public class PlanReplanner {

    private final ReplanPolicy policy;

    /** Construct a replanner with the default {@link ReplanPolicy#escalateOnly()} policy. */
    public PlanReplanner() {
        this(ReplanPolicy.escalateOnly());
    }

    /** Construct a replanner with the given rollback/split trigger policy. */
    public PlanReplanner(ReplanPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.policy = policy;
    }

    /** The configured trigger policy (visible for tests/observability). */
    public ReplanPolicy getPolicy() {
        return policy;
    }

    /**
     * Decide the replan action for a single stagnation event. Pure function
     * of the event and the configured policy.
     */
    public ReplanDecisionResult decide(StagnationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        StagnationSignalType signal = event.getSignalType();
        String phase = event.getTargetPhase();
        String task = event.getTargetTaskNo();
        switch (signal) {
            case GATE_EXHAUSTED:
                if (policy.hasRollbackTarget(phase)) {
                    return ReplanDecisionResult.rollback(policy.rollbackTarget(phase), null, signal,
                            "gate exhausted on phase " + phase + " -> rollback to " + policy.rollbackTarget(phase));
                }
                return ReplanDecisionResult.escalate(signal, phase, null,
                        "gate exhausted on phase " + phase + " (no rollback policy) -> escalate");
            case TASK_STALLED:
                if (policy.isSplittable(task)) {
                    return ReplanDecisionResult.split(task, signal,
                            "task " + task + " stalled and split-eligible -> split");
                }
                if (policy.hasRollbackTarget(phase)) {
                    return ReplanDecisionResult.rollback(policy.rollbackTarget(phase), task, signal,
                            "task " + task + " stalled on rollback-eligible phase " + phase
                                    + " -> rollback to " + policy.rollbackTarget(phase));
                }
                return ReplanDecisionResult.escalate(signal, phase, task,
                        "task " + task + " stalled (no recoverable policy) -> escalate");
            case REPEATED_ERRORS:
                if (policy.hasRollbackTarget(phase)) {
                    return ReplanDecisionResult.rollback(policy.rollbackTarget(phase), task, signal,
                            "repeated errors on task " + task + " (rollback-eligible phase " + phase
                                    + ") -> rollback to " + policy.rollbackTarget(phase));
                }
                return ReplanDecisionResult.escalate(signal, phase, task,
                        "repeated errors on task " + task + " (no rollback policy) -> escalate");
            default:
                throw new IllegalArgumentException("Unknown signal type: " + signal);
        }
    }

    /**
     * Decide the overall replan action for a batch of events. An empty batch
     * means no stagnation → {@link ReplanDecisionResult#continueResult()};
     * otherwise the highest-precedence per-event decision wins. Precedence
     * (most severe first): ESCALATE/ABORT &gt; ROLLBACK_PHASE &gt; SPLIT_TASK.
     * This preserves the legacy "any non-empty batch escalates" behavior when
     * no recoverable policy is configured.
     */
    public ReplanDecisionResult decide(List<StagnationEvent> events) {
        if (events == null || events.isEmpty()) {
            return ReplanDecisionResult.continueResult();
        }
        ReplanDecisionResult picked = ReplanDecisionResult.continueResult();
        for (StagnationEvent event : events) {
            ReplanDecisionResult candidate = decide(event);
            if (precedence(candidate) < precedence(picked)) {
                picked = candidate;
            }
        }
        return picked;
    }

    /** Lower number = higher precedence (ESCALATE/ABORT=0 &gt; ROLLBACK=1 &gt; SPLIT=2 &gt; CONTINUE=3). */
    private int precedence(ReplanDecisionResult r) {
        switch (r.getType()) {
            case ESCALATE:
            case ABORT:
                return 0;
            case ROLLBACK_PHASE:
                return 1;
            case SPLIT_TASK:
                return 2;
            default:
                return 3;
        }
    }

    /**
     * Enact a decision result on the runtime execution state.
     *
     * @throws UnsupportedOperationException if the decision is
     *         {@link ReplanDecision#ABORT} — its enactment is out-of-scope
     *         (successor) and must fail fast rather than be silently skipped
     */
    public void apply(ReplanDecisionResult decision, PlanExecutionState state) {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        switch (decision.getType()) {
            case CONTINUE:
                break;
            case ESCALATE:
                state.setPlanStatus(AgentExecStatus.escalated);
                String escPhase = state.getCurrentPhase();
                if (escPhase != null) {
                    state.setPhaseStatus(escPhase, AgentExecStatus.escalated);
                }
                break;
            case ROLLBACK_PHASE:
                enactRollback(decision, state);
                break;
            case SPLIT_TASK:
                enactSplit(decision, state);
                break;
            case ABORT:
                throw new UnsupportedOperationException(
                        "not yet implemented: ABORT enactment is deferred to a successor plan (design §14.4.2)");
            default:
                throw new IllegalArgumentException("Unknown decision: " + decision.getType());
        }
    }

    /**
     * ROLLBACK_PHASE enactment (design §14.4.3):
     * <ol>
     *   <li>target = the preceding phase to resume from (carried on the
     *       result); reset its tasks {@code completed→pending} and its phase
     *       status to {@code pending} (scoped to that phase only).</li>
     *   <li>source = the current phase being rolled back from; reset its
     *       stagnation state — resolve its tasks' accumulated
     *       {@code AgentPlanError}s (first writer of {@code resolvedAt}),
     *       zero their consecutive-failure counters, and clear its
     *       gate-exhaustion marker. This breaks the detect→rollback loop
     *       (otherwise the detector would immediately re-emit the same
     *       signal).</li>
     *   <li>move {@code currentPhase} back to the target.</li>
     * </ol>
     * The frozen template is never mutated (§14.4.3 freeze ruling); all
     * mutations target the mutable runtime overlay.
     */
    private void enactRollback(ReplanDecisionResult decision, PlanExecutionState state) {
        String target = decision.getTargetPhase();
        if (!state.hasPhase(target)) {
            throw new IllegalArgumentException(
                    "ROLLBACK_PHASE target phase not found in plan: " + target);
        }
        String source = state.getCurrentPhase();

        // (1) Reset target phase: tasks completed→pending, phase status→pending.
        for (String taskNo : state.phaseTaskNos(target)) {
            if (state.getTaskStatus(taskNo) == AgentExecStatus.completed) {
                state.setTaskStatus(taskNo, AgentExecStatus.pending);
            }
        }
        state.setPhaseStatus(target, AgentExecStatus.pending);

        // (2) Reset the source phase's stagnation state to break the loop.
        //     When target==source (rollback to self), reset covers both in one pass.
        String stagnationPhase = (source != null) ? source : target;
        resetStagnationForPhase(state, stagnationPhase);

        // (3) Move the current phase pointer back to the target.
        state.setCurrentPhase(target);
    }

    private void resetStagnationForPhase(PlanExecutionState state, String phaseName) {
        if (phaseName == null) {
            return;
        }
        for (String taskNo : state.phaseTaskNos(phaseName)) {
            state.resolveErrorsForTask(taskNo);
            state.resetConsecutiveFailures(taskNo);
        }
        state.clearGateExhausted(phaseName);
        AgentExecStatus ps = state.getPhaseStatus(phaseName);
        if (ps == AgentExecStatus.escalated || ps == AgentExecStatus.failed) {
            state.setPhaseStatus(phaseName, AgentExecStatus.pending);
        }
    }

    /**
     * SPLIT_TASK enactment (design §14.4.3):
     * <ol>
     *   <li>look up the {@link SplitSpec} for the parent task from the
     *       configured {@link ReplanPolicy};</li>
     *   <li>mark the parent as a split placeholder (treated as completed so
     *       it does not re-stall) and resolve its accumulated errors + zero
     *       its failure counter to break the detect→split loop;</li>
     *   <li>register each child template as a runtime overlay task under the
     *       parent's owning phase, initialized to {@code pending}.</li>
     * </ol>
     * The frozen template is never mutated; children are visible to
     * {@link PlanScheduler} (structural overlay) and the executor's phase
     * filter (runtime phase registration).
     */
    private void enactSplit(ReplanDecisionResult decision, PlanExecutionState state) {
        String parent = decision.getTargetTaskNo();
        SplitSpec spec = policy.splitSpecFor(parent);
        if (spec == null) {
            throw new IllegalStateException(
                    "SPLIT_TASK enacted for task '" + parent + "' but no SplitSpec is registered "
                            + "(policy/splitSpecFor returned null) — this is a programming error");
        }
        String phase = state.phaseOwningTask(parent);
        if (phase == null) {
            throw new IllegalStateException(
                    "SPLIT_TASK parent task '" + parent + "' is not owned by any phase");
        }

        // Mark the parent as a split placeholder and clear its stagnation state.
        state.markSplitParent(parent);
        state.setTaskStatus(parent, AgentExecStatus.completed);
        state.resolveErrorsForTask(parent);
        state.resetConsecutiveFailures(parent);

        // Insert the child templates into the runtime overlay under the parent's phase.
        for (io.nop.ai.agent.plan.model.AgentPlanTaskModel child : spec.getChildTemplates()) {
            // Register a fresh runtime node so the overlay owns its status lifecycle.
            state.registerRuntimeTask(phase, child);
        }
    }

    /**
     * Convenience: decide the overall action for a batch of events and enact
     * it. Returns the enacted decision result.
     */
    public ReplanDecisionResult decideAndApply(List<StagnationEvent> events, PlanExecutionState state) {
        ReplanDecisionResult decision = decide(events);
        apply(decision, state);
        return decision;
    }
}
