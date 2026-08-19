package io.nop.xlang.compare;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * corpus v1 解释器基线列：全部单元对拍全绿（单列阶段判定基准 = 列结果 vs 单元声明预期）。
 * 同时固定：类别覆盖矩阵（6 类 × 2 形态）、子集节点白名单（结构性载体注记）、
 * 列缺席记录（静态单元 java/truffle skipped、动态单元 truffle skipped）、同树证据。
 */
public class TestCorpusV1InterpreterBaseline {

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
    public void testInterpreterBaseline(CompareUnit unit) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(CorpusV1.Compiler.INSTANCE);

        CompareUnitReport report = harness.runUnit(unit);
        assertTrue(report.isPassed(), report::toString);
        assertEquals(1, report.getColumnOutcomes().size());

        ColumnOutcome outcome = report.getColumnOutcomes().get(0);
        assertEquals(CompareBackendIds.INTERPRETER, outcome.getBackendId());
        assertNotNull(outcome.getExecution());
        assertTrue(outcome.getExecution().getEvidence().getExecutedArtifact()
                == report.getCompiledTree(), "same tree instance must be executed");

        assertSkipRecords(unit, report);
        assertNodeCategories(unit, report.getCompiledTree());
    }

    private void assertSkipRecords(CompareUnit unit, CompareUnitReport report) {
        Set<String> skippedIds = report.getSkipRecords().stream()
                .map(ColumnSkipRecord::getBackendId).collect(Collectors.toSet());
        if (unit.getKind() == CompareUnitKind.STATIC) {
            assertEquals(Set.of(CompareBackendIds.JAVA, CompareBackendIds.TRUFFLE), skippedIds);
        } else {
            assertEquals(Set.of(CompareBackendIds.TRUFFLE), skippedIds,
                    "dynamic units: java column is not-applicable (no skip record), truffle absence recorded");
        }
        for (ColumnSkipRecord skip : report.getSkipRecords())
            assertEquals(ColumnSkipRecord.REASON_NOT_REGISTERED, skip.getReason());
    }

    private void assertNodeCategories(CompareUnit unit, IExecutableExpression tree) {
        Set<String> visited = visitNodeClassNames(tree);
        for (Set<String> rule : CorpusV1.getRequiredNodeRules(unit.getCategory())) {
            boolean satisfied = rule.stream().anyMatch(required -> visited.stream()
                    .anyMatch(name -> name.equals(required) || name.startsWith(required + "$")));
            assertTrue(satisfied, "unit " + unit.getName() + " of category " + unit.getCategory()
                    + " must contain a node of " + rule + ", visited=" + visited);
        }
        for (String name : visited) {
            assertTrue(CorpusV1.isAllowedNodeClass(name),
                    "unit " + unit.getName() + " contains out-of-subset node " + name
                            + " (category=" + unit.getCategory() + ")");
        }
    }

    public static Set<String> visitNodeClassNames(IExecutableExpression tree) {
        Set<String> names = new HashSet<>();
        tree.visit(expr -> {
            Class<?> enclosing = expr.getClass().getEnclosingClass();
            String name = enclosing == null ? expr.getClass().getSimpleName()
                    : enclosing.getSimpleName() + "$" + expr.getClass().getSimpleName();
            names.add(name);
            return true;
        });
        return names;
    }

    @org.junit.jupiter.api.Test
    public void testCoverageMatrix() {
        List<CompareUnit> units = CorpusV1.units();
        Map<String, Set<CompareUnitKind>> coverage = new LinkedHashMap<>();
        for (String category : CorpusV1.CATEGORIES)
            coverage.put(category, new HashSet<>());
        Set<CompareUnitKind> comboKinds = new HashSet<>();

        for (CompareUnit unit : units) {
            String category = unit.getCategory();
            if (category.equals(CorpusV1.CATEGORY_COMBO)) {
                comboKinds.add(unit.getKind());
            } else {
                assertTrue(coverage.containsKey(category), "unknown category: " + category);
                coverage.get(category).add(unit.getKind());
            }
        }
        for (Map.Entry<String, Set<CompareUnitKind>> entry : coverage.entrySet()) {
            assertEquals(Set.of(CompareUnitKind.STATIC, CompareUnitKind.DYNAMIC), entry.getValue(),
                    "category must be covered by both static and dynamic units: " + entry.getKey());
        }
        assertEquals(Set.of(CompareUnitKind.STATIC, CompareUnitKind.DYNAMIC), comboKinds,
                "combo units must exist for both kinds");
        assertTrue(units.size() >= 14, "corpus v1 must contain at least 14 units (6x2 + 2 combos)");
    }

    @org.junit.jupiter.api.Test
    public void testSchemaFourFields() {
        for (CompareUnit unit : CorpusV1.units()) {
            assertNotNull(unit.getSource(), "source field");
            assertNotNull(unit.getInputVars(), "input scope declaration field");
            assertNotNull(unit.getExpectation(), "expectation field");
            assertNotNull(unit.getKind(), "column applicability field");
            assertTrue(unit.getExpectation().hasReturnValue() || unit.getExpectation().isExpectException(),
                    "expectation must declare return value or exception: " + unit.getName());
            assertNotNull(unit.getSourceLocationPath());
            assertTrue(unit.getSourceLocationPath().length() > 0);
        }
        List<String> exceptionUnits = new ArrayList<>();
        for (CompareUnit unit : CorpusV1.units()) {
            if (unit.getExpectation().isExpectException())
                exceptionUnits.add(unit.getName());
        }
        assertTrue(exceptionUnits.size() >= 1, "at least one exception-semantics unit required");
    }
}
