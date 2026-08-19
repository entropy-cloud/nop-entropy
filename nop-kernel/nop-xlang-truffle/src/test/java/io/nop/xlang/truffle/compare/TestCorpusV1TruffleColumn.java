package io.nop.xlang.truffle.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.ColumnSkipRecord;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CorpusV1;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.frame.FrameLayoutMapper.SlotMeta;
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
 * 对拍 truffle 列激活（roadmap I5 验收第一项）：corpus v1 表达式单元 truffle 列 vs 解释器列
 * 对拍全绿（EXCLUSIVE 过渡形态；含身份断言 = 翻译 AST 经 CallTarget 执行）。
 *
 * <p>静态单元：解释器 + truffle 两列执行（同一棵树实例分列执行），三层断言 + 列间逐项
 * cross-compare + 身份断言；java 列缺席显式记 skipped（not-registered），不计入通过。
 * 动态单元：解释器 + truffle 两列执行，java 列为"不适用"（I1 列适用性机制：不在期望列集合，
 * 无 skip 记录——与"缺席"显式区分）。
 *
 * <p>本测试即端到端链路：树 → 翻译器 → Truffle AST → CallTarget 执行 → 三层对拍断言
 * （stock JDK 21 形态）。每单元顺带打印帧布局 kind 推断统计（子集内可推断比例的 log 记录来源）。
 */
public class TestCorpusV1TruffleColumn {

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
        return CorpusV1.units().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("units")
    public void testTruffleColumnVsInterpreter(CompareUnit unit) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);
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

        // 身份复核（harness 已依证据判定；此处显式复核执行体 = 翻译 AST 根节点，非解释器树）
        Object executed = truffleOutcome.getExecution().getEvidence().getExecutedArtifact();
        assertNotSame(report.getCompiledTree(), executed, "truffle column must not execute the interpreter tree");
        XLangRootNode root = (XLangRootNode) executed;
        assertSame(report.getCompiledTree(), root.getSourceTree(),
                "translated root must be derived from the same compiled tree instance");

        // 帧布局 kind 推断统计（log 记录来源；全量覆盖率归 I6 后续实测）
        FrameLayout layout = root.getFrameLayout();
        int inferred = 0;
        for (int i = 0; i < layout.getSlotCount(); i++) {
            SlotMeta meta = layout.getSlot(i);
            if (meta.isKindInferred())
                inferred++;
        }
        System.out.println("[truffle-frame-stats] unit=" + unit.getName() + " slots=" + layout.getSlotCount()
                + " kindInferred=" + inferred);
    }
}
