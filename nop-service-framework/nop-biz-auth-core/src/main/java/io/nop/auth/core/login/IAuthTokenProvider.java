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
}