/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.util.StringHelper;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

import static io.nop.commons.cache.CacheConfig.newConfig;

public class LocalUserContextCache implements IUserContextCache, IConfigRefreshable {
    private LocalCache<String, IUserContext> userContextCache;
    private LocalCache<String, Integer> loginFailCache;
    private LocalCache<String, String> verifyCodeCache;

    private UserContextConfig config;

    @Inject
    public void setUserContextConfig(UserContextConfig config) {
        this.config = config;
    }

    @Override
    public void refreshConfig() {
        userContextCache.getConfig().setExpireAfterAccess(config.getSessionTimeout());
        verifyCodeCache.getConfig().setExpireAfterWrite(config.getVerifyCodeTimeout());
        loginFailCache.getConfig().setExpireAfterWrite(config.getLoginFailTimeout());

        userContextCache.refreshConfig();
        verifyCodeCache.refreshConfig();
        loginFailCache.refreshConfig();
    }

    @PostConstruct
    public void init() {
        userContextCache = LocalCache.newCache("user-context-cache",
                newConfig(config.getMaxLoginUserCount()).expireAfterAccess(config.getSessionTimeout()).useMetrics(),
                null);

        loginFailCache = LocalCache.newCache("login-fail-cache", newConfig(config.getMaxLoginUserCount() * 2)
                .expireAfterWrite(config.getLoginFailTimeout()).useMetrics(), null);

        verifyCodeCache = LocalCache.newCache("verify-code-cache", newConfig(config.getMaxLoginUserCount() * 2)
                .expireAfterWrite(config.getVerifyCodeTimeout()).useMetrics(), null);
    }

    @Override
    public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
        IUserContext userContext = sessionId == null ? null : userContextCache.get(sessionId);
        return FutureHelper.success(userContext);
    }

    @Override
    public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
        userContextCache.put(userContext.getSessionId(), userContext);
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> removeUserContextAsync(String sessionId) {
        userContextCache.remove(sessionId);
        return FutureHelper.success(null);
    }

    @Override
    public int getLoginFailCountForUser(String userName) {
        return ConvertHelper.toPrimitiveInt(loginFailCache.get(userKey(userName)), 0, NopException::new);
    }

    @Override
    public void setLoginFailCountForUser(String userName, int count) {
        loginFailCache.put(userKey(userName), count);
    }

    @Override
    public void resetLoginFailCountForUser(String userName) {
        loginFailCache.remove(userKey(userName));
    }

    @Override
    public int getLoginFailCountForIp(String ip) {
        return ConvertHelper.toPrimitiveInt(loginFailCache.get(userKey(ip)), 0, NopException::new);
    }

    @Override
    public void resetLoginFailCountForIp(String ip) {
        loginFailCache.remove(ipKey(ip));
    }

    @Override
    public void setLoginFailCountForIp(String ip, int count) {
        loginFailCache.put(ipKey(ip), count);
    }

    private String userKey(String userName) {
        return "un:" + userName;
    }

    private String ipKey(String ip) {
        return "ip:" + ip;
    }

    @Override
    public String getVerifyCode(String key) {
        return verifyCodeCache.get(buildVerifyCacheKey(key));
    }

    @Override
    public void setVerifyCode(String key, String code) {
        verifyCodeCache.put(buildVerifyCacheKey(key), code);
    }

    String buildVerifyCacheKey(String key) {
        return "vc:" + StringHelper.md5Hash(key + config.getVerifyKey());
    }
}