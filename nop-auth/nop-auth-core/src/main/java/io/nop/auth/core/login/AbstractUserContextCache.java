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
import io.nop.api.core.exceptions.NopLoginException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.cache.ICache;
import io.nop.commons.util.StringHelper;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_LOGIN_NOT_CURRENT_SESSION;

public class AbstractUserContextCache implements IUserContextCache, IConfigRefreshable {
    protected ICache<String, IUserContext> userContextCache;
    protected ICache<String, Integer> loginFailCache;
    protected ICache<String, String> verifyCodeCache;
    protected ICache<String, String> userSessionCache;

    protected UserContextConfig config;

    @Inject
    public void setUserContextConfig(UserContextConfig config) {
        this.config = config;
    }

    @Override
    public void refreshConfig() {
        userContextCache.getConfig().setExpireAfterAccess(config.getSessionTimeout());
        userSessionCache.getConfig().setExpireAfterAccess(config.getSessionTimeout());

        verifyCodeCache.getConfig().setExpireAfterWrite(config.getVerifyCodeTimeout());
        loginFailCache.getConfig().setExpireAfterWrite(config.getLoginFailTimeout());

        userContextCache.refreshConfig();
        verifyCodeCache.refreshConfig();
        loginFailCache.refreshConfig();
        userSessionCache.refreshConfig();
    }

    @Override
    public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
        if (sessionId == null)
            return FutureHelper.success(null);

        return userContextCache.getAsync(sessionId).thenApply(userContext -> {
            if (userContext != null) {
                String currentSessionId = userSessionCache.get(userContext.getUserName());
                // 如果已经超时
                if (StringHelper.isEmpty(currentSessionId))
                    return null;

                if (!userContext.getSessionId().equals(currentSessionId)) {
                    userContextCache.removeAsync(sessionId);
                    throw new NopLoginException(ERR_AUTH_LOGIN_NOT_CURRENT_SESSION);
                }
            }
            return userContext;
        });
    }

    public CompletionStage<String> getUserSessionId(String userName) {
        return userSessionCache.getAsync(userName);
    }

    @Override
    public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
        return userSessionCache.putAsync(userContext.getUserName(), userContext.getSessionId())
                .thenCompose(v -> userContextCache.putAsync(userContext.getSessionId(), userContext));
    }

    @Override
    public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
        userSessionCache.removeIfMatchAsync(sessionInfo.getUserName(), sessionInfo.getSessionId());
        return userContextCache.removeAsync(sessionInfo.getSessionId());
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