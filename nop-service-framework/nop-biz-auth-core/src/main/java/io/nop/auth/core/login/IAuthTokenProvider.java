/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;

public interface IAuthTokenProvider {
    String generateAccessToken(IUserContext userContext, long expireSeconds);

    /**
     * accessCode为在url链接中传递的，有效期更短的一次性token
     */
    String generateAccessCode(IUserContext userContext, long expireSeconds);

    String generateRefreshToken(IUserContext userContext, long expireSeconds);

    AuthToken parseAuthToken(String accessToken);

    /**
     * 解析refresh令牌并校验其用途为refresh。默认实现回退到 {@link #parseAuthToken}，
     * 供不区分令牌用途的兼容实现（如外部SSO令牌）使用。
     */
    default AuthToken parseRefreshToken(String refreshToken) {
        return parseAuthToken(refreshToken);
    }

    /**
     * 解析一次性accessCode并校验其用途为code。默认实现回退到 {@link #parseAuthToken}。
     */
    default AuthToken parseAccessCode(String accessCode) {
        return parseAuthToken(accessCode);
    }
}