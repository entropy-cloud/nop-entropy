/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.AuthToken;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import static io.nop.auth.core.AuthCoreErrors.ARG_TOKEN;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_AUDIENCE;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_ISSUER;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_INVALID_TOKEN;
import static io.nop.auth.core.AuthCoreErrors.ERR_JWT_WRONG_TOKEN_TYPE;

public class JwtHelper {


    public static final String ALG_HMAC_SHA256 = "HS256";

    /**
     * JWT payload中的令牌用途值，配合 {@link AuthApiConstants#JWT_CLAIMS_TYPE} 使用。
     */
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String TOKEN_TYPE_CODE = "code";

    public static AuthToken parseToken(Key key, String token) {
        return parseToken(token, k -> key);
    }

    public static SecretKey hmacKey(String password, String salt) {
        return new SecretKeySpec(HashHelper.sha256(password.getBytes(StringHelper.CHARSET_UTF8),
                salt.getBytes(StandardCharsets.UTF_8)), ALG_HMAC_SHA256);
    }

    public static AuthToken toAuthToken(String token, JWTClaimsSet claims) {
        String sessionId = (String) claims.getClaim(AuthApiConstants.JWT_CLAIMS_SID);
        if (sessionId == null)
            sessionId = claims.getJWTID();

        String subject = claims.getSubject();
        long expireAt = claims.getExpirationTime().getTime();

        int seconds = (int) ((expireAt - claims.getIssueTime().getTime()) / 1000);
        String userName = (String) claims.getClaim(AuthApiConstants.JWT_CLAIMS_USERNAME);
        return new AuthToken(token, subject, userName, sessionId, expireAt, seconds, claims.getClaims());
    }

    public static AuthToken parseToken(String token, Function<String, Key> keyLocator) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            Key key = keyLocator.apply(jwt.getHeader().getKeyID());
            JWSVerifier verifier = newVerifier(key);
            boolean isVerified = jwt.verify(verifier);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!isVerified)
                throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);
            AuthToken authToken = toAuthToken(token, claims);
            if (authToken.isExpired())
                throw new NopExpiredJwtException(claims.getClaims()).authToken(authToken);
            return authToken;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_JWT_INVALID_TOKEN, e).param(ARG_TOKEN, token);
        }
    }

    /**
     * 解析并校验JWT：签名 + 过期时间 + issuer + audience + 令牌用途(typ)。
     * <p>
     * 支持迁移窗口：当令牌头未带KID（旧版令牌）时，若提供了 legacyKey 且处于 grace 期内，
     * 则使用 legacyKey 校验签名并跳过 issuer/audience/typ 校验。grace 期由
     * {@code legacyTokenGraceSeconds} 控制，按令牌自身的签发时间计算，旧令牌自然过期后即被拒绝。
     * <p>
     * 任意校验失败都抛出 {@link NopException}（带明确的错误码），不会返回 null 或静默放行。
     *
     * @param keyLocator                按JWT头KID查找对应令牌用途的签名密钥
     * @param expectedIssuer            期望的 issuer；为 null 表示不校验
     * @param expectedAudience          期望的 audience；为 null 表示不校验
     * @param expectedType              期望的令牌用途(access/refresh/code)；为 null 表示不校验
     * @param legacyKey                 旧版（无KID）令牌使用的签名密钥，可为 null
     * @param legacyTokenGraceSeconds   旧版令牌的宽限期（秒），按令牌签发时间计算；<=0 表示不接受旧版令牌
     */
    public static AuthToken parseToken(String token, Function<String, Key> keyLocator,
                                       String expectedIssuer, String expectedAudience, String expectedType,
                                       Key legacyKey, long legacyTokenGraceSeconds) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String kid = jwt.getHeader().getKeyID();
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            boolean legacy = false;
            Key key;
            if (kid != null) {
                key = keyLocator.apply(kid);
                if (key == null)
                    throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);
            } else {
                // 无KID视为旧版令牌：仅在显式开启宽限期且仍在窗口内时才接受
                key = resolveLegacyKey(claims, legacyKey, legacyTokenGraceSeconds, token);
                legacy = true;
            }

            JWSVerifier verifier = newVerifier(key);
            if (!jwt.verify(verifier))
                throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);

            AuthToken authToken = toAuthToken(token, claims);
            if (authToken.isExpired())
                throw new NopExpiredJwtException(claims.getClaims()).authToken(authToken);

            if (!legacy) {
                verifyIssuer(claims.getIssuer(), expectedIssuer, token);
                verifyAudience(claims.getAudience(), expectedAudience, token);
                verifyType(claims.getClaim(AuthApiConstants.JWT_CLAIMS_TYPE), expectedType, token);
            }
            return authToken;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_JWT_INVALID_TOKEN, e).param(ARG_TOKEN, token);
        }
    }

    private static Key resolveLegacyKey(JWTClaimsSet claims, Key legacyKey,
                                        long legacyTokenGraceSeconds, String token) {
        if (legacyKey == null || legacyTokenGraceSeconds <= 0)
            throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);

        Date issueTime = claims.getIssueTime();
        if (issueTime == null)
            throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);

        long ageMs = CoreMetrics.currentTimeMillis() - issueTime.getTime();
        if (ageMs < 0 || ageMs > legacyTokenGraceSeconds * 1000L)
            throw new NopException(ERR_JWT_INVALID_TOKEN).param(ARG_TOKEN, token);

        return legacyKey;
    }

    private static void verifyIssuer(String actual, String expected, String token) {
        if (expected != null && !expected.equals(actual))
            throw new NopException(ERR_JWT_INVALID_ISSUER).param(ARG_TOKEN, token);
    }

    private static void verifyAudience(List<String> actual, String expected, String token) {
        if (expected != null && (actual == null || !actual.contains(expected)))
            throw new NopException(ERR_JWT_INVALID_AUDIENCE).param(ARG_TOKEN, token);
    }

    private static void verifyType(Object actual, String expected, String token) {
        if (expected != null && !expected.equals(actual))
            throw new NopException(ERR_JWT_WRONG_TOKEN_TYPE).param(ARG_TOKEN, token);
    }

    public static SecretKey newHMACKey(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, ALG_HMAC_SHA256);
    }

    public static String genToken(Key key, String subject, String userName, String sessionId, long expireSeconds) {
        return genToken(key, null, "nop", null, null, subject, userName, sessionId, expireSeconds);
    }

    /**
     * 生成带 issuer/audience/用途(typ) 声明和 KID 的 JWT。
     *
     * @param keyId 写入JWT头的KID，解析时用于按令牌用途选择验签密钥，可为 null（仅旧版兼容场景）
     * @param typ   令牌用途，写入payload的 {@link AuthApiConstants#JWT_CLAIMS_TYPE} 声明，可为 null
     */
    public static String genToken(Key key, String keyId, String issuer, String audience, String typ,
                                  String subject, String userName, String sessionId, long expireSeconds) {
        Guard.notEmpty(sessionId, "sessionId");

        try {
            JWSSigner signer = newSigner(key);

            long begin = CoreMetrics.currentTimeMillis();
            JWSHeader header = new JWSHeader.Builder(getSignAlgorithm(key))
                    .keyID(keyId).build();

            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .expirationTime(new Date(begin + expireSeconds * 1000L))
                    .issueTime(new Date(begin)).jwtID(sessionId)
                    .claim(AuthApiConstants.JWT_CLAIMS_USERNAME, userName);
            if (audience != null)
                builder.audience(audience);
            if (typ != null)
                builder.claim(AuthApiConstants.JWT_CLAIMS_TYPE, typ);

            SignedJWT signedJWT = new SignedJWT(header, builder.build());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private static JWSAlgorithm getSignAlgorithm(Key key) {
        if (key instanceof PrivateKey)
            return JWSAlgorithm.RS256;
        return JWSAlgorithm.HS256;
    }

    private static JWSSigner newSigner(Key key) throws KeyLengthException {
        if (key instanceof PrivateKey)
            return new RSASSASigner((PrivateKey) key);
        if (key instanceof SecretKey)
            return new MACSigner((SecretKey) key);
        throw new IllegalArgumentException("nop.err.unsupported-key:" + key);
    }

    private static JWSVerifier newVerifier(Key key) throws JOSEException {
        if (key instanceof RSAPublicKey)
            return new RSASSAVerifier((RSAPublicKey) key);
        if (key instanceof SecretKey)
            return new MACVerifier((SecretKey) key);
        throw new IllegalArgumentException("nop.err.unsupported-key:" + key);
    }
}
