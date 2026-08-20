package io.nop.xlang.truffle.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.ColumnSkipRecord;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CorpusCoverageB;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对拍 truffle 列扩展到 corpus 覆盖 B（roadmap I7 验收第一项）：函数/闭包、控制流、输出/节点
 * 生成三族单元 truffle 列 vs 解释器列对拍全绿（三层断言 + 列间 cross-compare + 身份断言 =
 * 翻译 AST 经 CallTarget 执行；模板单元含输出缓冲副作用比对——$out 通路经
 * {@code XLangContext} 输出缓冲线程绑定机制；单 Context 串行形态，SHARED 超集用法）。
 *
 * <p>静态单元：解释器 + truffle 两列执行（同一棵树实例分列执行）；java 列缺席显式记
 * skipped（not-registered，java 列在 nop-xlang-java 模块）。动态单元：解释器 + truffle 两列
 * 执行，java 列为"不适用"（I1 列适用性机制）。
 *
 * <p>本测试即覆盖 B 的端到端链路载体（Phase 3 端到端验证项）：corpus B 单元（含模板单元
 * `$out` 输出通路）→ 树翻译 → Truffle AST → CallTarget 执行 → 三层对拍断言
 * （stock JDK 21 形态）。
 */
public class TestCorpusCoverageBTruffleColumn {

    private static TruffleBackendColumn column;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        column = TruffleBackendColumn.open();
    }

    @AfterAll
    public static void destroy() {
        column.close();
        CoreInitialization.destroy();
    }

    static Stream<CompareUnit> units() {
        return CorpusCoverageB.units().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("units")
    public void testTruffleColumnVsInterpreter(CompareUnit unit) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusCoverageB.Compiler.INSTANCE);
        harness.registerColumn(column);
        harness.registerIdentityRule(new TruffleBackendIdentityRule());

        CompareUnitReport report = harness.runUnit(unit);
        assertTrue(report.isPassed(), report::toString);

        Set<String> executedIds = report.getColumnOutcomes().stream()
                .map(ColumnOutcome::getBackendId).collect(Collectors.toSet());
        assertEquals(Set.of(CompareBackendIds.INTERPRETER, CompareBackendIds.TRUFFLE), executedIds,
                "both static and dynamic units must be executed by interpreter and truffle columns");

        Set<String> skippedIds = report.getSkipRecords().stream()
                .map(ColumnSkipRecord::getBackendId).collect(Collectors.toSet());
        if (unit.getKind() == CompareUnitKind.STATIC) {
            assertEquals(Set.of(CompareBackendIds.JAVA), skippedIds,
                    "static unit: java column absence must be explicitly recorded as skipped");
            for (ColumnSkipRecord skip : report.getSkipRecords())
                assertEquals(ColumnSkipRecord.REASON_NOT_REGISTERED, skip.getReason());
        } else {
            assertEquals(Set.of(), skippedIds,
                    "dynamic unit: java column is not-applicable (no skip record), not absent");
        }

        ColumnOutcome truffleOutcome = report.getColumnOutcomes().stream()
                .filter(o -> CompareBackendIds.TRUFFLE.equals(o.getBackendId())).findFirst().orElseThrow();
        assertTrue(truffleOutcome.isPassed(), truffleOutcome::toString);

        // 身份复核（harness 已依证据判定；此处显式复核执行体 = 翻译 AST 根节点，非解释器树）——
        // 接线验证：truffle 列在覆盖 B 单元上持续经 CallTarget 执行翻译 AST（非解释器兜底）
        Object executed = truffleOutcome.getExecution().getEvidence().getExecutedArtifact();
        assertNotSame(report.getCompiledTree(), executed, "truffle column must not execute the interpreter tree");
        XLangRootNode root = (XLangRootNode) executed;
        assertSame(report.getCompiledTree(), root.getSourceTree(),
                "translated root must be derived from the same compiled tree instance");
    }

    /**
     * 语料缺口核验（Phase 3 Phase 1 语料要求逐项闭合的可观测锚点）：corpus B 单元在册清单
     * 覆盖多态/同函数重复调用语料（dyn-closure + fn-* 族）、控制流边界语料（ctrl-* 5 单元 +
     * exception 2）、闭包捕获语料（fn-closure-cell 可变 slot round-trip + fn-arrow-call/
     * dyn-iife 捕获时载体 + fn-local-call 局部函数调用形态）、输出换缓冲语料
     * （tpl-collect-* 3 单元）。逐项闭合记录见 plan Execution Notes / 当日 log。
     */
    @org.junit.jupiter.api.Test
    public void testCorpusBCoversRequiredGapCorpora() {
        Set<String> names = CorpusCoverageB.units().stream()
                .map(CompareUnit::getName).collect(Collectors.toSet());
        // 同函数重复调用 + 捕获时载体 + 局部函数调用形态 + 可变 slot cell
        assertTrue(names.containsAll(java.util.List.of("fn-local-call-static-b", "fn-closure-cell-static-b",
                "fn-arrow-call-static-b", "dyn-closure-dynamic-b", "dyn-iife-dynamic-b")));
        // 控制流边界（函数内 return/break/continue + 嵌套循环 + 异常单元）
        assertTrue(names.containsAll(java.util.List.of("ctrl-for-break-continue-static-b",
                "ctrl-loops-static-b", "ctrl-switch-static-b", "ctrl-forin-static-b",
                "exception-throw-static-b", "exception-fn-throw-static-b")));
        // 输出换缓冲（collect 三形态 + 模板 $out 通路）
        assertTrue(names.containsAll(java.util.List.of("tpl-collect-text-static-b",
                "tpl-collect-node-static-b", "tpl-collect-sql-static-b", "tpl-text-static-b")));
        // 局部函数调用形态承载单元（I4 Phase 1 §2 裁定语料）
        assertEquals(java.util.Set.of("fn-local-call", "exception-fn-throw"),
                CorpusCoverageB.LOCAL_FUNCTION_FORM_UNITS);
    }
}
