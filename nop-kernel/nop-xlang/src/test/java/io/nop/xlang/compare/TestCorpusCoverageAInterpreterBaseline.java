package io.nop.xlang.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * corpus 覆盖 A 解释器基线列（I3 Phase 3）：A 五族 + 残余类别单元在解释器列全绿
 * （单列阶段判定基准 = 列结果 vs 单元声明预期），并断言类别节点规则（产生路径证据）
 * 与 A 范围节点白名单（无 B 族节点越界）。
 */
public class TestCorpusCoverageAInterpreterBaseline {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testInterpreterBaselineAllGreen() {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusCoverageA.Compiler.INSTANCE);

        List<CompareUnit> units = CorpusCoverageA.units();
        assertFalse(units.isEmpty());
        for (CompareUnit unit : units) {
            CompareUnitReport report = harness.runUnit(unit);
            assertTrue(report.isPassed(), report::toString);
        }
    }

    @Test
    public void testCategoryNodeRulesHold() {
        for (String category : CorpusCoverageA.CATEGORIES) {
            boolean anyUnit = false;
            for (CompareUnit unit : CorpusCoverageA.units()) {
                if (!category.equals(unit.getCategory()))
                    continue;
                anyUnit = true;
                IExecutableExpression tree = CorpusCoverageA.Compiler.INSTANCE.compile(unit);
                Set<String> classes = collectNodeClasses(tree);
                for (String name : classes) {
                    assertTrue(CorpusCoverageA.isAllowedNodeClass(name),
                            "unit " + unit.getName() + " contains out-of-scope node: " + name);
                }
                assertTrue(satisfiesAllRules(classes, CorpusCoverageA.getRequiredNodeRules(category)),
                        "unit " + unit.getName() + " of category " + category
                                + " satisfies no required node rule; classes=" + classes);
            }
            assertTrue(anyUnit, "category has no unit: " + category);
        }
    }

    /** 五族覆盖证据：每族静态形态 ≥1（硬要求）。 */
    @Test
    public void testEveryCategoryHasStaticUnit() {
        for (String category : CorpusCoverageA.CATEGORIES) {
            assertTrue(CorpusCoverageA.units().stream().anyMatch(
                            u -> category.equals(u.getCategory()) && u.getKind() == CompareUnitKind.STATIC),
                    "category must have at least one static unit: " + category);
        }
    }

    /** 异常语义单元 ≥1（错误码 + 预期源位置，I1 口径）。 */
    @Test
    public void testExceptionUnitsExist() {
        assertTrue(CorpusCoverageA.units().stream().anyMatch(
                u -> u.getExpectation().isExpectException() && u.getExpectation().getExpectedErrorLocation() != null));
    }

    /**
     * 规则语义与 {@code TestCorpusV1InterpreterBaseline#assertNodeCategories} 同口径：
     * 每条规则 = 一组等价类，树含其中任一成员即满足该规则（类别内全部规则均须满足）。
     */
    private static boolean satisfiesAllRules(Set<String> classes, List<Set<String>> rules) {
        for (Set<String> rule : rules) {
            boolean any = false;
            for (String name : rule) {
                if (containsClass(classes, name)) {
                    any = true;
                    break;
                }
            }
            if (!any)
                return false;
        }
        return true;
    }

    private static boolean containsClass(Set<String> classes, String simpleName) {
        for (String name : classes) {
            if (name.equals(simpleName) || name.startsWith(simpleName + '$'))
                return true;
        }
        return false;
    }

    static Set<String> collectNodeClasses(IExecutableExpression tree) {
        TreeSet<String> classes = new TreeSet<>();
        if (tree == null)
            return classes;
        tree.visit(new IExecutableExpressionVisitor() {
            @Override
            public boolean onVisitExpr(IExecutableExpression expr) {
                // 嵌套类带宿主前缀命名（与 TestCorpusV1InterpreterBaseline#visitNodeClassNames 同口径，
                // 如 SeqExecutable$SimpleSeqExecutable 归文件级基线 SeqExecutable）
                Class<?> enclosing = expr.getClass().getEnclosingClass();
                String name = enclosing == null ? expr.getClass().getSimpleName()
                        : enclosing.getSimpleName() + "$" + expr.getClass().getSimpleName();
                classes.add(name);
                return true;
            }
        });
        return new LinkedHashSet<>(classes);
    }
}
