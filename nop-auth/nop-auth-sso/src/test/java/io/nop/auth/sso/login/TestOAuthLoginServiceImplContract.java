package io.nop.auth.sso.login;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.jwt.JwtHelper;
import io.nop.auth.sso.SsoConfig;
import io.nop.auth.sso.SsoErrors;
import io.nop.auth.sso.jwk.JWKPublicKeyLocator;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;

import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_ISSUER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * check2 [P3] 回归：OAuthLoginServiceImpl.generateVerifyCode 契约占位的异常形态。
 *
 * <p>修复前行为：抛裸 {@code UnsupportedOperationException} 且消息为未解析的 error-code
 * 形态字符串 {@code "nop.err.auth.not-impl"}——违反模块错误处理两级策略（NopException +
 * ErrorCode），客户端收到未本地化的 500。
 *
 * <p>F-A6-1 回归：parseAuthToken 必须走完整校验重载——issuer 按 SsoConfig 校验。
 * 修复前行为：2 参重载只验签名+过期，任意同 IdP 签发的跨受众令牌在 logout 缝隙被接受。
 */
class TestOAuthLoginServiceImplContract {

    @Test
    void testGenerateVerifyCodeFailsWithNopException() {
        OAuthLoginServiceImpl service = new OAuthLoginServiceImpl();

        NopException ex = assertThrows(NopException.class, () -> service.generateVerifyCode("verify-secret"),
                "contract placeholder must use NopException + ErrorCode, not bare UnsupportedOperationException");
        assertEquals(SsoErrors.ERR_AUTH_SSO_NOT_IMPL.getErrorCode(), ex.getErrorCode());
    }

    /** 自铸 RSA 密钥对 + stub JWK 定位器（keyLocator 为 protected 同包可替换），不依赖网络。 */
    private static final class Harness {
        final OAuthLoginServiceImpl service;
        final PrivateKey signingKey;

        Harness(String expectedIssuer) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.signingKey = keyPair.getPrivate();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

            OAuthLoginServiceImpl svc = new OAuthLoginServiceImpl();
            SsoConfig config = new SsoConfig();
            config.setIssuer(expectedIssuer);
            svc.setConfig(config);
            // 替换真实 JWKS 定位器：任意 kid 返回自铸公钥
            svc.keyLocator = new JWKPublicKeyLocator() {
                @Override
                public RSAPublicKey getPublicKey(String keyId) {
                    return publicKey;
                }
            };
            this.service = svc;
        }

        String mintToken(String issuer) {
            return JwtHelper.genToken(signingKey, "test-kid", issuer, null, null,
                    "a", "alice", "session-1", 300);
        }
    }

    @Test
    void testParseAuthTokenRejectsWrongIssuerWhenConfigured() throws Exception {
        Harness harness = new Harness("https://idp.example.com");
        String token = harness.mintToken("https://other-idp.example.com");

        NopException ex = assertThrows(NopException.class,
                () -> harness.service.parseAuthToken(token),
                "token from a different issuer must be rejected when config.issuer is set");
        assertEquals(ERR_JWT_INVALID_ISSUER.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void testParseAuthTokenAcceptsMatchingIssuer() throws Exception {
        Harness harness = new Harness("https://idp.example.com");
        String token = harness.mintToken("https://idp.example.com");

        assertEquals("alice", harness.service.parseAuthToken(token).getUserName());
    }

    @Test
    void testParseAuthTokenSkipsIssuerCheckWhenNotConfigured() throws Exception {
        Harness harness = new Harness(null);
        String token = harness.mintToken("https://any-idp.example.com");

        assertEquals("alice", harness.service.parseAuthToken(token).getUserName());
    }
}
