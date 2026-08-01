package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;

import java.util.Objects;

/**
 * Per-case detail row inside a {@link GuardrailTestReport} (design
 * {@code guardrail-contract.md} §增量 1, Decision D). Immutable value object:
 * binds the evaluated case, the guardrail's actual result, and the grader's
 * verdict together for regression snapshots.
 */
public final class CaseResult {

    private final AttackCase attackCase;
    private final GuardrailResult actual;
    private final GradeResult grade;

    public CaseResult(AttackCase attackCase, GuardrailResult actual, GradeResult grade) {
        this.attackCase = attackCase;
        this.actual = actual;
        this.grade = grade;
    }

    public AttackCase getAttackCase() {
        return attackCase;
    }

    public GuardrailResult getActual() {
        return actual;
    }

    public GradeResult getGrade() {
        return grade;
    }

    public String getCaseId() {
        return attackCase.getId();
    }

    public String getCategory() {
        return attackCase.getCategory();
    }

    public String getThreatClass() {
        return attackCase.getThreatClass();
    }

    public GuardrailDirection getDirection() {
        return attackCase.getDirection();
    }

    public ExpectedBehavior getExpectedBehavior() {
        return attackCase.getExpectedBehavior();
    }

    public Verdict getVerdict() {
        return grade.getVerdict();
    }

    public String getTransform() {
        return attackCase.getTransform();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CaseResult that = (CaseResult) o;
        return Objects.equals(attackCase, that.attackCase)
                && Objects.equals(actual, that.actual)
                && Objects.equals(grade, that.grade);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attackCase, actual, grade);
    }
}
