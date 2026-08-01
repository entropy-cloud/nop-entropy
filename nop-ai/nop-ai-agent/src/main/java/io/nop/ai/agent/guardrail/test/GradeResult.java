package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.guardrail.GuardrailResult;

import java.util.Objects;

/**
 * Deterministic grader judgment for a single {@link AttackCase} (design
 * {@code guardrail-contract.md} §增量 1, Decision C). Immutable value object.
 */
public final class GradeResult {

    private final Verdict verdict;
    private final String caseId;
    private final GuardrailResult actual;
    private final String reason;

    public GradeResult(Verdict verdict, String caseId, GuardrailResult actual, String reason) {
        this.verdict = verdict;
        this.caseId = caseId;
        this.actual = actual;
        this.reason = reason;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getCaseId() {
        return caseId;
    }

    public GuardrailResult getActual() {
        return actual;
    }

    public String getReason() {
        return reason;
    }

    public boolean isPass() {
        return verdict == Verdict.PASS;
    }

    public boolean isFail() {
        return verdict == Verdict.FAIL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GradeResult that = (GradeResult) o;
        return verdict == that.verdict
                && Objects.equals(caseId, that.caseId)
                && Objects.equals(actual, that.actual)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verdict, caseId, actual, reason);
    }

    @Override
    public String toString() {
        return "GradeResult{verdict=" + verdict + ", caseId='" + caseId + "', reason='" + reason + "'}";
    }
}
