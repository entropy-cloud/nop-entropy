package io.nop.xlang.expr.filter;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.ExpressionStatement;
import io.nop.xlang.ast.Program;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：ExpressionToFilterBeanTransformer（WI0 快照 0% 靶点，135 行）。
 * 验证表达式 AST 到 FilterBean（Predicate Tree）的转换语义：比较、逻辑组合、
 * 函数式过滤算子、between 边界，以及不允许表达式时的错误路径。
 */
public class TestExpressionToFilterBeanTransformer extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private TreeBean transform(String source) {
        Program program = XLang.newCompileTool().parseFullExpr(null, source);
        Expression expr = ((ExpressionStatement) program.getBody().get(0)).getExpression();
        return new ExpressionToFilterBeanTransformer().transform(expr);
    }

    private TreeBean transformNoExprOp(String source) {
        Program program = XLang.newCompileTool().parseFullExpr(null, source);
        Expression expr = ((ExpressionStatement) program.getBody().get(0)).getExpression();
        return new ExpressionToFilterBeanTransformer(false).transform(expr);
    }

    private static Object attr(TreeBean bean, String name) {
        return bean.getAttrs() == null ? null : bean.getAttrs().get(name);
    }

    /**
     * 字面量按真值转换：truthy 字面量 → alwaysTrue，falsy → alwaysFalse。
     */
    @Test
    public void testLiteralTransformsToAlwaysTrueOrFalse() {
        assertEquals(FilterBeanConstants.FILTER_OP_ALWAYS_TRUE, transform("1").getTagName());
        assertEquals(FilterBeanConstants.FILTER_OP_ALWAYS_FALSE, transform("0").getTagName());
        assertEquals(FilterBeanConstants.FILTER_OP_ALWAYS_FALSE, transform("null").getTagName());
    }

    /**
     * 属性与常量比较：name/value 按属性在左侧的顺序生成。
     */
    @Test
    public void testCompareWithConstantOnRight() {
        TreeBean bean = transform("a == 1");
        assertEquals(FilterBeanConstants.FILTER_OP_EQ, bean.getTagName());
        assertEquals("a", attr(bean, FilterBeanConstants.FILTER_ATTR_NAME));
        assertEquals(1, attr(bean, FilterBeanConstants.FILTER_ATTR_VALUE));
    }

    /**
     * 常量在左侧时交换操作数并取反比较符（回归覆盖 wi3#1）：3 < a 语义等价 a > 3，
     * 应生成 gt(a,3)，而非 lt(a,3)。
     */
    @Test
    public void testCompareWithConstantOnLeftIsSwapped() {
        TreeBean bean = transform("3 < a");
        assertEquals(FilterBeanConstants.FILTER_OP_GT, bean.getTagName(),
                "常量在左时必须使用 reverseOp：3 < a 等价 a > 3");
        assertEquals("a", attr(bean, FilterBeanConstants.FILTER_ATTR_NAME));
        assertEquals(3, attr(bean, FilterBeanConstants.FILTER_ATTR_VALUE));

        // 其余比较符的交换语义
        assertEquals(FilterBeanConstants.FILTER_OP_LT, transform("3 > a").getTagName());
        assertEquals(FilterBeanConstants.FILTER_OP_GE, transform("3 <= a").getTagName());
        assertEquals(FilterBeanConstants.FILTER_OP_LE, transform("3 >= a").getTagName());
        // eq/ne 交换后保持不变
        assertEquals(FilterBeanConstants.FILTER_OP_EQ, transform("1 == a").getTagName());
        assertEquals(FilterBeanConstants.FILTER_OP_NE, transform("1 != a").getTagName());
    }

    /**
     * 两侧都是属性名时生成 propCompare（valueName 指向另一属性）。
     */
    @Test
    public void testPropCompareOp() {
        TreeBean bean = transform("a >= b");
        assertEquals(FilterBeanConstants.FILTER_OP_GE, bean.getTagName());
        assertEquals("a", attr(bean, FilterBeanConstants.FILTER_ATTR_NAME));
        assertEquals("b", attr(bean, FilterBeanConstants.FILTER_ATTR_VALUE_NAME));
    }

    /**
     * 逻辑与/或生成 and/or 组合树，not 生成 not 节点。
     */
    @Test
    public void testLogicalComposition() {
        TreeBean andBean = transform("a == 1 && b == 2");
        assertEquals(FilterBeanConstants.FILTER_OP_AND, andBean.getTagName());
        List<TreeBean> children = andBean.getChildren();
        assertEquals(2, children.size());
        assertEquals(FilterBeanConstants.FILTER_OP_EQ, children.get(0).getTagName());

        TreeBean orBean = transform("a == 1 || b == 2");
        assertEquals(FilterBeanConstants.FILTER_OP_OR, orBean.getTagName());

        TreeBean notBean = transform("!(a == 1)");
        assertEquals(FilterBeanConstants.FILTER_OP_NOT, notBean.getTagName());
        assertEquals(1, notBean.getChildren().size());
    }

    /**
     * 函数式比较算子：eq(a, 1) 等价于 a == 1。
     */
    @Test
    public void testCallCompareOp() {
        TreeBean bean = transform("eq(a, 1)");
        assertEquals(FilterBeanConstants.FILTER_OP_EQ, bean.getTagName());
        assertEquals("a", attr(bean, FilterBeanConstants.FILTER_ATTR_NAME));
        assertEquals(1, attr(bean, FilterBeanConstants.FILTER_ATTR_VALUE));
    }

    /**
     * 函数式断言算子：notNull(a) 生成 notNull 断言节点。
     */
    @Test
    public void testCallAssertOp() {
        TreeBean bean = transform("notNull(a)");
        assertEquals(FilterBeanConstants.FILTER_OP_NOT_NULL, bean.getTagName());
        assertEquals("a", attr(bean, FilterBeanConstants.FILTER_ATTR_NAME));
    }

    /**
     * 断言算子参数个数错误 → nop.err.xlang.expr.filter-op-invalid-arg-count。
     */
    @Test
    public void testAssertOpInvalidArgCountThrows() {
        NopException e = assertThrows(NopException.class, () -> transform("notNull(a, b)"));
        assertEquals(XLangErrors.ERR_FILTER_OP_INVALID_ARG_COUNT.getErrorCode(), e.getErrorCode());
        assertEquals(2, e.getParams().get("argCount"));
    }

    /**
     * 不允许 expr 时，不可归约为过滤算子的表达式 → nop.err.xlang.expr.filter-not-allow-expr。
     */
    @Test
    public void testNonFilterExprDisallowedThrows() {
        NopException e = assertThrows(NopException.class, () -> transformNoExprOp("-a"));
        assertEquals(XLangErrors.ERR_FILTER_NOT_ALLOW_EXPR.getErrorCode(), e.getErrorCode());
    }

    /**
     * 允许 expr 时，不可归约为过滤算子的表达式（如一元负号）包装为 expr 节点保留原始表达式。
     */
    @Test
    public void testNonFilterExprWrappedAsExprBean() {
        TreeBean bean = transform("-a");
        assertEquals(FilterBeanConstants.FILTER_OP_EXPR, bean.getTagName());
        assertNotNull(attr(bean, FilterBeanConstants.FILTER_ATTR_VALUE));
    }

    /**
     * 不支持的运算符直接出现在过滤表达式中 → nop.err.xlang.expr.unsupported-op
     * （算术运算符没有对应 filterOp，即使 allowExprOp=true 也不允许）。
     */
    @Test
    public void testArithOpThrowsUnsupportedOp() {
        NopException e = assertThrows(NopException.class, () -> transform("1 + 2"));
        assertEquals(XLangErrors.ERR_EXPR_UNSUPPORTED_OP.getErrorCode(), e.getErrorCode());
    }

    /**
     * 未知的过滤函数名 → nop.err.xlang.expr.unsupported-op。
     */
    @Test
    public void testUnknownFilterFunctionThrows() {
        NopException e = assertThrows(NopException.class, () -> transform("unknownOp(a)"));
        assertEquals(XLangErrors.ERR_EXPR_UNSUPPORTED_OP.getErrorCode(), e.getErrorCode());
    }

    /**
     * between(name, min, max, false, false) 生成 between 节点，闭区间语义（无 exclude 标记）。
     */
    @Test
    public void testBetweenOp() {
        TreeBean bean = transform("between(a, 1, 10, false, false)");
        assertEquals(FilterBeanConstants.FILTER_OP_BETWEEN, bean.getTagName());
        assertEquals("a", attr(bean, FilterBeanConstants.FILTER_ATTR_NAME));
        assertEquals(1, attr(bean, FilterBeanConstants.FILTER_ATTR_MIN));
        assertEquals(10, attr(bean, FilterBeanConstants.FILTER_ATTR_MAX));
        assertNull(attr(bean, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN),
                "false 不输出 excludeMin 标记");
        assertNull(attr(bean, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX));
    }

    /**
     * 回归覆盖 wi3#2（plan 2306 项 14）：getArgument 越界返回 null，
     * 3/4 参数的 between 调用合法，排除标记缺省为 false。
     */
    @Test
    public void testBetweenWithThreeAndFourArgsIsSupported() {
        TreeBean three = transform("between(a, 1, 10)");
        assertEquals(FilterBeanConstants.FILTER_OP_BETWEEN, three.getTagName());
        assertEquals("a", attr(three, FilterBeanConstants.FILTER_ATTR_NAME));
        assertNull(attr(three, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN),
                "3 参数形式排除标记缺省 false（不输出标记）");
        assertNull(attr(three, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX));

        TreeBean four = transform("between(a, 1, 10, true)");
        assertEquals(FilterBeanConstants.FILTER_OP_BETWEEN, four.getTagName());
        assertEquals(Boolean.TRUE, attr(four, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN));
        assertNull(attr(four, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX));

        // 参数个数超出 5 仍报既有错误码
        NopException e = assertThrows(NopException.class,
                () -> transform("between(a, 1, 10, true, true, true)"));
        assertTrue(e.getErrorCode().contains("filter-op-invalid-arg-count"));
    }

    /**
     * between 第 4/5 参数为 truthy 字面量时输出开区间标记。
     */
    @Test
    public void testBetweenOpWithExclusiveBounds() {
        TreeBean bean = transform("between(a, 1, 10, true, true)");
        assertEquals(Boolean.TRUE, attr(bean, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN));
        assertEquals(Boolean.TRUE, attr(bean, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX));
    }

    /**
     * between 排除标记必须是字面量，否则报 nop.err.xlang.expr.filter-not-allow-expr。
     */
    @Test
    public void testBetweenExcludeMustBeLiteral() {
        NopException e = assertThrows(NopException.class,
                () -> transform("between(a, 1, 10, b, true)"));
        assertEquals(XLangErrors.ERR_FILTER_NOT_ALLOW_EXPR.getErrorCode(), e.getErrorCode());
    }

    /**
     * 回归覆盖 wi3#2：4 参数 between 不再因 getArgument(4) 越界失败，
     * 仅 excludeMin 生效（见 testBetweenWithThreeAndFourArgsIsSupported 的 four 分支断言）。
     * 保留本用例作为 4 参数形式的独立锚点。
     */
    @Test
    public void testBetweenWithFourArgsIsSupported() {
        TreeBean bean = transform("between(a, 1, 10, true)");
        assertEquals(FilterBeanConstants.FILTER_OP_BETWEEN, bean.getTagName());
        assertEquals(Boolean.TRUE, attr(bean, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MIN));
        assertNull(attr(bean, FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX));
    }

    /**
     * between 参数个数不在 3~5 范围 → nop.err.xlang.expr.filter-op-invalid-arg-count。
     */
    @Test
    public void testBetweenInvalidArgCountThrows() {
        NopException e = assertThrows(NopException.class, () -> transform("between(a, 1)"));
        assertEquals(XLangErrors.ERR_FILTER_OP_INVALID_ARG_COUNT.getErrorCode(), e.getErrorCode());
        assertTrue(e.getParams().containsKey("argCount"));
    }
}
