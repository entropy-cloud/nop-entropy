/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_LOGIN_NOT_CURRENT_SESSION;

public class AbstractUserContextCache implements IUserContextCache, IConfigRefreshable {
    protected ICache<String, IUserContext> userContextCache;
    protected ICache<String, String> verifyCodeCache;
    protected ICache<String, String> userSessionCache;

    /**
     * 登录失败计数 store（design nop-auth §3.2，plan 2274 Phase 2）：可选注入 + 内联缺省
     * Local 实例（LocalUserContextCache.init() 构造，per-instance，等价原 loginFailCache
     * 语义）。容器路径注入 {@code nopActiveLoginAttemptStore}（local/redis 按配置选择）。
     */
    @Inject
    @Nullable
    protected ILoginAttemptStore loginAttemptStore;

    protected UserContextConfig config;

    @Inject
    public void setUserContextConfig(UserContextConfig config) {
        this.config = config;
    }

    /** 容器/测试显式替换验证码缓存（须满足 design §3.3 TTL 保持契约：写入带 verifyCodeTimeout TTL）。 */
    public void setVerifyCodeCache(ICache<String, String> verifyCodeCache) {
        this.verifyCodeCache = verifyCodeCache;
    }

    /** 容器/测试显式替换失败计数 store。 */
    public void setLoginAttemptStore(ILoginAttemptStore loginAttemptStore) {
        this.loginAttemptStore = loginAttemptStore;
    }

    /**
     * 失败计数 store 解析：注入优先，缺省回落 JVM 共享 Local 实例（design §3.2 同一性裁定）。
     * public 供 {@code LoginServiceImpl} 等宿主解析同一实例（手工 wiring 写读一致性）。
     */
    public ILoginAttemptStore attemptStore() {
        if (loginAttemptStore != null)
            return loginAttemptStore;
        return LoginAttemptStores.sharedLocalDefault();
    }

    @Override
    public void refreshConfig() {
        userContextCache.getConfig().setExpireAfterAccess(config.getSessionTimeout());
        userSessionCache.getConfig().setExpireAfterAccess(config.getSessionTimeout());

        if (verifyCodeCache.getConfig() != null) {
            verifyCodeCache.getConfig().setExpireAfterWrite(config.getVerifyCodeTimeout());
            verifyCodeCache.refreshConfig();
        }

        userContextCache.refreshConfig();
        userSessionCache.refreshConfig();

        if (loginAttemptStore instanceof IConfigRefreshable)
            ((IConfigRefreshable) loginAttemptStore).refreshConfig();
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
        userContext.clearDirty();
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
        return attemptStore().getLoginFailCount(ILoginAttemptStore.userKey(userName));
    }

    @Override
    public void setLoginFailCountForUser(String userName, int count) {
        attemptStore().setLoginFailCount(ILoginAttemptStore.userKey(userName), count);
    }

    @Override
    public void resetLoginFailCountForUser(String userName) {
        attemptStore().resetLoginFailCount(ILoginAttemptStore.userKey(userName));
    }

    @Override
    public int getLoginFailCountForIp(String ip) {
        // plan 2274 Fix：原实现误用 userKey(ip)（"un:" 前缀）读、ipKey 写——读写前缀不一致的
        // 既有缺陷（生产无调用方），统一为 ip: 维度
        return attemptStore().getLoginFailCount(ILoginAttemptStore.ipKey(ip));
    }

    @Override
    public void resetLoginFailCountForIp(String ip) {
        attemptStore().resetLoginFailCount(ILoginAttemptStore.ipKey(ip));
    }

    @Override
    public void setLoginFailCountForIp(String ip, int count) {
        attemptStore().setLoginFailCount(ILoginAttemptStore.ipKey(ip), count);
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
