package io.nop.xlang.compare;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static io.nop.xlang.compare.CorpusV1.CATEGORY_ARITHMETIC;
import static io.nop.xlang.compare.CorpusV1.CATEGORY_METHOD_CALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 差异注入自检（端到端载体）：corpus 单元定义 → 树编译 → 矩阵化执行 → 三层断言 +
 * 身份断言 + 列缺席记录 → FAIL/PASS 判定完整链路。四类分歧注入各 ≥1 例均判 FAIL、
 * 同单元下解释器列 PASS——红/绿可控；断言引擎确实被列执行结果驱动（每例断言具体失败维度）。
 */
public class TestCompareDivergenceInjection {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static CompareUnit unit(String name) {
        return CorpusV1.units().stream().filter(u -> u.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unit not found: " + name));
    }

    private static ExecCompareHarness harnessWith(IEvalBackendColumn injected) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
        harness.registerColumn(injected);
        if (!injected.getBackendId().equals(CompareBackendIds.JAVA)
                && !injected.getBackendId().equals(CompareBackendIds.TRUFFLE))
            harness.registerIdentityRule(new InterpreterIdentityRule(injected.getBackendId()));
        return harness;
    }

    private static ColumnOutcome requireOutcome(CompareUnitReport report, String backendId) {
        return report.getColumnOutcomes().stream().filter(o -> o.getBackendId().equals(backendId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("outcome missing: " + backendId));
    }

    private static void assertRedGreen(CompareUnitReport report, String injectedId, String expectedFailurePart) {
        assertTrue(report.isPassed() == false, "any-divergence must FAIL the unit: " + report);

        ColumnOutcome interpreter = requireOutcome(report, CompareBackendIds.INTERPRETER);
        assertTrue(interpreter.isPassed(), "interpreter column must stay green: " + interpreter.getFailures());

        ColumnOutcome injected = requireOutcome(report, injectedId);
        assertFalse(injected.isPassed(), "injected column must be judged FAIL");
        assertTrue(injected.getFailures().stream().anyMatch(f -> f.contains(expectedFailurePart)),
                "assertion engine must detect [" + expectedFailurePart + "] from execution result, failures="
                        + injected.getFailures());
    }

    @Test
    public void testReturnValueDivergence() {
        CompareUnit unit = unit("arith-plus-static");
        Function<Object, Object> wrongValue = v -> 999;
        CompareUnitReport report = harnessWith(
                InjectedColumns.returnValueTampering("injected-return-value", wrongValue)).runUnit(unit);
        assertRedGreen(report, "injected-return-value", "return-value-mismatch");
        assertTrue(requireOutcome(report, "injected-return-value").getFailures().stream()
                .anyMatch(f -> f.contains("was=Integer(999)")));
    }

    @Test
    public void testReturnTypeDivergence() {
        CompareUnit unit = unit("arith-plus-static");
        Function<Object, Object> wrongType = v -> Long.valueOf(((Number) v).longValue());
        CompareUnitReport report = harnessWith(
                InjectedColumns.returnValueTampering("injected-return-type", wrongType)).runUnit(unit);
        assertRedGreen(report, "injected-return-type", "return-value-mismatch");
        assertTrue(requireOutcome(report, "injected-return-type").getFailures().stream()
                .anyMatch(f -> f.contains("was=Long(7)")), "type divergence like 7L vs 7 must be detected");
    }

    @Test
    public void testSideEffectScopeDivergence() {
        CompareUnit unit = unit("method-static-side-effect-static");
        CompareUnitReport report = harnessWith(
                InjectedColumns.scopePolluter("injected-scope-divergence", "injectedVar")).runUnit(unit);
        assertRedGreen(report, "injected-scope-divergence", "scope-vars-keyset-mismatch");
    }

    @Test
    public void testSideEffectOutputDivergence() {
        CompareUnit unit = unit("arith-plus-static");
        CompareUnitReport report = harnessWith(
                InjectedColumns.outputPolluter("injected-output-divergence", "polluted")).runUnit(unit);
        assertRedGreen(report, "injected-output-divergence", "output-calls-mismatch");
    }

    @Test
    public void testExceptionDivergence() {
        CompareUnit unit = unit("exception-method-static");
        CompareUnitReport report = harnessWith(
                InjectedColumns.exceptionRewriter("injected-exception-divergence")).runUnit(unit);
        assertRedGreen(report, "injected-exception-divergence", "exception-error-code-mismatch");
        assertTrue(requireOutcome(report, "injected-exception-divergence").getFailures().stream()
                .anyMatch(f -> f.contains("exception-location-path-mismatch")));
    }

    @Test
    public void testIdentitySpoofing() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
        harness.registerIdentityRule(new NonInterpreterArtifactIdentityRule(CompareBackendIds.JAVA));
        harness.registerColumn(InjectedColumns.interpreterSpoofing(CompareBackendIds.JAVA));

        CompareUnitReport report = harness.runUnit(unit("method-instance-static"));
        assertFalse(report.isPassed(), report::toString);

        ColumnOutcome interpreter = requireOutcome(report, CompareBackendIds.INTERPRETER);
        assertTrue(interpreter.isPassed(), () -> String.join(";", interpreter.getFailures()));

        ColumnOutcome spoof = requireOutcome(report, CompareBackendIds.JAVA);
        assertFalse(spoof.isPassed());
        assertTrue(spoof.getFailures().stream().anyMatch(f -> f.contains("identity-mismatch")),
                "identity verdict must be made by harness from execution evidence, failures=" + spoof.getFailures());
        assertEquals(1, report.getSkipRecords().size(),
                "java is registered (spoof), so only truffle should be skipped");
    }

    @Test
    public void testCrossColumnDivergenceCounted() {
        CompareUnit unit = unit("arith-plus-static");
        Function<Object, Object> wrongValue = v -> 999;
        CompareUnitReport report = harnessWith(
                InjectedColumns.returnValueTampering("injected-cross", wrongValue)).runUnit(unit);

        ColumnOutcome injected = requireOutcome(report, "injected-cross");
        assertTrue(injected.getFailures().stream()
                .anyMatch(f -> f.contains("cross-column-return-divergence")),
                "with >=2 columns, cross-column comparison layer must also fire, failures=" + injected.getFailures());
    }
}
