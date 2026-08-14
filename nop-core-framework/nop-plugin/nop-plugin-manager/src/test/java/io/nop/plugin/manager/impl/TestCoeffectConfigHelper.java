package io.nop.plugin.manager.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W5：expectedValue 宽松比较器（定义级全局 + 实例级合并视图两端共用）——
 * 字符串与布尔/数字等价比较逐类验证。
 */
public class TestCoeffectConfigHelper {

    @Test
    public void testBooleanComparison() {
        // expected "true"（spec 字符串）与 Boolean.TRUE 等价
        assertTrue(CoeffectConfigHelper.matches(Boolean.TRUE, "true"));
        assertTrue(CoeffectConfigHelper.matches("true", "true"));
        assertFalse(CoeffectConfigHelper.matches(Boolean.FALSE, "true"));
        assertFalse(CoeffectConfigHelper.matches("false", "true"));
        // expected 为 Boolean（无 | 缺省 true）
        assertTrue(CoeffectConfigHelper.matches("true", Boolean.TRUE));
        assertTrue(CoeffectConfigHelper.matches(Boolean.TRUE, Boolean.TRUE));
        assertFalse(CoeffectConfigHelper.matches("false", Boolean.TRUE));
        assertFalse(CoeffectConfigHelper.matches(Boolean.FALSE, Boolean.TRUE));
    }

    @Test
    public void testNumericComparison() {
        // 数值字符串与数字等价（两端同规则）
        assertTrue(CoeffectConfigHelper.matches(10, "10"));
        assertTrue(CoeffectConfigHelper.matches("10", 10));
        assertTrue(CoeffectConfigHelper.matches(10.0, "10"));
        assertTrue(CoeffectConfigHelper.matches("10.5", 10.5));
        assertFalse(CoeffectConfigHelper.matches("10", 11));
        assertFalse(CoeffectConfigHelper.matches("abc", 10), "非数值字符串与数字不匹配");
    }

    @Test
    public void testStringComparison() {
        assertTrue(CoeffectConfigHelper.matches("dev", "dev"));
        assertFalse(CoeffectConfigHelper.matches("dev", "prod"));
        // actual 为 Boolean/Number 与 expected 字符串等价
        assertTrue(CoeffectConfigHelper.matches(Boolean.TRUE, "true"));
        assertTrue(CoeffectConfigHelper.matches(10, "10"));
    }

    @Test
    public void testNullAndMismatch() {
        assertFalse(CoeffectConfigHelper.matches(null, "true"), "actual null 一律不匹配");
        assertFalse(CoeffectConfigHelper.matches(null, Boolean.TRUE));
        assertFalse(CoeffectConfigHelper.matches("x", 1));
        assertFalse(CoeffectConfigHelper.matches(1, "true"));
    }
}
