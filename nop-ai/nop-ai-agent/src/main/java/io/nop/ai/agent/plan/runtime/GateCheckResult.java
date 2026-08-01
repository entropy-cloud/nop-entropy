package io.nop.ai.agent.plan.runtime;

import io.nop.ai.agent.plan.model.AgentPlanCriterion;
import io.nop.ai.agent.plan.model.GateOnFail;

import java.util.Collections;
import java.util.List;

/**
 * Result of {@link PlanRunner#checkGate} evaluation. Captures the gate outcome
 * (passed / retry / blocked / escalated / explicit-verdict-required) plus the
 * criteria that caused the failure, so callers can act on a structured verdict
 * rather than guessing from an exception (design §14.1).
 *
 * <p>This is an immutable value object returned by the runtime gate checker.
 */
public final class GateCheckResult {

    /**
     * The discrete outcome of evaluating a phase gate.
     */
    public enum Outcome {
        /** All required criteria are satisfied (and no blocking criterion is
         * unsatisfied); the gate is open — proceed to the next phase. */
        PASSED,

        /** The gate failed and {@code on-fail=retry}; the current attempt has
         * not yet reached {@code max-retries}, so the phase should be retried. */
        RETRY,

        /** The gate failed with {@code on-fail=retry} but the retry budget
         * ({@code max-retries}) is exhausted — escalate (design §14.1:
         * "达上限后按 escalate 处理"). */
        RETRY_EXHAUSTED,

        /** The gate failed with {@code on-fail=block}; subsequent phases are
         * blocked until the gate passes. */
        BLOCKED,

        /** The gate failed with {@code on-fail=escalate}; the plan/phase
         * status should be set to
         * {@link io.nop.ai.agent.model.AgentExecStatus#escalated}. */
        ESCALATED,

        /** {@code require-explicit-verdict=true} but no explicit verdict has
         * been recorded (gate.verdict is not {@code true}). The gate must not
         * auto-pass — this is an explicit "not passed" state, never a silent
         * pass (Minimum Rules #24 — no silent no-op). */
        EXPLICIT_VERDICT_REQUIRED
    }

    private final Outcome outcome;
    private final boolean hardBlocked;
    private final List<AgentPlanCriterion> unsatisfiedRequired;
    private final List<AgentPlanCriterion> unsatisfiedBlocking;
    private final int attempt;
    private final int maxRetries;
    private final GateOnFail onFail;

    private GateCheckResult(Outcome outcome, boolean hardBlocked,
                            List<AgentPlanCriterion> unsatisfiedRequired,
                            List<AgentPlanCriterion> unsatisfiedBlocking,
                            int attempt, int maxRetries, GateOnFail onFail) {
        this.outcome = outcome;
        this.hardBlocked = hardBlocked;
        this.unsatisfiedRequired = unsatisfiedRequired == null ? Collections.emptyList() : unsatisfiedRequired;
        this.unsatisfiedBlocking = unsatisfiedBlocking == null ? Collections.emptyList() : unsatisfiedBlocking;
        this.attempt = attempt;
        this.maxRetries = maxRetries;
        this.onFail = onFail;
    }

    static GateCheckResult passed() {
        return new GateCheckResult(Outcome.PASSED, false, null, null, 0, 0, null);
    }

    static GateCheckResult explicitVerdictRequired(int attempt, int maxRetries, GateOnFail onFail,
                                                   List<AgentPlanCriterion> unsatisfiedRequired,
                                                   List<AgentPlanCriterion> unsatisfiedBlocking,
                                                   boolean hardBlocked) {
        return new GateCheckResult(Outcome.EXPLICIT_VERDICT_REQUIRED, hardBlocked,
                unsatisfiedRequired, unsatisfiedBlocking, attempt, maxRetries, onFail);
    }

    static GateCheckResult failed(GateOnFail onFail, int attempt, int maxRetries,
                                  List<AgentPlanCriterion> unsatisfiedRequired,
                                  List<AgentPlanCriterion> unsatisfiedBlocking,
                                  boolean hardBlocked) {
        Outcome outcome;
        switch (onFail) {
            case retry:
                if (attempt > maxRetries) {
                    outcome = Outcome.RETRY_EXHAUSTED;
                } else {
                    outcome = Outcome.RETRY;
                }
                break;
            case block:
                outcome = Outcome.BLOCKED;
                break;
            case escalate:
                outcome = Outcome.ESCALATED;
                break;
            default:
                throw new IllegalArgumentException("Unknown GateOnFail: " + onFail);
        }
        return new GateCheckResult(outcome, hardBlocked, unsatisfiedRequired, unsatisfiedBlocking,
                attempt, maxRetries, onFail);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public boolean isPassed() {
        return outcome == Outcome.PASSED;
    }

    public boolean isHardBlocked() {
        return hardBlocked;
    }

    public List<AgentPlanCriterion> getUnsatisfiedRequired() {
        return unsatisfiedRequired;
    }

    public List<AgentPlanCriterion> getUnsatisfiedBlocking() {
        return unsatisfiedBlocking;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public GateOnFail getOnFail() {
        return onFail;
    }
}
