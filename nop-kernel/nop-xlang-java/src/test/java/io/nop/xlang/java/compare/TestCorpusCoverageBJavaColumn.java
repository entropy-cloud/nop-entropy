package io.nop.xlang.java.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.ColumnSkipRecord;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CorpusCoverageB;
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
 * 对拍 java 列扩展到 corpus 覆盖 B（roadmap I4 验收第一项）：B 三族类别单元
 * java 列 vs 解释器列对拍全绿（三层断言 + 列间 cross-compare + 身份断言）。
 *
 * <p><b>I2 移交闭合</b>：模板单元（含输出语义）java 列按 {@code EvalMethodConvention.OUT_PARAM}
 * 契约生成第二隐参 {@code IEvalOutput $out}，以 harness 录制缓冲为实参直接反射调用入口方法
 * （参数个数定位）——三层断言的输出缓冲比对（输出 API 调用序列 + 节点事件）即 `$out`
 * 契约执行路径验证。动态单元 java 列不适用（I1 列适用性机制，无 skip 记录）；
 * truffle 列缺席显式记 skipped（not-registered，I7 前不消费覆盖 B）。
 *
 * <p>本测试即覆盖 B 的端到端链路载体（plan Phase 3 端到端验证项）：corpus B 单元 → 树编译 →
 * 转译器 → 生成源码（`$out` 第二隐参）→ 测试域编译加载（JdkJavaCompiler 内存编译）→ 执行 →
 * 三层对拍断言（含输出缓冲）。
 */
public class TestCorpusCoverageBJavaColumn {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static Stream<CompareUnit> units() {
        return CorpusCoverageB.units().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("units")
    public void testJavaColumnVsInterpreter(CompareUnit unit) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusCoverageB.Compiler.INSTANCE);
        harness.registerColumn(JavaBackendColumn.INSTANCE);
        harness.registerIdentityRule(new JavaBackendIdentityRule());

        CompareUnitReport report = harness.runUnit(unit);
        assertTrue(report.isPassed(), report::toString);

        for (ColumnSkipRecord skip : report.getSkipRecords())
            assertEquals(ColumnSkipRecord.REASON_NOT_REGISTERED, skip.getReason());

        Set<String> skippedIds = report.getSkipRecords().stream()
                .map(ColumnSkipRecord::getBackendId).collect(Collectors.toSet());

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
