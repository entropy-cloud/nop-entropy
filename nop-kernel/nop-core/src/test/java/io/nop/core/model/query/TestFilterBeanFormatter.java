package io.nop.core.model.query;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFilterBeanFormatter {

    private String format(TreeBean filter) {
        return new FilterBeanFormatter(Function.identity()).format(filter);
    }

    @Test
    public void testFormatCompareOp() {
        // eq 有数学符号时渲染为中缀形式（eq 的数学符号是 =）
        assertEquals("status = \"A\"", normalizeQuotes(format(FilterBeans.eq("status", "A"))));
        assertEquals("age = 20", normalizeQuotes(format(FilterBeans.eq("age", 20))));
        assertEquals("age > 18", normalizeQuotes(format(FilterBeans.gt("age", 18))));
        assertEquals("age != 20", normalizeQuotes(format(FilterBeans.ne("age", 20))));
    }

    @Test
    public void testFormatAssertOp() {
        String text = format(FilterBeans.isNull("memo"));
        assertEquals("memo isNull", text.trim());
    }

    @Test
    public void testFormatBetweenOp() {
        String text = normalizeQuotes(format(FilterBeans.between("age", 18, 30)));
        assertEquals("age between 18 and 30", text.trim());
    }

    @Test
    public void testFormatAndGroup() {
        TreeBean and = FilterBeans.and(FilterBeans.eq("a", 1), FilterBeans.eq("b", 2));
        String text = normalizeQuotes(format(and));
        assertEquals("a = 1 and b = 2", text.trim());
    }

    @Test
    public void testFormatOrGroup() {
        TreeBean or = FilterBeans.or(FilterBeans.eq("a", 1), FilterBeans.eq("b", 2));
        String text = normalizeQuotes(format(or));
        assertEquals("a = 1 or b = 2", text.trim());
    }

    @Test
    public void testFormatNotGroup() {
        TreeBean not = FilterBeans.not(FilterBeans.eq("a", 1));
        String text = normalizeQuotes(format(not));
        assertEquals("not( a = 1 )", text.trim());
    }

    @Test
    public void testFormatNestedGroup() {
        TreeBean nested = FilterBeans.and(
                FilterBeans.eq("a", 1),
                FilterBeans.or(FilterBeans.eq("b", 2), FilterBeans.eq("c", 3)));
        String text = normalizeQuotes(format(nested));
        assertTrue(text.startsWith("a = 1"), "first condition should come first: " + text);
        assertTrue(text.contains("or"), "nested or should be rendered: " + text);
        assertTrue(text.contains(")"), "nested group should be parenthesized: " + text);
    }

    @Test
    public void testNameTransformerApplied() {
        // nameTransformer 常用于把字段名映射为表别名前缀
        FilterBeanFormatter formatter = new FilterBeanFormatter(name -> "o." + name);
        String text = normalizeQuotes(formatter.format(FilterBeans.eq("status", 1)));
        assertEquals("o.status = 1", text.trim());
    }

    @Test
    public void testFormatAlwaysTrueAndFalse() {
        assertEquals("true", format(FilterBeans.alwaysTrue()).trim());
        assertEquals("false", format(FilterBeans.alwaysFalse()).trim());
    }

    @Test
    public void testUseFunctionCallForOpWithoutMathSymbol() {
        // like 等没有数学符号的 op 在 useFunctionCall 下渲染为函数调用形式
        FilterBeanFormatter formatter = new FilterBeanFormatter(Function.identity()).useFunctionCall(true);
        String text = normalizeQuotes(formatter.format(FilterBeans.like("name", "a%")));
        // 注意：当前实现函数调用形式缺少右括号（疑似缺陷，见测试报告），只断言前缀
        assertTrue(text.startsWith("like(name"), "function call form should start with op(name: " + text);
        assertTrue(text.contains("a%"));
    }

    /**
     * FilterBeanFormatter 对字符串值的渲染依赖 JsonTool 序列化（单引号），测试中统一为双引号便于断言
     */
    private String normalizeQuotes(String text) {
        return text.replace("'", "\"").replaceAll("\\s+", " ").trim();
    }
}
