package io.nop.xlang.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * corpus 覆盖 B 解释器基线列（I4 Phase 3）：B 三族类别单元在解释器列全绿
 * （单列阶段判定基准 = 列结果 vs 单元声明预期——含模板单元的输出调用序列比对，
 * 即 I2 移交的 `$out` 契约在解释器侧的基准），并断言类别节点规则、强成员覆盖、
 * 局部函数调用形态计数与 B 范围节点白名单。
 */
public class TestCorpusCoverageBInterpreterBaseline {

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
        harness.setCompiler(CorpusCoverageB.Compiler.INSTANCE);

        List<CompareUnit> units = CorpusCoverageB.units();
        assertFalse(units.isEmpty());
        for (CompareUnit unit : units) {
            CompareUnitReport report = harness.runUnit(unit);
            assertTrue(report.isPassed(), report::toString);
        }
    }

    @Test
    public void testCategoryNodeRulesHold() {
        for (String category : CorpusCoverageB.CATEGORIES) {
            boolean anyUnit = false;
            for (CompareUnit unit : CorpusCoverageB.units()) {
                if (!category.equals(unit.getCategory()))
                    continue;
                anyUnit = true;
                IExecutableExpression tree = CorpusCoverageB.Compiler.INSTANCE.compile(unit);
                Set<String> classes = collectNodeClasses(tree);
                for (String name : classes) {
                    assertTrue(CorpusCoverageB.isAllowedNodeClass(name),
                            "unit " + unit.getName() + " contains out-of-scope node: " + name);
                }
                assertTrue(satisfiesAllRules(classes, CorpusCoverageB.getRequiredNodeRules(category)),
                        "unit " + unit.getName() + " of category " + category
                                + " satisfies no required node rule; classes=" + classes);
            }
            assertTrue(anyUnit, "category has no unit: " + category);
        }
    }

    /** 三族覆盖证据：每族静态形态 ≥1（硬要求）；每族至少一个单元含强成员（防规则平凡满足）。 */
    @Test
    public void testEveryCategoryHasStaticUnitAndStrongMember() {
        for (String category : CorpusCoverageB.CATEGORIES) {
            assertTrue(CorpusCoverageB.units().stream().anyMatch(
                            u -> category.equals(u.getCategory()) && u.getKind() == CompareUnitKind.STATIC),
                    "category must have at least one static unit: " + category);
            Set<String> strong = CorpusCoverageB.getStrongMembers(category);
            boolean strongHit = false;
            for (CompareUnit unit : CorpusCoverageB.units()) {
                if (!category.equals(unit.getCategory()))
                    continue;
                Set<String> classes = collectNodeClasses(CorpusCoverageB.Compiler.INSTANCE.compile(unit));
                for (String member : strong) {
                    if (containsClass(classes, member)) {
                        strongHit = true;
                        break;
                    }
                }
            }
            assertTrue(strongHit, "category must have at least one unit containing a strong member: " + category);
        }
    }

    /**
     * 局部函数调用形态（非根 CallFuncExecutable，I4 Phase 1 §2 裁定语料）：
     * 承载单元的树含 ≥2 个 CallFuncExecutable（程序入口包装 + 非根调用点）。
     */
    @Test
    public void testLocalFunctionCallFormUnits() {
        for (String name : CorpusCoverageB.LOCAL_FUNCTION_FORM_UNITS) {
            CompareUnit unit = CorpusCoverageB.units().stream()
                    .filter(u -> u.getName().equals(name + "-static-b")).findFirst().orElseThrow();
            Map<String, Integer> counts = new LinkedHashMap<>();
            CorpusCoverageB.Compiler.INSTANCE.compile(unit).visit(new IExecutableExpressionVisitor() {
                @Override
                public boolean onVisitExpr(IExecutableExpression expr) {
                    counts.merge(expr.getClass().getSimpleName(), 1, Integer::sum);
                    return true;
                }
            });
            assertTrue(counts.getOrDefault("CallFuncExecutable", 0) >= 2,
                    "local function form unit must contain root entry + non-root call: " + unit.getName()
                            + ", counts=" + counts);
        }
    }

    /** 异常语义单元 ≥1（错误码 + 预期源位置，I1 口径）。 */
    @Test
    public void testExceptionUnitsExist() {
        assertTrue(CorpusCoverageB.units().stream().anyMatch(
                u -> u.getExpectation().isExpectException() && u.getExpectation().getExpectedErrorLocation() != null));
    }

    /** 模板单元（$out 通路）≥1 且其期望为输出调用序列（I2 移交闭合载体）。 */
    @Test
    public void testTemplateUnitsWithOutputCallsExist() {
        assertTrue(CorpusCoverageB.units().stream().anyMatch(
                u -> u.getKind() == CompareUnitKind.STATIC && u.getExpectation().hasExpectedOutputCalls()));
    }

    /**
     * 动态产生缺席记录（显式，非静默跳过）：控制流语句族与输出族不可经动态编译出口产生，
     * EscapeOutput 静态亦不可低成本产生（escapeXml 未注册）——矩阵合成树覆盖的对应关系
     * 见本类 javadoc 与 CorpusCoverageB javadoc。
     */
    @Test
    public void testDynamicAbsenceRecorded() {
        // 动态单元全部为函数族（full-expr 可产生 IIFE/闭包值）；控制流/输出族缺席即上述记录的体现
        Set<String> dynamicCategories = new TreeSet<>();
        for (CompareUnit unit : CorpusCoverageB.units()) {
            if (unit.getKind() == CompareUnitKind.DYNAMIC)
                dynamicCategories.add(unit.getCategory());
        }
        assertTrue(dynamicCategories.contains(CorpusCoverageB.CATEGORY_FUNCTION_CLOSURE),
                "function family must have dynamic units (IIFE producible)");
        assertFalse(dynamicCategories.contains(CorpusCoverageB.CATEGORY_CONTROL_FLOW),
                "control-flow statements are not producible via dynamic exits (recorded absence)");
        assertFalse(dynamicCategories.contains(CorpusCoverageB.CATEGORY_OUTPUT_NODE_GEN),
                "output nodes are not producible via dynamic exits (recorded absence)");
    }

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
