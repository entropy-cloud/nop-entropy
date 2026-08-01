package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGuardrailTestReport {

    private static AttackCase attack(String id, String category) {
        return new AttackCase(id, category, category, "p",
                GuardrailDirection.INPUT, ExpectedBehavior.BLOCK, null, null);
    }

    private static AttackCase benign(String id, String category) {
        return new AttackCase(id, category, category, "p",
                GuardrailDirection.INPUT, ExpectedBehavior.PASS, null, null);
    }

    private static CaseResult cr(AttackCase ac, GuardrailResult result, Verdict verdict) {
        return new CaseResult(ac, result,
                new GradeResult(verdict, ac.getId(), result, "r"));
    }

    @Test
    void emptyReportHasZeroRates() {
        GuardrailTestReport report = GuardrailTestReport.build(java.util.Collections.emptyList());
        assertEquals(0, report.getTotalCases());
        assertEquals(0.0, report.getBlockRate());
        assertEquals(0.0, report.getLeakRate());
        assertEquals(0.0, report.getFalsePositiveRate());
    }

    @Test
    void attackMetricsComputed() {
        AttackCase a1 = attack("a1", "prompt_injection");
        AttackCase a2 = attack("a2", "prompt_injection");
        AttackCase a3 = attack("a3", "prompt_injection");
        List<CaseResult> results = Arrays.asList(
                cr(a1, new GuardrailResult.BlockResult("x"), Verdict.PASS),   // blocked
                cr(a2, GuardrailResult.PassResult.instance(), Verdict.FAIL),  // leaked
                cr(a3, new GuardrailResult.ModifyResult("m"), Verdict.PARTIAL)); // modified

        GuardrailTestReport report = GuardrailTestReport.build(results);

        assertEquals(3, report.getTotalAttacks());
        assertEquals(1, report.getBlockedAttacks());
        assertEquals(1, report.getLeakedAttacks());
        assertEquals(1, report.getModifiedAttacks());
        // rates are concrete numbers
        assertEquals(1.0 / 3.0, report.getBlockRate(), 1e-9);
        assertEquals(1.0 / 3.0, report.getLeakRate(), 1e-9);
    }

    @Test
    void benignFalsePositiveComputed() {
        AttackCase b1 = benign("b1", "benign");
        AttackCase b2 = benign("b2", "benign");
        List<CaseResult> results = Arrays.asList(
                cr(b1, GuardrailResult.PassResult.instance(), Verdict.PASS),
                cr(b2, new GuardrailResult.BlockResult("fp"), Verdict.FAIL));

        GuardrailTestReport report = GuardrailTestReport.build(results);

        assertEquals(2, report.getTotalBenign());
        assertEquals(1, report.getFalselyBlockedBenign());
        assertEquals(0.5, report.getFalsePositiveRate(), 1e-9);
    }

    @Test
    void perCategoryBreakdown() {
        AttackCase a1 = attack("a1", "prompt_injection");
        AttackCase a2 = attack("a2", "role_hijack");
        AttackCase b1 = benign("b1", "benign");
        List<CaseResult> results = Arrays.asList(
                cr(a1, new GuardrailResult.BlockResult("x"), Verdict.PASS),
                cr(a2, GuardrailResult.PassResult.instance(), Verdict.FAIL),
                cr(b1, GuardrailResult.PassResult.instance(), Verdict.PASS));

        GuardrailTestReport report = GuardrailTestReport.build(results);

        assertNotNull(report.getCategory("prompt_injection"));
        assertEquals(1, report.getCategory("prompt_injection").getBlockedAttacks());
        assertEquals(1.0, report.getCategory("prompt_injection").getBlockRate(), 1e-9);

        assertNotNull(report.getCategory("role_hijack"));
        assertEquals(1, report.getCategory("role_hijack").getLeakedAttacks());

        assertNotNull(report.getCategory("benign"));
        assertEquals(1, report.getCategory("benign").getTotalBenign());
    }

    @Test
    void reportIsImmutable() {
        AttackCase a1 = attack("a1", "c");
        List<CaseResult> input = new java.util.ArrayList<>();
        input.add(cr(a1, new GuardrailResult.BlockResult("x"), Verdict.PASS));

        GuardrailTestReport report = GuardrailTestReport.build(input);

        // mutate the source list — report must not change
        input.clear();
        assertEquals(1, report.getResults().size());
        // mutate the returned list — must throw
        boolean threw = false;
        try {
            report.getResults().add(cr(attack("x", "c"), GuardrailResult.PassResult.instance(), Verdict.PASS));
        } catch (UnsupportedOperationException e) {
            threw = true;
        }
        assertTrue(threw, "results list must be unmodifiable");
    }
}
