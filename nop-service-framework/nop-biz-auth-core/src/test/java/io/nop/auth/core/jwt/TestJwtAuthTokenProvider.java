package io.nop.auth.core.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.UserContextImpl;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_AUDIENCE;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_ISSUER;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_TOKEN;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_TOKEN_EXPIRED;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_WRONG_TOKEN_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 验证 JWT 用途/issuer/audience 契约（H-1）：access/refresh/code 令牌不可互相替换，
 * 错误的 issuer/audience/用途被拒绝，旧版令牌按迁移窗口接受。
 */
public class TestJwtAuthTokenProvider {

    private static UserContextImpl userContext() {
        UserContextImpl ctx = new UserContextImpl();
        ctx.setUserName("alice");
        ctx.setSessionId("session-1");
        return ctx;
    }

    private static JwtAuthTokenProvider provider(String encKey, long legacyGrace) {
        JwtAuthTokenProvider p = new JwtAuthTokenProvider();
        p.setEncKey(encKey);
        p.setLegacyTokenGraceSeconds(legacyGrace);
        return p;
    }

    private static void expect(ErrorCodeRunnable fn, String expectedCode) {
        try {
            fn.run();
            fail("expected exception " + expectedCode);
        } catch (NopException e) {
            assertEquals(expectedCode, e.getErrorCode());
        }
    }

    @FunctionalInterface
    interface ErrorCodeRunnable {
        void run();
    }

    @Test
    public void testPurposeConfusionRejected() {
        JwtAuthTokenProvider p = provider("test-enc-key", 0);
        UserContextImpl ctx = userContext();

        String access = p.generateAccessToken(ctx, 60);
        String refresh = p.generateRefreshToken(ctx, 60);
        String code = p.generateAccessCode(ctx, 60);

        // 三种令牌互不相同
        assertNotEquals(access, refresh);
        assertNotEquals(access, code);

        // access 令牌在 access 消费者处通过
        AuthToken parsed = p.parseAuthToken(access);
        assertEquals("alice", parsed.getUserName());

        // refresh 令牌在 access 消费者处被拒（用途不匹配）
        expect(() -> p.parseAuthToken(refresh), ERR_JWT_WRONG_TOKEN_TYPE.getErrorCode());
        // access 令牌在 refresh 消费者处被拒
        expect(() -> p.parseRefreshToken(access), ERR_JWT_WRONG_TOKEN_TYPE.getErrorCode());
        // code 令牌在 access 消费者处被拒
        expect(() -> p.parseAuthToken(code), ERR_JWT_WRONG_TOKEN_TYPE.getErrorCode());
        // refresh 令牌在 refresh 消费者处通过
        assertEquals("alice", p.parseRefreshToken(refresh).getUserName());
        // code 令牌在 code 消费者处通过
        assertEquals("alice", p.parseAccessCode(code).getUserName());
    }

    @Test
    public void testWrongIssuerAudienceRejected() {
        JwtAuthTokenProvider p = provider("test-enc-key", 0);
        UserContextImpl ctx = userContext();

        // 伪造一个 issuer/audience 不匹配的 access 令牌：直接用 access 密钥签发但改写 issuer
        Key accessKey = p.getKey(JwtAuthTokenProvider.KID_ACCESS);
        String badIssuer = JwtHelper.genToken(accessKey, JwtAuthTokenProvider.KID_ACCESS,
                "evil", "nop", JwtHelper.TOKEN_TYPE_ACCESS, "a", "alice", "session-1", 60);
        expect(() -> p.parseAuthToken(badIssuer), ERR_JWT_INVALID_ISSUER.getErrorCode());

        // audience 不匹配
        Key refreshKey = p.getKey(JwtAuthTokenProvider.KID_REFRESH);
        String badAudience = JwtHelper.genToken(refreshKey, JwtAuthTokenProvider.KID_REFRESH,
                "nop", "evil-aud", JwtHelper.TOKEN_TYPE_REFRESH, "r", "alice", "session-1", 60);
        expect(() -> p.parseRefreshToken(badAudience), ERR_JWT_INVALID_AUDIENCE.getErrorCode());
    }

    @Test
    public void testExpiredTokenRejected() {
        JwtAuthTokenProvider p = provider("test-enc-key", 0);
        UserContextImpl ctx = userContext();

        // 签发一个已过期的令牌（直接构造）
        Key accessKey = p.getKey(JwtAuthTokenProvider.KID_ACCESS);
        String expired = legacyStyleToken(accessKey, JwtAuthTokenProvider.KID_ACCESS,
                JwtHelper.TOKEN_TYPE_ACCESS, "a", -60, -120);
        expect(() -> p.parseAuthToken(expired), ERR_JWT_TOKEN_EXPIRED.getErrorCode());
    }

    @Test
    public void testLegacyTokenMigrationWindow() throws Exception {
        JwtAuthTokenProvider p = provider("test-enc-key", 200);
        UserContextImpl ctx = userContext();

        // 旧版令牌：无KID、无typ、用 legacy 密钥(nop salt)签发
        Key legacyKey = JwtHelper.hmacKey("test-enc-key", "nop");
        String legacyNow = legacyStyleToken(legacyKey, null, null, "a", 60, 0);

        // 宽限期内（grace=200s，刚签发）应被接受（access 消费者）
        AuthToken parsed = p.parseAuthToken(legacyNow);
        assertEquals("alice", parsed.getUserName());

        // grace=0 时不接受旧版令牌
        JwtAuthTokenProvider pNoGrace = provider("test-enc-key", 0);
        expect(() -> pNoGrace.parseAuthToken(legacyNow), ERR_JWT_INVALID_TOKEN.getErrorCode());

        // 超出宽限期（签发于 300s 前，grace=200s）被拒
        String legacyOld = legacyStyleToken(legacyKey, null, null, "a", -10, -300);
        expect(() -> p.parseAuthToken(legacyOld), ERR_JWT_INVALID_TOKEN.getErrorCode());

        // 旧版令牌用错误密钥签发被拒
        Key wrongKey = JwtHelper.hmacKey("other-key", "nop");
        String legacyWrong = legacyStyleToken(wrongKey, null, null, "a", 60, 0);
        expect(() -> p.parseAuthToken(legacyWrong), ERR_JWT_INVALID_TOKEN.getErrorCode());
    }

    @Test
    public void testUnknownKidRejected() {
        JwtAuthTokenProvider p = provider("test-enc-key", 0);
        UserContextImpl ctx = userContext();

        Key accessKey = p.getKey(JwtAuthTokenProvider.KID_ACCESS);
        // 用一个未知 KID 签发
        String unknownKid = JwtHelper.genToken(accessKey, "unknown-kid", "nop", "nop",
                JwtHelper.TOKEN_TYPE_ACCESS, "a", "alice", "session-1", 60);
        expect(() -> p.parseAuthToken(unknownKid), ERR_JWT_INVALID_TOKEN.getErrorCode());
    }

    @Test
    public void testBackwardCompatGenTokenStillWorks() {
        // 旧的 genToken 签名（无KID/aud/typ）仍可用于兼容场景，parseToken(2参) 仍可解析
        Key key = JwtHelper.hmacKey("test", "nop");
        String token = JwtHelper.genToken(key, "my", "abc", "2", 20000);
        AuthToken parsed = JwtHelper.parseToken(key, token);
        assertEquals("abc", parsed.getUserName());
    }

    /**
     * 构造可控 issueTime/expiry 的令牌。expireOffset 和 issueOffset 为相对当前时间的秒数（可负）。
     * kid/typ 为 null 时省略，模拟旧版令牌。
     */
    private static String legacyStyleToken(Key key, String kid, String typ, String subject,
                                           long expireOffsetSec, long issueOffsetSec) {
        try {
            JWSSigner signer = new MACSigner((SecretKey) key);
            long now = System.currentTimeMillis();
            JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                    .issuer("nop")
                    .subject(subject)
                    .expirationTime(new Date(now + expireOffsetSec * 1000L))
                    .issueTime(new Date(now + issueOffsetSec * 1000L))
                    .jwtID("session-1")
                    .claim("preferred_username", "alice");
            if (typ != null)
                b.claim("typ", typ);

            JWSHeader.Builder hb = new JWSHeader.Builder(JWSAlgorithm.HS256);
            if (kid != null)
                hb.keyID(kid);

            SignedJWT jwt = new SignedJWT(hb.build(), b.build());
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
