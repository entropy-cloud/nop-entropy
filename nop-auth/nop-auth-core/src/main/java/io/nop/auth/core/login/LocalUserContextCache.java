/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.login;

import io.nop.commons.cache.LocalCache;

import jakarta.annotation.PostConstruct;

import static io.nop.commons.cache.CacheConfig.newConfig;

public class LocalUserContextCache extends AbstractUserContextCache {


    @PostConstruct
    public void init() {
        userContextCache = LocalCache.newCache("login-user-context-cache",
                newConfig(config.getMaxLoginUserCount() * 10).expireAfterAccess(config.getSessionTimeout()).useMetrics(),
                null);

        userSessionCache = LocalCache.newCache("login-user-session-cache",
                newConfig(config.getMaxLoginUserCount()).expireAfterAccess(config.getSessionTimeout()).useMetrics(),
                null);

        loginFailCache = LocalCache.newCache("login-fail-cache", newConfig(config.getMaxLoginUserCount() * 2)
                .expireAfterWrite(config.getLoginFailTimeout()).useMetrics(), null);

        verifyCodeCache = LocalCache.newCache("login-verify-code-cache", newConfig(config.getMaxLoginUserCount() * 2)
                .expireAfterWrite(config.getVerifyCodeTimeout()).useMetrics(), null);
    }
}