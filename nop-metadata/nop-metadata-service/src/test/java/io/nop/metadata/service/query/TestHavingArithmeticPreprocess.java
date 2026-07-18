package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaTable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多列算术 having 预处理单元测试（plan 2026-07-18-1500-2 Phase 1：#25 新功能测试）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>合法算术表达式 name→aggSql 替换正确（单表 + JOIN 限定名）；</li>
 *   <li>未选定 measure name → 显式失败 {@code ERR_AGGR_HAVING_UNKNOWN_NAME}；</li>
 *   <li>expression 型 measure（aggSql 含 {@code ?}）被 arithmetic 引用 →
 *       显式失败 {@code ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED}（{@code ?} 安全边界沿用 D12.4）；</li>
 *   <li>不安全关键字（{@code DROP}）→ 显式失败 {@code ERR_AGGR_HAVING_EXPR_UNSAFE}；</li>
 *   <li>parse 失败（未闭合括号）→ 显式失败 {@code ERR_AGGR_HAVING_EXPR_UNPARSEABLE}；</li>
 *   <li>字面量禁止裁定 → 显式失败 {@code ERR_AGGR_HAVING_EXPR_UNSAFE}（reason 含 literals-not-allowed）；</li>
 *   <li>TreeBean 预处理：{@code expr} leaf 改写 {@code name} 为最终 SQL，递归 and/or/not 子树。</li>
 * </ul>
 *
 * <p>不验证 SQL 真实执行（属端到端测试，见 {@code TestNopMetaAggregationBizModel}）。
 */
public class TestHavingArithmeticPreprocess {

    private static NopMetaTable table() {
        NopMetaTable t = new NopMetaTable();
        t.setMetaTableId("meta-table-arithmetic-test");
        return t;
    }

    private static Map<String, String> nameToExpr(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private static TreeBean exprLeaf(String op, String expr, Object value) {
        TreeBean leaf = new TreeBean(op);
        leaf.setAttr(MetaAggregationExecutor.HAVING_EXPR_ATTR, expr);
        leaf.setAttr(FilterBeanConstants.FILTER_ATTR_VALUE, value);
        return leaf;
    }

    /** 合法算术表达式：name→aggSql 替换正确。 */
    @Test
    public void testSubstituteArithmeticOfTwoMeasures() {
        Map<String, String> names = nameToExpr(
                "sumA", "SUM(AMOUNT)",
                "sumB", "SUM(DISCOUNT)");
        String sql = MetaAggregationExecutor.substituteAndValidateHavingExpr(
                "sumA - sumB", names, table(),
                Arrays.asList("sumA", "sumB"), Arrays.asList());
        assertEquals("SUM(AMOUNT) - SUM(DISCOUNT)", sql,
                "substitution: sumA - sumB → SUM(AMOUNT) - SUM(DISCOUNT)");
    }

    /** 合法算术表达式：JOIN 路径 qualified 名（l./r. 前缀）。 */
    @Test
    public void testSubstituteArithmeticJoinQualified() {
        Map<String, String> names = nameToExpr(
                "leftM", "SUM(l.AMOUNT)",
                "rightM", "SUM(r.QTY)");
        String sql = MetaAggregationExecutor.substituteAndValidateHavingExpr(
                "leftM - rightM", names, table(),
                Arrays.asList("leftM", "rightM"), Arrays.asList());
        assertEquals("SUM(l.AMOUNT) - SUM(r.QTY)", sql,
                "JOIN substitution: leftM - rightM → SUM(l.AMOUNT) - SUM(r.QTY)");
    }

    /** 复杂算术组合：a / b * c。 */
    @Test
    public void testSubstituteComplexArithmetic() {
        Map<String, String> names = nameToExpr(
                "a", "SUM(A)",
                "b", "SUM(B)",
                "c", "SUM(C)");
        String sql = MetaAggregationExecutor.substituteAndValidateHavingExpr(
                "a / b * c", names, table(),
                Arrays.asList("a", "b", "c"), Arrays.asList());
        assertEquals("SUM(A) / SUM(B) * SUM(C)", sql,
                "complex substitution: a / b * c → SUM(A) / SUM(B) * SUM(C)");
    }

    /** 括号分组的算术表达式。 */
    @Test
    public void testSubstituteParenthesizedArithmetic() {
        Map<String, String> names = nameToExpr(
                "a", "SUM(A)",
                "b", "SUM(B)",
                "c", "SUM(C)");
        String sql = MetaAggregationExecutor.substituteAndValidateHavingExpr(
                "(a + b) - c", names, table(),
                Arrays.asList("a", "b", "c"), Arrays.asList());
        assertEquals("(SUM(A) + SUM(B)) - SUM(C)", sql,
                "parenthesized substitution");
    }

    /** 未选定 measure name → 显式失败 ERR_AGGR_HAVING_UNKNOWN_NAME。 */
    @Test
    public void testUnknownNameFailsExplicitly() {
        Map<String, String> names = nameToExpr("sumA", "SUM(AMOUNT)");
        NopException ex = assertThrows(NopException.class, () ->
                MetaAggregationExecutor.substituteAndValidateHavingExpr(
                        "sumA - unknownM", names, table(),
                        Arrays.asList("sumA"), Arrays.asList()));
        assertTrue(ex.getErrorCode().contains("having-unknown-name")
                        || ex.getMessage().contains("not in the user-selected"),
                "must fail with ERR_AGGR_HAVING_UNKNOWN_NAME: " + ex.getMessage());
    }

    /** expression 型 measure（aggSql 含 ?）被 arithmetic 引用 → 显式失败。 */
    @Test
    public void testExpressionMeasureReferencedFails() {
        Map<String, String> names = nameToExpr(
                "fieldM", "SUM(AMOUNT)",
                "exprM", "SUM(AMOUNT * ?)");  // expression 型 measure（含 ? 字面量占位符）
        NopException ex = assertThrows(NopException.class, () ->
                MetaAggregationExecutor.substituteAndValidateHavingExpr(
                        "fieldM - exprM", names, table(),
                        Arrays.asList("fieldM", "exprM"), Arrays.asList()));
        assertTrue(ex.getErrorCode().contains("having-order-by-unsupported")
                        || ex.getMessage().contains("HAVING or ORDER BY"),
                "must fail with ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED: " + ex.getMessage());
    }

    /** 不安全关键字 DROP（替换后 SQL 文本含 DROP）→ 显式失败 ERR_AGGR_HAVING_EXPR_UNSAFE。 */
    @Test
    public void testUnsafeKeywordFails() {
        // 通过精心构造的 nameToExpr 让替换后的 SQL 含 DROP 关键字
        Map<String, String> names = nameToExpr(
                "evil", "DROP TABLE foo");
        NopException ex = assertThrows(NopException.class, () ->
                MetaAggregationExecutor.substituteAndValidateHavingExpr(
                        "evil", names, table(),
                        Arrays.asList("evil"), Arrays.asList()));
        assertTrue(ex.getErrorCode().contains("having-expr-unsafe")
                        || ex.getErrorCode().contains("unsafe"),
                "must fail with having-expr-unsafe: " + ex.getErrorCode());
    }

    /** parse 失败（未闭合括号）→ 显式失败 ERR_AGGR_HAVING_EXPR_UNPARSEABLE。 */
    @Test
    public void testUnparseableFails() {
        // 通过 nameToExpr 让替换后含未闭合括号
        Map<String, String> names = nameToExpr(
                "a", "SUM(A)",
                "b", "SUM(B)");
        NopException ex = assertThrows(NopException.class, () ->
                MetaAggregationExecutor.substituteAndValidateHavingExpr(
                        "(a + b", names, table(),  // user-level 未闭合括号
                        Arrays.asList("a", "b"), Arrays.asList()));
        assertTrue(ex.getErrorCode().contains("having-expr-unparseable")
                        || ex.getErrorCode().contains("unparseable"),
                "must fail with having-expr-unparseable: " + ex.getErrorCode());
    }

    /** Phase 1 字面量禁止：user expr 含数值字面量 → 显式失败。 */
    @Test
    public void testLiteralForbiddenFails() {
        Map<String, String> names = nameToExpr(
                "a", "SUM(A)",
                "b", "SUM(B)");
        NopException ex = assertThrows(NopException.class, () ->
                MetaAggregationExecutor.substituteAndValidateHavingExpr(
                        "a / b * 100", names, table(),  // 100 是字面量
                        Arrays.asList("a", "b"), Arrays.asList()));
        assertTrue(ex.getErrorCode().contains("having-expr-unsafe"),
                "must fail with having-expr-unsafe (literals disallowed): " + ex.getErrorCode());
        assertTrue(ex.getMessage().contains("literals")
                        || ex.getMessage().contains("not allowed"),
                "error reason must mention literals disallowed: " + ex.getMessage());
    }

    /** TreeBean 预处理：单 leaf，expr 改写 name。 */
    @Test
    public void testPreprocessSingleLeafWritesNameAttr() {
        Map<String, String> names = nameToExpr(
                "a", "SUM(A)",
                "b", "SUM(B)");
        TreeBean leaf = exprLeaf("gt", "a - b", 10);
        MetaAggregationExecutor.preprocessHavingArithmetic(leaf, names, table(),
                Arrays.asList("a", "b"), Arrays.asList());
        Object nameAttr = leaf.getAttr(FilterBeanConstants.FILTER_ATTR_NAME);
        assertEquals("SUM(A) - SUM(B)", nameAttr,
                "preprocess writes final SQL to name attr");
        // expr 属性仍保留（不参与 translate，仅上下文）
        assertEquals("a - b", leaf.getAttr(MetaAggregationExecutor.HAVING_EXPR_ATTR));
    }

    /** TreeBean 预处理：递归 and/or/not，仅预处理 expr leaf，常规 name leaf 不变。 */
    @Test
    public void testPreprocessRecursiveAndOrNot() {
        Map<String, String> names = nameToExpr(
                "a", "SUM(A)",
                "b", "SUM(B)",
                "c", "CATEGORY");
        // (a - b > 10) AND (c = 'X')——first leaf 是 expr leaf，second 是常规 name leaf
        TreeBean andTree = FilterBeans.and(
                exprLeaf("gt", "a - b", 10),
                FilterBeans.eq("c", "X"));
        MetaAggregationExecutor.preprocessHavingArithmetic(andTree, names, table(),
                Arrays.asList("a", "b"), Arrays.asList("c"));
        TreeBean firstChild = andTree.getChildren().get(0);
        assertEquals("SUM(A) - SUM(B)",
                firstChild.getAttr(FilterBeanConstants.FILTER_ATTR_NAME),
                "expr leaf in and-tree: name rewritten");
        TreeBean secondChild = andTree.getChildren().get(1);
        assertEquals("c", secondChild.getAttr(FilterBeanConstants.FILTER_ATTR_NAME),
                "regular name leaf in and-tree: name unchanged");
    }

    /** containsHavingArithmeticLeaf 检测：having 树中存在 expr leaf → true；不存在 → false。 */
    @Test
    public void testContainsHavingArithmeticLeaf() {
        Map<String, String> names = nameToExpr("a", "SUM(A)");
        // 无 expr leaf
        TreeBean plainLeaf = FilterBeans.gt("a", 10);
        assertTrue(!MetaAggregationExecutor.containsHavingArithmeticLeaf(plainLeaf),
                "no expr leaf → false");
        // 有 expr leaf
        TreeBean exprLeaf = exprLeaf("gt", "a - a", 0);
        assertTrue(MetaAggregationExecutor.containsHavingArithmeticLeaf(exprLeaf),
                "expr leaf present → true");
        // and 树中包含 expr leaf
        TreeBean andWithExpr = FilterBeans.and(plainLeaf, exprLeaf);
        assertTrue(MetaAggregationExecutor.containsHavingArithmeticLeaf(andWithExpr),
                "and-tree containing expr leaf → true");
        // and 树中不包含 expr leaf
        TreeBean andPlain = FilterBeans.and(plainLeaf, FilterBeans.eq("a", 5));
        assertTrue(!MetaAggregationExecutor.containsHavingArithmeticLeaf(andPlain),
                "and-tree without expr leaf → false");
        // null 安全
        assertTrue(!MetaAggregationExecutor.containsHavingArithmeticLeaf(null),
                "null having → false");
    }
}
