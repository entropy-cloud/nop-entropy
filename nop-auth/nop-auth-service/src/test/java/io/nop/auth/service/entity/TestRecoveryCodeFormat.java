package io.nop.auth.service.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 [P3] 回归：恢复码格式边界。
 *
 * <p>修复前行为：{@code Math.abs(Long.MIN_VALUE)} 返回自身（负数），旧实现该理论边界
 * （概率 2^-64）产生带 {@code -} 前缀的短串（非 10 位纯数字），落库 hash 后永不匹配。
 * 修复为 {@code & Long.MAX_VALUE} 位掩码取非负。
 *
 * <p>注：随机源（MathHelper.secureRandom）不可注入，2^-64 边界无法确定性触发旧缺陷，
 * 故无 stash 红验证；以对抽取出的纯函数 {@code toRecoveryCode} 的极值边界测试钉定新行为。
 */
class TestRecoveryCodeFormat {

    @Test
    void testToRecoveryCodeExtremeInputsAreTenDigits() {
        long[] extremes = {Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L, 1L, 1234567890123L, -1234567890123L};
        for (long v : extremes) {
            String code = NopAuthUserBizModel.toRecoveryCode(v);
            assertTrue(code.matches("\\d{10}"), "input " + v + " must map to 10 pure digits but was " + code);
        }
    }

    @Test
    void testToRecoveryCodeZeroIsPadded() {
        assertEquals("0000000000", NopAuthUserBizModel.toRecoveryCode(0L));
    }

    @Test
    void testGeneratedRecoveryCodesAreTenDigits() throws Exception {
        // 随机路径行为钉定：1000 次生成恒为 10 位纯数字
        Method gen = NopAuthUserBizModel.class.getDeclaredMethod("generateRecoveryCode");
        gen.setAccessible(true);
        for (int i = 0; i < 1000; i++) {
            String code = (String) gen.invoke(null);
            assertNotNull(code);
            assertTrue(code.matches("\\d{10}"), "generated code must be 10 digits but was " + code);
        }
    }
}
