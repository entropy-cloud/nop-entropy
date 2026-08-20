package io.nop.xlang.truffle.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.ColumnSkipRecord;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CorpusCoverageA;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.ObjectBindingAssignExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ReferenceSelfAssignExecutable;
import io.nop.xlang.exec.ReferenceSelfDecExecutable;
import io.nop.xlang.exec.ReferenceSelfIncExecutable;
import io.nop.xlang.exec.RenewReferenceExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.ScopeSelfDecExecutable;
import io.nop.xlang.exec.ScopeSelfIncExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.frame.FrameLayoutMapper;
import io.nop.xlang.truffle.frame.FrameLayoutMapper.KindReason;
import io.nop.xlang.truffle.frame.FrameLayoutMapper.SlotMeta;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对拍 truffle 列扩展到 corpus 覆盖 A（roadmap I6 验收第一项）：A 五族 + 残余类别单元
 * truffle 列 vs 解释器列对拍全绿（三层断言 + 列间 cross-compare + 身份断言 = 翻译 AST 经
 * CallTarget 执行；单 Context 串行形态，SHARED 超集用法）。
 *
 * <p>静态单元：解释器 + truffle 两列执行（同一棵树实例分列执行）；java 列缺席显式记
 * skipped（not-registered），不计入通过。动态单元：解释器 + truffle 两列执行，java 列为
 * "不适用"（I1 列适用性机制：不在期望列集合，无 skip 记录）。
 *
 * <p>本测试即覆盖 A 的端到端链路载体（Phase 3 端到端验证项）：corpus A 单元 → 树翻译 →
 * Truffle AST → CallTarget 执行 → 三层对拍断言（stock JDK 21 形态）。每单元顺带打印：
 * <ul>
 * <li>`[truffle-frame-stats]`——帧 kind 推断全量覆盖率实测（I5 移交第一项闭合的统计口径：
 *     逐单元可复跑，kind 未推断原因按 {@link KindReason} 五分类）；</li>
 * <li>`[truffle-q3-stats]`——Q3 残余路径使用统计（作用域链访问的 slot 化 vs scope 链查找
 *     计数占比，逐单元可复跑）。</li>
 * </ul>
 */
public class TestCorpusCoverageATruffleColumn {

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
        return CorpusCoverageA.units().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("units")
    public void testTruffleColumnVsInterpreter(CompareUnit unit) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusCoverageA.Compiler.INSTANCE);
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
        // 接线验证：truffle 列在覆盖 A 单元上持续经 CallTarget 执行翻译 AST（非解释器兜底）
        Object executed = truffleOutcome.getExecution().getEvidence().getExecutedArtifact();
        assertNotSame(report.getCompiledTree(), executed, "truffle column must not execute the interpreter tree");
        XLangRootNode root = (XLangRootNode) executed;
        assertSame(report.getCompiledTree(), root.getSourceTree(),
                "translated root must be derived from the same compiled tree instance");

        // ---- 帧 kind 推断全量覆盖率实测（I5 移交第一项：逐单元可复跑输出 + 原因分类） ----
        FrameLayout layout = root.getFrameLayout();
        Map<KindReason, Integer> reasons = new EnumMap<>(KindReason.class);
        int inferred = 0;
        for (int i = 0; i < layout.getSlotCount(); i++) {
            SlotMeta meta = layout.getSlot(i);
            reasons.merge(meta.getKindReason(), 1, Integer::sum);
            if (meta.isKindInferred())
                inferred++;
        }
        System.out.println("[truffle-frame-stats] unit=" + unit.getName() + " slots=" + layout.getSlotCount()
                + " kindInferred=" + inferred + " reasons=" + reasons);

        // ---- Q3 残余路径使用统计（slot 化访问 vs scope 链查找节点访问计数，逐单元可复跑） ----
        Q3AccessStats stats = new Q3AccessStats(slotNamesOf(layout));
        report.getCompiledTree().visit(stats);
        System.out.println("[truffle-q3-stats] unit=" + unit.getName()
                + " slotAccesses=" + stats.slotAccesses + " scopeLookups=" + stats.scopeLookups);
    }

    private static String[] slotNamesOf(FrameLayout layout) {
        String[] names = new String[layout.getSlotCount()];
        for (int i = 0; i < layout.getSlotCount(); i++)
            names[i] = layout.getSlot(i).getName();
        return names;
    }

    /**
     * 作用域链访问族计数器：slot 化路径（帧 slot 承载）vs scope 链查找路径（Q3 残余）。
     */
    private static final class Q3AccessStats implements IExecutableExpressionVisitor {
        private final String[] slotNames;

        int slotAccesses;

        int scopeLookups;

        Q3AccessStats(String[] slotNames) {
            this.slotNames = slotNames;
        }

        @Override
        public boolean onVisitExpr(IExecutableExpression expr) {
            if (expr instanceof SlotIdentifierExecutable || expr instanceof SlotAssignExecutable
                    || expr instanceof ReferenceIdentifierExecutable
                    || expr instanceof ReferenceAssignExecutable
                    || expr instanceof ReferenceSelfAssignExecutable
                    || expr instanceof ReferenceSelfIncExecutable
                    || expr instanceof ReferenceSelfDecExecutable
                    || expr instanceof RenewReferenceExecutable
                    || expr instanceof SelfAssignExecutable
                    || expr instanceof SelfIncExecutable || expr instanceof SelfDecExecutable
                    || expr instanceof VarStatusExecutable
                    || expr instanceof io.nop.xlang.exec.InitRefSlotExecutable
                    || expr instanceof io.nop.xlang.exec.EnhanceRefSlotExecutable
                    || expr instanceof BindVarExecutable) {
                slotAccesses++;
            } else if (expr instanceof ScopeIdentifierExecutable || expr instanceof GlobalVarExecutable
                    || expr instanceof ScopeAssignExecutable || expr instanceof ScopeSelfAssignExecutable
                    || expr instanceof ScopeSelfIncExecutable || expr instanceof ScopeSelfDecExecutable) {
                scopeLookups++;
            } else if (expr instanceof DebugIdentifierExecutable) {
                // 按名查找序：入口帧可解析 = slot 化；否则 scope 链查找
                String varName = ((DebugIdentifierExecutable) expr).getVarName();
                boolean inFrame = false;
                for (String name : slotNames) {
                    if (varName.equals(name)) {
                        inFrame = true;
                        break;
                    }
                }
                if (inFrame)
                    slotAccesses++;
                else
                    scopeLookups++;
            } else if (expr instanceof ArrayBindingAssignExecutable) {
                countBindings(((ArrayBindingAssignExecutable) expr).getElementBindings(),
                        ((ArrayBindingAssignExecutable) expr).getRestBinding());
            } else if (expr instanceof ObjectBindingAssignExecutable) {
                countBindings(((ObjectBindingAssignExecutable) expr).getPropBindings(),
                        ((ObjectBindingAssignExecutable) expr).getRestBinding());
            }
            return true;
        }

        private void countBindings(AssignIdentifier[] bindings, AssignIdentifier rest) {
            if (bindings != null) {
                for (AssignIdentifier id : bindings)
                    countBinding(id);
            }
            if (rest != null)
                countBinding(rest);
        }

        private void countBinding(AssignIdentifier id) {
            if (id.getVarSlot() >= 0)
                slotAccesses++;
            else
                scopeLookups++;
        }
    }
}
