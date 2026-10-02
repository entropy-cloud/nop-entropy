package io.nop.xlang.expr;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.XLangErrors;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：xscript（compileFullExpr）求值边界语义。
 * 覆盖内建函数/全局对象边界、运算符边界、null 传播与错误路径。
 * 语义锚点均对齐既有 .test.md 数据用例（string/binary-op/member-optional/js-globals/bool-op）。
 */
public class TestEvalBoundarySemantics extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private Object eval(String expr) {
        IEvalAction action = XLang.newCompileTool().compileFullExpr(null, expr);
        return action.invoke(new ServiceContextImpl());
    }

    /**
     * 字符串拼接的 null 传播边界：null 参与 += / + 拼接时转为 "null" 字符串（对齐 string.test.md）。
     */
    @Test
    public void testStringConcatWithNull() {
        assertEquals("\nnullsv", eval("let x = \"\\n\"; let y = null; x+=y; x+=\"s\"; x=x+'v'; x"));
    }

    /**
     * 严格相等边界：=== 不做类型转换，跨类型必为 false；null 与 null 严格相等。
     */
    @Test
    public void testStrictEqualityBoundaries() {
        assertEquals(Boolean.FALSE, eval("1 === '1'"), "跨类型 === 必须为 false");
        assertEquals(Boolean.TRUE, eval("null === null"));
        assertEquals(Boolean.TRUE, eval("1 !== '1'"));
        assertEquals(Boolean.FALSE, eval("1 === 1 ? false : true"), "三元表达式复用严格相等语义");
    }

    /**
     * 逻辑运算符返回操作数本身而非布尔值（JS 语义）：左操作数真值时 && 返回右操作数（对齐 bool-op.test.md）。
     */
    @Test
    public void testLogicalOperatorsReturnOperands() {
        assertEquals("b", eval("let x = 'a'; let y = 'b'; x && y"), "左侧真值时 && 返回右操作数");
        assertNull(eval("let x = 'a'; x && null"));
        assertEquals("a", eval("let z = null || 'a'; z"), "左侧 null 时 || 返回右操作数");
        assertEquals(0, eval("let x = 'a'; x && 0"));
    }

    /**
     * null 合并运算符 ??：仅 null/undefined 触发右操作数，falsy 值（0/false）不触发。
     */
    @Test
    public void testNullishCoalescingBoundary() {
        assertEquals("a", eval("let x = null; x ?? 'a'"));
        assertEquals(0, eval("0 ?? 'default'"), "0 不是 null，?? 不得触发右侧");
        assertEquals(Boolean.FALSE, eval("false ?? 'default'"), "false 不是 null，?? 不得触发右侧");
    }

    /**
     * 可选链边界：null 对象的 ?. 属性/索引/方法全部透传 null（对齐 member-optional.md）。
     */
    @Test
    public void testOptionalChainOnNull() {
        assertNull(eval("let a = null; a?.length"));
        assertNull(eval("let b = null; b?.[0]"));
        assertNull(eval("let a = null; a?.charAt(0)"));
    }

    /**
     * 越界索引返回 null 而非抛错（对齐 member-optional.md 的 b[1] == null）。
     */
    @Test
    public void testArrayIndexOutOfBoundsReturnsNull() {
        assertNull(eval("let b = [1]; b[1]"), "越界索引按 JS 语义返回 null");
        assertNull(eval("let b = [1]; b?.[1]"));
    }

    /**
     * 对 null 直接取属性（无 ?.）必须报 nop.err.xlang.exec.get-prop-on-null-obj。
     */
    @Test
    public void testGetPropertyOnNullThrows() {
        NopException e = assertThrows(NopException.class,
                () -> eval("let a = null; a.length"));
        assertEquals(XLangErrors.ERR_EXEC_GET_PROP_ON_NULL_OBJ.getErrorCode(), e.getErrorCode());
    }

    /**
     * 对象上不存在的方法 → nop.err.xlang.exec.obj-unknown-method。
     */
    @Test
    public void testUnknownMethodThrows() {
        NopException e = assertThrows(NopException.class,
                () -> eval("'abc'.noSuchMethod()"));
        assertEquals(XLangErrors.ERR_EXEC_NO_OBJ_METHOD.getErrorCode(), e.getErrorCode());
    }

    /**
     * 数值运算边界：double 除法为 JS 语义（0.0/0.0 → NaN），字符串 indexOf 未命中返回 -1。
     */
    @Test
    public void testNumericAndStringBuiltinBoundaries() {
        assertEquals(Boolean.TRUE, eval("Number.isNaN(0.0 / 0.0)"));
        assertEquals(Boolean.TRUE, eval("'abc'.indexOf('z') == -1"));
        assertEquals(2, eval("'abc'.indexOf('c')"));
        assertEquals('a', eval("'abc'.charAt(0)"));
    }

    /**
     * Math 全局对象边界：floor/ceil 向整数收敛，abs/max 按值域选择。
     */
    @Test
    public void testMathGlobalBoundaries() {
        assertEquals(4L, eval("Math.floor(4.7)"));
        assertEquals(5L, eval("Math.ceil(4.1)"));
        assertEquals(3L, eval("Math.abs(-3L)"));
        assertEquals(3L, eval("Math.max(1L, 3L)"));
    }

    /**
     * Number.parseInt 边界：带进制解析，非数字输入按 Java 语义抛错路径由 Number.isNaN 区分。
     */
    @Test
    public void testNumberParseBoundaries() {
        assertEquals(42, eval("Number.parseInt('42')"));
        assertEquals(16, eval("Number.parseInt('10', 16)"));
        assertEquals(Boolean.TRUE, eval("Number.isInteger(5L)"));
        assertEquals(Boolean.FALSE, eval("Number.isInteger(5.5)"));
    }

    /**
     * JSON 全局对象：parse/stringify 往返语义。
     */
    @Test
    public void testJsonGlobalRoundTrip() {
        assertEquals(Boolean.TRUE, eval("let s = JSON.stringify(JSON.parse('{\"a\":1}')); s.contains(\"\\\"a\\\":1\")"));
    }

    /**
     * instanceof 边界：new Array() 落到 java.util.ArrayList。
     */
    @Test
    public void testInstanceOfGlobalCollections() {
        assertEquals(Boolean.TRUE, eval("new Array() instanceof java.util.ArrayList"));
        assertEquals(Boolean.TRUE, eval("new Map() instanceof java.util.LinkedHashMap"));
    }

    /**
     * 解析错误恢复路径：二元运算符缺少右侧操作数在 antlr 解析层失败 →
     * nop.err.antlr.common.parse-fail（未进入 xlang 语义层错误码）。
     */
    @Test
    public void testParseErrorBinaryOpMissingRight() {
        NopException e = assertThrows(NopException.class, () -> eval("1 +"));
        assertEquals("nop.err.antlr.common.parse-fail", e.getErrorCode());
    }

    /**
     * 解析错误恢复路径：括号未闭合在 antlr 解析层失败 → nop.err.antlr.common.parse-fail。
     */
    @Test
    public void testParseErrorUnclosedParen() {
        NopException e = assertThrows(NopException.class, () -> eval("(1 + 2"));
        assertEquals("nop.err.antlr.common.parse-fail", e.getErrorCode());
    }

    /**
     * 语义校验路径：break 出现在循环外 → nop.err.xlang.break-statement-not-in-loop
     * （对齐 compile-error.test.md）。
     */
    @Test
    public void testBreakOutsideLoopRejected() {
        NopException e = assertThrows(NopException.class,
                () -> eval("function f(){ if(true) break; }"));
        assertEquals("nop.err.xlang.break-statement-not-in-loop", e.getErrorCode());
    }

    /**
     * 空集合上的 for-of 边界：空数组循环体不执行，求和保持零值。
     */
    @Test
    public void testEmptyCollectionIteration() {
        assertEquals(Boolean.TRUE, eval("let s = 0; for (let x of []) { s += x; } s == 0"));
        assertEquals(Boolean.TRUE, eval("[].size() == 0"));
    }

    /**
     * 三元与逻辑组合的短路边界：右侧表达式不产生副作用/错误。
     */
    @Test
    public void testShortCircuitAvoidsErrorPath() {
        Object v = eval("let a = null; (a == null) || a.length");
        assertEquals(Boolean.TRUE, v, "左侧已为 true 时右侧 null 解引用必须被短路");
        assertFalse(((Boolean) eval("let a = null; (a != null) && false")));
    }
}
