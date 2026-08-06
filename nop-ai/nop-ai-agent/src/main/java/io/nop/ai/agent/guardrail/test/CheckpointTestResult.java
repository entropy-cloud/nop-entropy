package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.security.SecurityCheckpoint;

import java.util.Objects;

/**
 * Per-case result row produced by {@link CheckpointTestHarness#runCase} and
 * aggregated into {@link CheckpointTestReport} (design
 * {@code guardrail-contract.md} §增量 4, 裁定 E). Immutable value object:
 * binds the evaluated case, the chain's actual {@link SecurityCheckpoint.Decision},
 * the captured {@code matchedRule} of the denying checkpoint layer, and the
 * pass/fail verdict.
 *
 * <p><b>Pass semantics</b>: a case passes when the actual decision equals the
 * expected decision <em>and</em> (when {@code expectedMatchedRule} is set) the
 * captured deny {@code matchedRule} equals it. A {@code null}
 * {@code actualMatchedRule} on an expected-DENY case is itself a signal (no
 * deny audit event was recorded — e.g. the deny was produced without an audit
 * log, which should not happen for the 7-checkpoint chain).
 */
public final class CheckpointTestResult {

    private final CheckpointTestCase testCase;
    private final SecurityCheckpoint.Decision actualDecision;
    private final String actualMatchedRule;
    private final boolean passed;
    private final String failureReason;

    public CheckpointTestResult(CheckpointTestCase testCase, SecurityCheckpoint.Decision actualDecision,
                                String actualMatchedRule, boolean passed, String failureReason) {
        this.testCase = testCase;
        this.actualDecision = actualDecision;
        this.actualMatchedRule = actualMatchedRule;
        this.passed = passed;
        this.failureReason = failureReason;
    }

    public CheckpointTestCase getTestCase() {
        return testCase;
    }

    public SecurityCheckpoint.Decision getActualDecision() {
        return actualDecision;
    }

    public String getActualMatchedRule() {
        return actualMatchedRule;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getCaseId() {
        return testCase.getId();
    }

    public String getCategory() {
        return testCase.getCategory();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CheckpointTestResult that = (CheckpointTestResult) o;
        return passed == that.passed
                && Objects.equals(testCase, that.testCase)
                && actualDecision == that.actualDecision
                && Objects.equals(actualMatchedRule, that.actualMatchedRule)
                && Objects.equals(failureReason, that.failureReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testCase, actualDecision, actualMatchedRule, passed, failureReason);
    }

    @Override
    public String toString() {
        return "CheckpointTestResult{case='" + testCase.getId() + "', actual=" + actualDecision
                + ", rule='" + actualMatchedRule + "', passed=" + passed + "}";
    }
}
