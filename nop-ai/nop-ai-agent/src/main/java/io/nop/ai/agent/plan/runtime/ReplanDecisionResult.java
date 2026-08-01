package io.nop.ai.agent.plan.runtime;

import java.util.Objects;

/**
 * The result of a replan decision: the {@link ReplanDecision decision type}
 * plus the payload needed to enact it (design §14.4.2 decision contract —
 * "decision payload = decision type + target phase/task + triggering signal
 * type + reason").
 *
 * <p>This result object is the return type of {@link PlanReplanner#decide}.
 * Carrying the payload on the result (rather than re-passing the triggering
 * event to {@link PlanReplanner#apply}) keeps the decision record observable:
 * {@link PlanExecutionResult#getDecisionsEnacted()} captures results with
 * their targets, so an audit trail can see <em>which</em> phase/task a
 * rollback/split targeted, not just the decision type.
 *
 * <p><b>Idempotency</b>: the decision is a pure function of the triggering
 * stagnation state and the configured {@link ReplanPolicy}. The
 * {@link #idempotencyKey()} is composed of the structural fields only
 * (type, target phase, target task, triggering signal), so the same
 * observable stagnation + policy always yields the same decision result
 * (design §14.4.4).
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class ReplanDecisionResult {

    private final ReplanDecision type;
    private final String targetPhase;
    private final String targetTaskNo;
    private final StagnationSignalType triggerSignal;
    private final String reason;

    private ReplanDecisionResult(ReplanDecision type, String targetPhase, String targetTaskNo,
                                 StagnationSignalType triggerSignal, String reason) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
        this.targetPhase = targetPhase;
        this.targetTaskNo = targetTaskNo;
        this.triggerSignal = triggerSignal;
        this.reason = reason;
    }

    /** A CONTINUE result: no stagnation, no payload. */
    public static ReplanDecisionResult continueResult() {
        return new ReplanDecisionResult(ReplanDecision.CONTINUE, null, null, null,
                "no stagnation signal observed");
    }

    /** An ESCALATE result targeting a phase and/or task. */
    public static ReplanDecisionResult escalate(StagnationSignalType triggerSignal,
                                                String targetPhase, String targetTaskNo, String reason) {
        return new ReplanDecisionResult(ReplanDecision.ESCALATE, targetPhase, targetTaskNo,
                triggerSignal, reason);
    }

    /** A ROLLBACK_PHASE result: {@code targetPhase} is the preceding phase to resume from. */
    public static ReplanDecisionResult rollback(String targetPhase, String targetTaskNo,
                                                StagnationSignalType triggerSignal, String reason) {
        if (targetPhase == null || targetPhase.isEmpty()) {
            throw new IllegalArgumentException("targetPhase must not be null/empty for ROLLBACK_PHASE");
        }
        return new ReplanDecisionResult(ReplanDecision.ROLLBACK_PHASE, targetPhase, targetTaskNo,
                triggerSignal, reason);
    }

    /** A SPLIT_TASK result: {@code targetTaskNo} is the task being split. */
    public static ReplanDecisionResult split(String targetTaskNo, StagnationSignalType triggerSignal,
                                             String reason) {
        if (targetTaskNo == null || targetTaskNo.isEmpty()) {
            throw new IllegalArgumentException("targetTaskNo must not be null/empty for SPLIT_TASK");
        }
        return new ReplanDecisionResult(ReplanDecision.SPLIT_TASK, null, targetTaskNo,
                triggerSignal, reason);
    }

    /**
     * An ABORT result. {@code decide()} never produces ABORT (its enactment is
     * out-of-scope); this factory exists so {@link PlanReplanner#apply} can
     * be exercised on the ABORT path, where it fails fast with
     * {@link UnsupportedOperationException} (Minimum Rules #24).
     */
    public static ReplanDecisionResult abort(String reason) {
        return new ReplanDecisionResult(ReplanDecision.ABORT, null, null, null, reason);
    }

    public ReplanDecision getType() {
        return type;
    }

    public String getTargetPhase() {
        return targetPhase;
    }

    public String getTargetTaskNo() {
        return targetTaskNo;
    }

    public StagnationSignalType getTriggerSignal() {
        return triggerSignal;
    }

    public String getReason() {
        return reason;
    }

    /** Whether this decision is terminal (stops execution): ESCALATE or ABORT. */
    public boolean isTerminal() {
        return type == ReplanDecision.ESCALATE || type == ReplanDecision.ABORT;
    }

    /** Whether this decision is a recoverable (non-terminal) replan: ROLLBACK or SPLIT. */
    public boolean isRecoverable() {
        return type == ReplanDecision.ROLLBACK_PHASE || type == ReplanDecision.SPLIT_TASK;
    }

    /**
     * Deterministic key over structural fields only (excludes the free-form
     * reason). Two results describing the same decision over the same target
     * driven by the same signal always produce the same key.
     */
    public String idempotencyKey() {
        return type.name() + "|" + targetPhase + "|" + targetTaskNo + "|" + triggerSignal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReplanDecisionResult)) return false;
        ReplanDecisionResult that = (ReplanDecisionResult) o;
        return type == that.type
                && Objects.equals(targetPhase, that.targetPhase)
                && Objects.equals(targetTaskNo, that.targetTaskNo)
                && triggerSignal == that.triggerSignal;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, targetPhase, targetTaskNo, triggerSignal);
    }

    @Override
    public String toString() {
        return "ReplanDecisionResult{type=" + type
                + ", targetPhase=" + targetPhase
                + ", targetTaskNo=" + targetTaskNo
                + ", triggerSignal=" + triggerSignal
                + ", reason=" + reason + '}';
    }
}
