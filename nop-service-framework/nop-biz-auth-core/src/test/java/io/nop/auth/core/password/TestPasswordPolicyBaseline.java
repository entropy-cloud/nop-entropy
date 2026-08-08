package io.nop.auth.core.password;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_LENGTH_TOO_SHORT;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_TOO_FEW_DIGITS;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_TOO_FEW_SPECIAL_CHAR;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 验证密码策略基线（M-6，DR-1d）：minLength=12，且必须同时包含大写/小写/数字/特殊字符。
 */
public class TestPasswordPolicyBaseline {

    private DefaultPasswordPolicy baselinePolicy() {
        // 与 auth-core-defaults.beans.xml 中 DR-1d 基线一致
        DefaultPasswordPolicy policy = new DefaultPasswordPolicy();
        policy.setMinLength(12);
        policy.setUpperCaseCount(1);
        policy.setLowerCaseCount(1);
        policy.setDigitCount(1);
        policy.setSpecialCharCount(1);
        policy.setNotUserName(true);
        return policy;
    }

    @Test
    public void testStrongPasswordAccepted() {
        DefaultPasswordPolicy policy = baselinePolicy();
        // 满足基线：>=12位、含大小写/数字/特殊字符
        policy.checkAllowedPassword("alice", "Abcdef1!xyz9");
        policy.checkAllowedPassword("alice", "Str0ng#Pass2024");
    }

    @Test
    public void testWeakPasswordBelowBaselineRejected() {
        DefaultPasswordPolicy policy = baselinePolicy();

        // 旧默认密码 "123" 长度不足且缺少多类字符
        expectCode(() -> policy.checkAllowedPassword("alice", "123"), ERR_PASSWORD_LENGTH_TOO_SHORT.getErrorCode());

        // 长度够但缺数字
        expectCode(() -> policy.checkAllowedPassword("alice", "Abcdefgh!xyz"), ERR_PASSWORD_TOO_FEW_DIGITS.getErrorCode());

        // 长度够但缺特殊字符
        expectCode(() -> policy.checkAllowedPassword("alice", "Abcdefgh1xyz"),
                ERR_PASSWORD_TOO_FEW_SPECIAL_CHAR.getErrorCode());
    }

    @Test
    public void testOverridePathRelaxesBaseline() {
        // 迁移/逃生：部署可通过配置放宽（例如回到旧基线 minLength=8、各类=0）
        DefaultPasswordPolicy relaxed = new DefaultPasswordPolicy();
        relaxed.setMinLength(8);
        relaxed.setUpperCaseCount(0);
        relaxed.setLowerCaseCount(0);
        relaxed.setDigitCount(0);
        relaxed.setSpecialCharCount(0);
        // 旧弱密码在放宽配置下可通过，避免升级即锁定既有用户
        relaxed.checkAllowedPassword("alice", "12345678");
    }

    private interface FailingRunnable {
        void run();
    }

    private static void expectCode(FailingRunnable fn, String code) {
        try {
            fn.run();
            fail("expected NopException " + code);
        } catch (NopException e) {
            org.junit.jupiter.api.Assertions.assertEquals(code, e.getErrorCode());
        }
    }
}
