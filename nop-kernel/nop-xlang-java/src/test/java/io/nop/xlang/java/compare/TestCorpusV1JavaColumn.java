package io.nop.xlang.java.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.ColumnSkipRecord;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CorpusV1;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.java.gen.EvalMethodConvention;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对拍 java 列激活（roadmap I2 验收第一项）：corpus v1 表达式单元 java 列 vs 解释器列对拍全绿。
 *
 * <p>静态单元：解释器 + java 两列执行（同一棵树实例分列执行），三层断言 + 列间逐项 cross-compare
 * + 身份断言（java 列执行体 = 本单元确定性派生生成类实例，非解释器树）；truffle 列缺席显式记
 * skipped（not-registered），不计入通过。动态单元：java 列不适用（无生成类，I1 列适用性机制，
 * 无 skip 记录），truffle 列缺席显式记录。
 *
 * <p>本测试即端到端链路：树 → 转译器 → 生成源码 → 测试域编译加载（JdkJavaCompiler 内存编译）
 * → 执行 → 三层对拍断言。
 */
public class TestCorpusV1JavaColumn {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static Stream<CompareUnit> units() {
        return CorpusV1.units().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("units")
    public void testJavaColumnVsInterpreter(CompareUnit unit) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
        harness.registerColumn(JavaBackendColumn.INSTANCE);
        harness.registerIdentityRule(new JavaBackendIdentityRule());

        CompareUnitReport report = harness.runUnit(unit);
        assertTrue(report.isPassed(), report::toString);

        Set<String> skippedIds = report.getSkipRecords().stream()
                .map(ColumnSkipRecord::getBackendId).collect(Collectors.toSet());
        for (ColumnSkipRecord skip : report.getSkipRecords())
            assertEquals(ColumnSkipRecord.REASON_NOT_REGISTERED, skip.getReason());

        if (unit.getKind() == CompareUnitKind.STATIC) {
            Set<String> executedIds = report.getColumnOutcomes().stream()
                    .map(ColumnOutcome::getBackendId).collect(Collectors.toSet());
            assertEquals(Set.of(CompareBackendIds.INTERPRETER, CompareBackendIds.JAVA), executedIds,
                    "static unit must be executed by both interpreter and java columns");
            assertEquals(Set.of(CompareBackendIds.TRUFFLE), skippedIds,
                    "static unit: truffle column absence must be explicitly recorded");

            ColumnOutcome javaOutcome = report.getColumnOutcomes().stream()
                    .filter(o -> CompareBackendIds.JAVA.equals(o.getBackendId())).findFirst().orElseThrow();
            assertTrue(javaOutcome.isPassed(), javaOutcome::toString);

            // 身份复核（harness 已依证据判定；此处显式复核执行体 = 生成类实例，非解释器树）
            Object executed = javaOutcome.getExecution().getEvidence().getExecutedArtifact();
            assertNotSame(report.getCompiledTree(), executed, "java column must not execute the interpreter tree");
            String expected = EvalMethodConvention.GENERATED_PACKAGE + '.'
                    + EvalMethodConvention.generatedClassName(unit.getSourceLocationPath());
            assertEquals(expected, executed.getClass().getName());
        } else {
            assertEquals(1, report.getColumnOutcomes().size(),
                    "dynamic unit: only interpreter column applies (java not-applicable, no skip record)");
            assertEquals(CompareBackendIds.INTERPRETER, report.getColumnOutcomes().get(0).getBackendId());
            assertEquals(Set.of(CompareBackendIds.TRUFFLE), skippedIds,
                    "dynamic unit: truffle column absence must be explicitly recorded");
        }
    }
}
