/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2026-07-19-1250-3 Phase 5 AR-11 Proof：验证 {@code evalExpectPassWhen} 解析失败时
 * 显式抛 ErrorCode（{@code ERR_QUALITY_EXPECT_PASS_WHEN_INVALID}），而非破坏整个 checkpoint 的 NFE。
 *
 * <p>evalExpectPassWhen 是 private static 方法，通过同包反射访问（质量规则执行器内部使用）。
 */
public class TestEvalExpectPassWhenErrorPath {

    /**
     * 通过反射调用 {@link MetaQualityRuleExecutor} 的 private static evalExpectPassWhen 方法。
     */
    private boolean invokeEvalExpectPassWhen(String expr, double value) throws Exception {
        java.lang.reflect.Method m = MetaQualityRuleExecutor.class.getDeclaredMethod(
                "evalExpectPassWhen", String.class, double.class);
        m.setAccessible(true);
        try {
            return (boolean) m.invoke(null, expr, value);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void testValidEqExpression() throws Exception {
        assertTrue(invokeEvalExpectPassWhen("eq 1", 1.0));
        assertFalse(invokeEvalExpectPassWhen("eq 1", 2.0));
    }

    @Test
    public void testValidGtExpression() throws Exception {
        assertTrue(invokeEvalExpectPassWhen("gt 5", 6.0));
        assertFalse(invokeEvalExpectPassWhen("gt 5", 5.0));
    }

    @Test
    public void testValidGeExpression() throws Exception {
        assertTrue(invokeEvalExpectPassWhen("ge 5", 5.0));
        assertTrue(invokeEvalExpectPassWhen("ge 5", 6.0));
        assertFalse(invokeEvalExpectPassWhen("ge 5", 4.0));
    }

    @Test
    public void testValidLtExpression() throws Exception {
        assertTrue(invokeEvalExpectPassWhen("lt 5", 4.0));
        assertFalse(invokeEvalExpectPassWhen("lt 5", 5.0));
    }

    @Test
    public void testValidLeExpression() throws Exception {
        assertTrue(invokeEvalExpectPassWhen("le 5", 5.0));
        assertTrue(invokeEvalExpectPassWhen("le 5", 4.0));
        assertFalse(invokeEvalExpectPassWhen("le 5", 6.0));
    }

    @Test
    public void testDefaultEqZero() throws Exception {
        assertTrue(invokeEvalExpectPassWhen("unknown_keyword", 0.0));
        assertFalse(invokeEvalExpectPassWhen("unknown_keyword", 1.0));
    }

    /**
     * plan 2026-07-19-1250-3 Phase 5 AR-11 核心：非法数字表达式必须显式抛 {@link NopException}
     * 携带 {@code ERR_QUALITY_EXPECT_PASS_WHEN_INVALID}，而非 NumberFormatException 破坏整个 checkpoint。
     */
    @Test
    public void testInvalidNumericExpressionThrowsNopException() {
        NopException ex = assertThrows(NopException.class,
                () -> invokeEvalExpectPassWhen("gt abc", 5.0));
        assertTrue(ex.getErrorCode().contains("expect-pass-when"),
                "ErrorCode must be ERR_QUALITY_EXPECT_PASS_WHEN_INVALID: " + ex.getErrorCode());
    }

    @Test
    public void testInvalidNumericExpressionEqVariant() {
        NopException ex = assertThrows(NopException.class,
                () -> invokeEvalExpectPassWhen("eq not_a_number", 5.0));
        assertTrue(ex.getErrorCode().contains("expect-pass-when"));
    }

    @Test
    public void testInvalidNumericExpressionLtVariant() {
        assertThrows(NopException.class, () -> invokeEvalExpectPassWhen("lt 1.2.3", 5.0));
    }

    @Test
    public void testNoNumberFormatExceptionLeaked() {
        // 验证不再抛 NumberFormatException（plan AR-11 核心反例）
        try {
            invokeEvalExpectPassWhen("gt abc", 5.0);
        } catch (NumberFormatException e) {
            throw new AssertionError("evalExpectPassWhen must NOT leak NumberFormatException; "
                    + "should wrap in NopException(ERR_QUALITY_EXPECT_PASS_WHEN_INVALID). Got: " + e, e);
        } catch (NopException e) {
            // expected
            return;
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception type: " + e, e);
        }
    }
}
