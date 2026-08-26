package io.nop.auth.sso.login;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.sso.SsoErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * check2 [P3] 回归：OAuthLoginServiceImpl.generateVerifyCode 契约占位的异常形态。
 *
 * <p>修复前行为：抛裸 {@code UnsupportedOperationException} 且消息为未解析的 error-code
 * 形态字符串 {@code "nop.err.auth.not-impl"}——违反模块错误处理两级策略（NopException +
 * ErrorCode），客户端收到未本地化的 500。
 */
class TestOAuthLoginServiceImplContract {

    @Test
    void testGenerateVerifyCodeFailsWithNopException() {
        OAuthLoginServiceImpl service = new OAuthLoginServiceImpl();

        NopException ex = assertThrows(NopException.class, () -> service.generateVerifyCode("verify-secret"),
                "contract placeholder must use NopException + ErrorCode, not bare UnsupportedOperationException");
        assertEquals(SsoErrors.ERR_AUTH_SSO_NOT_IMPL.getErrorCode(), ex.getErrorCode());
    }
}
