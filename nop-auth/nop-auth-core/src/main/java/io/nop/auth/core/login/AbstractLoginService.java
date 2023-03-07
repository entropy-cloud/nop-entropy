/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginUserInfo;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public abstract class AbstractLoginService implements ILoginService {
    @Inject
    @Nullable
    protected IUserContextHook userContextHook;

    @Inject
    protected IUserContextCache userContextCache;

    @Inject
    @Nullable
    protected ILoginSessionStore loginSessionStore;

    @Override
    public CompletionStage<Void> flushUserContextAsync(IUserContext userContext) {
        if (!userContext.dirty()) {
            return FutureHelper.success(null);
        }

        if (userContextHook != null)
            userContextHook.onUpdate(userContext);

        return userContextCache.saveUserContextAsync(userContext).thenRun(userContext::clearDirty);

    }

    @Override
    public CompletionStage<IUserContext> getUserContextAsync(AuthToken token) {
        String sessionId = token.getSessionId();
        return doGetUserContext(sessionId);
    }

    @Override
    public CompletionStage<IUserContext> getLoginUserContextAsync(String userName) {
        SessionInfo sessionInfo = getSessionInfoForUser(userName);
        if (sessionInfo == null)
            return FutureHelper.success(null);
        return doGetUserContext(sessionInfo.getSessionId());
    }

    protected CompletionStage<IUserContext> doGetUserContext(String sessionId) {
        return userContextCache.getUserContextAsync(sessionId).thenApply(userContext -> {
            if (userContext == null)
                return null;

            if (userContextHook != null)
                userContextHook.onAccess(userContext);
            return userContext;
        });
    }

    @Override
    public CompletionStage<Void> killLoginAsync(String userName) {
        SessionInfo sessionInfo = getSessionInfoForUser(userName);
        if (sessionInfo == null)
            return FutureHelper.success(null);
        return doLogout(AuthApiConstants.LOGOUT_TYPE_KILL, sessionInfo);
    }

    protected SessionInfo getSessionInfoForUser(String userName) {
        if (loginSessionStore == null)
            return null;
        return loginSessionStore.getSessionInfoForUser(userName);
    }

    protected CompletionStage<Void> doLogout(int logoutType, SessionInfo sessionInfo) {
        String currentUser = ContextProvider.currentUserRefNo();

        if (loginSessionStore != null) {
            loginSessionStore.logoutSession(sessionInfo.getSessionId(), logoutType, currentUser);
        }

        if (userContextHook != null) {
            userContextHook.onLogout(sessionInfo.getUserName(), sessionInfo.getSessionId(), logoutType);
        }
        return userContextCache.removeUserContextAsync(sessionInfo.getSessionId());
    }


    @Override
    public LoginUserInfo getUserInfo(IUserContext userContext) {
        if (userContext == null)
            return null;

        LoginUserInfo info = new LoginUserInfo();
        info.setUserName(userContext.getUserName());
        info.setNickName(userContext.getNickName());
        info.setLocale(userContext.getLocale());
        info.setTimeZone(userContext.getTimeZone());
        info.setTenantId(userContext.getTenantId());
        info.setRoles(userContext.getRoles());
        info.setOpenId(userContext.getOpenId());
        info.setDeptId(userContext.getDeptId());
        info.setRoles(userContext.getRoles());
        return info;
    }
}
