package io.nop.xlang.compare;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 列缺席机制测试：缺席的期望列显式记 skipped（含原因）、不计入通过计数；
 * 动态单元 java 列"不适用"不产生 skip 记录，truffle 缺席仍记录。
 */
public class TestColumnAbsenceRecording {

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

    @Test
    public void testStaticUnitJavaAndTruffleSkipped() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
        CompareUnitReport report = harness.runUnit(unit("arith-plus-static"));

        Set<String> skipped = report.getSkipRecords().stream()
                .map(ColumnSkipRecord::getBackendId).collect(Collectors.toSet());
        assertEquals(Set.of(CompareBackendIds.JAVA, CompareBackendIds.TRUFFLE), skipped);
        assertTrue(report.getSkipRecords().stream()
                .allMatch(s -> s.getReason().equals(ColumnSkipRecord.REASON_NOT_REGISTERED)));
        assertEquals(1, report.getColumnOutcomes().size(),
                "skipped columns must not be counted as executed/passed outcomes");
        assertTrue(report.isPassed(), report::toString);
    }

    @Test
    public void testDynamicUnitTruffleSkippedJavaNotApplicable() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
        CompareUnitReport report = harness.runUnit(unit("arith-plus-dynamic"));

        assertEquals(1, report.getSkipRecords().size());
        assertEquals(CompareBackendIds.TRUFFLE, report.getSkipRecords().get(0).getBackendId());
        assertEquals(ColumnSkipRecord.REASON_NOT_REGISTERED, report.getSkipRecords().get(0).getReason());
        assertTrue(report.getSkipRecords().stream()
                .noneMatch(s -> s.getBackendId().equals(CompareBackendIds.JAVA)),
                "java column is not-applicable for dynamic units (structurally no generated class), no skip record");
    }

    @Test
    public void testRegisteredColumnWithoutCapabilityRecordedSkipped() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
        harness.registerIdentityRule(new NonInterpreterArtifactIdentityRule(CompareBackendIds.JAVA));
        harness.registerColumn(noCapabilityJavaColumn());

        CompareUnitReport report = harness.runUnit(unit("arith-plus-static"));
        ColumnSkipRecord javaSkip = report.getSkipRecords().stream()
                .filter(s -> s.getBackendId().equals(CompareBackendIds.JAVA)).findFirst()
                .orElseThrow();
        assertEquals(ColumnSkipRecord.REASON_CAPABILITY_NOT_DECLARED, javaSkip.getReason());
        assertTrue(report.getSkipRecords().stream()
                .anyMatch(s -> s.getBackendId().equals(CompareBackendIds.TRUFFLE)
                        && s.getReason().equals(ColumnSkipRecord.REASON_NOT_REGISTERED)));
        assertEquals(1, report.getColumnOutcomes().size());
        assertTrue(report.isPassed(), report::toString);
    }

    @Test
    public void testNoExecutedColumnMeansFail() {
        ExecCompareHarness harness = new ExecCompareHarness();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
        CompareUnitReport report = harness.runUnit(unit("arith-plus-static"));

        assertTrue(report.getColumnOutcomes().isEmpty());
        assertFalse(report.isPassed(), "unit with no executed column must not pass (skips never count as pass)");
        assertEquals(3, report.getSkipRecords().size(), "interpreter+java+truffle all absent are recorded");
    }

    private IEvalBackendColumn noCapabilityJavaColumn() {
        return new IEvalBackendColumn() {
            @Override
            public String getBackendId() {
                return CompareBackendIds.JAVA;
            }

            @Override
            public boolean isSupportsStaticUnits() {
                return false;
            }

            @Override
            public boolean isSupportsDynamicUnits() {
                return false;
            }

            @Override
            public BackendExecutionResult execute(BackendExecRequest request) {
                throw new IllegalStateException("column without declared capability must never be executed");
            }
        };
    }
}
