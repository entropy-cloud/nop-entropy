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

import static io.nop.auth.core.jwt.JwtHelper.ALG_HMAC_SHA256;

public class JwtAuthTokenProvider implements IAuthTokenProvider {
    private String algorithm = ALG_HMAC_SHA256;
    private String encKey;
    private Key signKey;

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

    protected synchronized Key getSignKey() {
        if (signKey == null) {
            if (StringHelper.isEmpty(encKey)) {
                signKey = JwtHelper.hmacKey(StringHelper.generateUUID(), "nop");
            } else {
                signKey = JwtHelper.hmacKey(encKey, "nop");
            }
        }
        return signKey;
    }

    @Override
    public String generateAccessToken(IUserContext userContext, long expireSeconds) {
        return JwtHelper.genToken(getSignKey(), "a", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public String generateAccessCode(IUserContext userContext, long expireSeconds) {
        return JwtHelper.genToken(getSignKey(), "c", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public String generateRefreshToken(IUserContext userContext, long expireSeconds) {
        return JwtHelper.genToken(getSignKey(), "r", userContext.getUserName(), userContext.getSessionId(), expireSeconds);
    }

    @Override
    public AuthToken parseAuthToken(String accessToken) {
        return JwtHelper.parseToken(getSignKey(), accessToken);
    }
}