/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.ISessionIdGenerator;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.dao.api.IDaoProvider;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DaoLoginSessionStore implements ILoginSessionStore {
    @Inject
    protected ISessionIdGenerator sessionIdGenerator;

    @Inject
    protected IDaoProvider daoProvider;

    @Override
    public SessionInfo getSessionInfoForUser(String userName) {
        Guard.notEmpty(userName, "userName");

        NopAuthSession example = new NopAuthSession();
        example.setUserName(userName);
        example.setLoginType(AuthApiConstants.LOGOUT_TYPE_NONE);

        NopAuthSession session = daoProvider.daoFor(NopAuthSession.class).findFirstByExample(example);
        if (session == null)
            return null;
        return new SessionInfo(userName, session.getSessionId());
    }

    @Override
    public String saveSession(IUserContext userContext, LoginRequest request, Map<String, Object> headers) {
        Guard.notEmpty(userContext.getUserId(), "userId");
        Guard.notEmpty(userContext.getUserName(), "userName");

        NopAuthSession session = new NopAuthSession();
        session.setSessionId(sessionIdGenerator.generateId());
        session.setTenantId(userContext.getTenantId());
        session.setUserId(userContext.getUserId());
        session.setUserName(userContext.getUserName());
        session.setLoginType(request.getLoginType());
        session.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE);
        session.setLoginTime(CoreMetrics.currentTimestamp());
        session.setLoginAddr(ApiHeaders.getStringHeader(headers, ApiConstants.HEADER_CLIENT_ADDR));
        session.setLoginDevice(request.getDeviceType());
        session.setLoginApp(request.getAppName());
        session.setLoginOs(request.getDeviceOs());
        daoProvider.daoFor(NopAuthSession.class).saveEntity(session);
        return session.getSessionId();
    }

    @Override
    public void logoutSession(String sessionId, int logoutType, String logoutUser) {
        NopAuthSession session = loadSession(sessionId);
        if (session != null) {
            if (session.getLogoutType() == null || AuthApiConstants.LOGOUT_TYPE_NONE == session.getLogoutType()) {
                session.setLogoutType(logoutType);
                session.setLogoutTime(CoreMetrics.currentTimestamp());
                session.setLogoutBy(logoutUser);
            }
        }
    }

    protected NopAuthSession loadSession(String sessionId) {
        return daoProvider.daoFor(NopAuthSession.class).getEntityById(sessionId);
    }

    @Override
    public List<String> getActionSessions(String userName) {
        Guard.notEmpty(userName, "userName");

        NopAuthSession example = new NopAuthSession();
        example.setUserName(userName);
        example.setLogoutType(AuthApiConstants.LOGOUT_TYPE_NONE);
        List<NopAuthSession> sessions = daoProvider.daoFor(NopAuthSession.class).findAllByExample(example);

        List<String> sessionIds = new ArrayList<>(sessions.size());
        for (NopAuthSession session : sessions) {
            sessionIds.add(session.getSessionId());
        }
        return sessionIds;
    }

}