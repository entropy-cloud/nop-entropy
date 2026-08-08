/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.jwt;

import io.nop.api.core.auth.IUserContext;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.commons.util.StringHelper;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;

import static io.nop.auth.core.jwt.JwtHelper.ALG_HMAC_SHA256;

/**
 * JWT 令牌签发与解析。按令牌用途(access/refresh/code)使用各自独立的签名密钥(KID)，
 * 并在解析时校验 issuer/audience/用途，避免不同用途令牌互相替换（H-1）。
 * <p>
 * 旧版令牌（无KID、单一密钥）通过 legacy-token-grace-seconds 迁移窗口兼容：宽限期内按令牌签发时间接受，
 * 过期后强制重新登录。
 */
public class JwtAuthTokenProvider implements IAuthTokenProvider {

    public static final String KID_ACCESS = "access";
    public static final String KID_REFRESH = "refresh";
    public static final String KID_CODE = "code";

    private String algorithm = ALG_HMAC_SHA256;
    private String encKey;
    private String issuer = "nop";
    private String audience = "nop";
    private long legacyTokenGraceSeconds;

    private final Map<String, Key> signKeys = new HashMap<>();
    private Key legacyKey;

    public String getEncKey() {
        return encKey;
    }

    public void setEncKey(String encKey) {
        this.encKey = encKey;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getLegacyTokenGraceSeconds() {
        return legacyTokenGraceSeconds;
    }

    public void setLegacyTokenGraceSeconds(long legacyTokenGraceSeconds) {
        this.legacyTokenGraceSeconds = legacyTokenGraceSeconds;
    }

    /**
     * 按令牌用途派生独立密钥。encKey 为空时使用随机UUID（单实例临时密钥，重启后失效）；
     * 否则用 encKey + 各用途salt 派生确定密钥，同时派生旧版兼容密钥。
     */
    protected synchronized void ensureKeys() {
        if (!signKeys.isEmpty())
            return;

        if (StringHelper.isEmpty(encKey)) {
            signKeys.put(KID_ACCESS, JwtHelper.hmacKey(StringHelper.generateUUID(), "nop-access"));
            signKeys.put(KID_REFRESH, JwtHelper.hmacKey(StringHelper.generateUUID(), "nop-refresh"));
            signKeys.put(KID_CODE, JwtHelper.hmacKey(StringHelper.generateUUID(), "nop-code"));
        } else {
            signKeys.put(KID_ACCESS, JwtHelper.hmacKey(encKey, "nop-access"));
            signKeys.put(KID_REFRESH, JwtHelper.hmacKey(encKey, "nop-refresh"));
            signKeys.put(KID_CODE, JwtHelper.hmacKey(encKey, "nop-code"));
            legacyKey = JwtHelper.hmacKey(encKey, "nop");
        }
    }

    protected Key getKey(String kid) {
        ensureKeys();
        return signKeys.get(kid);
    }

    @Override
    public String generateAccessToken(IUserContext userContext, long expireSeconds) {
        ensureKeys();
        return JwtHelper.genToken(signKeys.get(KID_ACCESS), KID_ACCESS, issuer, audience, JwtHelper.TOKEN_TYPE_ACCESS,
                "a", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public String generateAccessCode(IUserContext userContext, long expireSeconds) {
        ensureKeys();
        return JwtHelper.genToken(signKeys.get(KID_CODE), KID_CODE, issuer, audience, JwtHelper.TOKEN_TYPE_CODE,
                "c", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public String generateRefreshToken(IUserContext userContext, long expireSeconds) {
        ensureKeys();
        return JwtHelper.genToken(signKeys.get(KID_REFRESH), KID_REFRESH, issuer, audience, JwtHelper.TOKEN_TYPE_REFRESH,
                "r", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public AuthToken parseAuthToken(String accessToken) {
        ensureKeys();
        return JwtHelper.parseToken(accessToken, this::getKey, issuer, audience, JwtHelper.TOKEN_TYPE_ACCESS,
                legacyKey, legacyTokenGraceSeconds);
    }

    @Override
    public AuthToken parseRefreshToken(String refreshToken) {
        ensureKeys();
        return JwtHelper.parseToken(refreshToken, this::getKey, issuer, audience, JwtHelper.TOKEN_TYPE_REFRESH,
                legacyKey, legacyTokenGraceSeconds);
    }

    @Override
    public AuthToken parseAccessCode(String accessCode) {
        ensureKeys();
        return JwtHelper.parseToken(accessCode, this::getKey, issuer, audience, JwtHelper.TOKEN_TYPE_CODE,
                legacyKey, legacyTokenGraceSeconds);
    }
}
