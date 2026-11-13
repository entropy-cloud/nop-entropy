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

    /**
     * 2.3-C9-003（plan 2551 裁定 K）：密钥表为 volatile 不可变快照（整表替换发布）——原形态
     * 非 volatile HashMap 经 synchronized ensureKeys 延迟初始化，热路径（每认证请求 parse/gen）
     * 全部串行过单例监视器（锁内 isEmpty 快检可零成本消除）。{@code legacyKey} 一并并入不可变
     * 快照（R2-n：parseToken 锁外读，留在旧形态=未安全发布可变字段）。仓内先例
     * AiRateLimitGatewayInterceptor.buckets。
     */
    private volatile KeySnapshot keySnapshot = KeySnapshot.EMPTY;

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

    /** 不可变密钥快照（keys 表 + legacyKey 整表发布） */
    static final class KeySnapshot {
        static final KeySnapshot EMPTY = new KeySnapshot(Map.of(), null);

        final Map<String, Key> signKeys;
        final Key legacyKey;

        KeySnapshot(Map<String, Key> signKeys, Key legacyKey) {
            this.signKeys = signKeys;
            this.legacyKey = legacyKey;
        }
    }

    /**
     * 锁外快检（DCL）：快照非空即返回（热路径零锁）；否则进入 synchronized 二次确认并构建。
     * 并发首调共享同一次派生（singleflight 语义——败者以胜者快照为准，失败可重试：
     * 派生抛异常时快照保持 EMPTY，下个调用重新进入构建）。
     */
    protected void ensureKeys() {
        if (!keySnapshot.signKeys.isEmpty())
            return;

        synchronized (this) {
            if (!keySnapshot.signKeys.isEmpty())
                return;

            Map<String, Key> keys = new HashMap<>();
            Key legacy = null;
            if (StringHelper.isEmpty(encKey)) {
                keys.put(KID_ACCESS, JwtHelper.hmacKey(StringHelper.generateUUID(), "nop-access"));
                keys.put(KID_REFRESH, JwtHelper.hmacKey(StringHelper.generateUUID(), "nop-refresh"));
                keys.put(KID_CODE, JwtHelper.hmacKey(StringHelper.generateUUID(), "nop-code"));
            } else {
                keys.put(KID_ACCESS, JwtHelper.hmacKey(encKey, "nop-access"));
                keys.put(KID_REFRESH, JwtHelper.hmacKey(encKey, "nop-refresh"));
                keys.put(KID_CODE, JwtHelper.hmacKey(encKey, "nop-code"));
                legacy = JwtHelper.hmacKey(encKey, "nop");
            }
            this.keySnapshot = new KeySnapshot(Map.copyOf(keys), legacy);
        }
    }

    /** 测试缝：access key 快照读取 */
    Key getAccessTokenKeyForTest() {
        ensureKeys();
        return keySnapshot.signKeys.get(KID_ACCESS);
    }

    protected Key getKey(String kid) {
        ensureKeys();
        return keySnapshot.signKeys.get(kid);
    }

    @Override
    public String generateAccessToken(IUserContext userContext, long expireSeconds) {
        ensureKeys();
        return JwtHelper.genToken(keySnapshot.signKeys.get(KID_ACCESS), KID_ACCESS, issuer, audience, JwtHelper.TOKEN_TYPE_ACCESS,
                "a", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public String generateAccessCode(IUserContext userContext, long expireSeconds) {
        ensureKeys();
        return JwtHelper.genToken(keySnapshot.signKeys.get(KID_CODE), KID_CODE, issuer, audience, JwtHelper.TOKEN_TYPE_CODE,
                "c", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public String generateRefreshToken(IUserContext userContext, long expireSeconds) {
        ensureKeys();
        return JwtHelper.genToken(keySnapshot.signKeys.get(KID_REFRESH), KID_REFRESH, issuer, audience, JwtHelper.TOKEN_TYPE_REFRESH,
                "r", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public AuthToken parseAuthToken(String accessToken) {
        ensureKeys();
        ensureKeys();
        return JwtHelper.parseToken(accessToken, this::getKey, issuer, audience, JwtHelper.TOKEN_TYPE_ACCESS,
                keySnapshot.legacyKey, legacyTokenGraceSeconds);
    }

    @Override
    public AuthToken parseRefreshToken(String refreshToken) {
        ensureKeys();
        return JwtHelper.parseToken(refreshToken, this::getKey, issuer, audience, JwtHelper.TOKEN_TYPE_REFRESH,
                keySnapshot.legacyKey, legacyTokenGraceSeconds);
    }

    @Override
    public AuthToken parseAccessCode(String accessCode) {
        ensureKeys();
        return JwtHelper.parseToken(accessCode, this::getKey, issuer, audience, JwtHelper.TOKEN_TYPE_CODE,
                keySnapshot.legacyKey, legacyTokenGraceSeconds);
    }
}
