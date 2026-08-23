package io.nop.auth.sso.jwk;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.auth.sso.SsoErrors.ERR_AUTH_SSO_ACCESS_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * check 审计 nop-auth 报告 [P3] vendored JWK 类 bare RuntimeException 且 null keyType NPE：
 * 修复后畸形 JWKS 数据（kty缺失/不支持）以 NopException + ERR_AUTH_SSO_ACCESS_FAIL 归一拒绝，
 * 不再是裸 NPE / RuntimeException。
 */
public class TestJwkErrorNormalization extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMissingKeyTypeRejectedWithErrorCode() {
        JWK jwk = JsonTool.parseBeanFromText("{\"alg\":\"RS256\"}", JWK.class);
        NopException err = assertThrows(NopException.class, jwk::toPublicKey,
                "missing kty must fail with NopException instead of NPE");
        assertEquals(ERR_AUTH_SSO_ACCESS_FAIL.getErrorCode(), err.getErrorCode());
    }

    @Test
    public void testUnsupportedKeyTypeRejectedWithErrorCode() {
        JWK jwk = JsonTool.parseBeanFromText("{\"kty\":\"oct\",\"alg\":\"HS256\"}", JWK.class);
        NopException err = assertThrows(NopException.class, jwk::toPublicKey,
                "unsupported keyType must fail with NopException instead of bare RuntimeException");
        assertEquals(ERR_AUTH_SSO_ACCESS_FAIL.getErrorCode(), err.getErrorCode());
    }
}
