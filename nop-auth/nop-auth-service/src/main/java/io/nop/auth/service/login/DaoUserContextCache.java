package io.nop.auth.service.login;

import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.core.login.LocalUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthSession;
import io.nop.commons.util.DateHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class DaoUserContextCache extends LocalUserContextCache {
    @Inject
    IDaoProvider daoProvider;

    private IEntityDao<NopAuthSession> dao() {
        return daoProvider.daoFor(NopAuthSession.class);
    }

    @Override
    public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
        return FutureHelper.futureCall(() -> {
            NopAuthSession session = dao().getEntityById(sessionId);
            if (session == null || session.getLogoutType() != AuthApiConstants.LOGOUT_TYPE_NONE)
                return null;

            UserContextImpl userContext = new UserContextImpl();

            Map<String, Object> map = (Map<String, Object>) JsonTool.parse(session.getCacheData());
            if (map != null) {
                BeanTool.instance().setProperties(userContext, map);
            }

            userContext.setUserId(session.getUserId());
            userContext.setUserName(session.getUserName());
            userContext.setTenantId(session.getTenantId());
            userContext.setLoginType(session.getLoginType());
            userContext.setAccessToken(session.getAccessToken());
            userContext.setRefreshToken(session.getRefreshToken());

            userContext.setLastAccessTime(CoreMetrics.currentTimeMillis());
            return userContext;
        });

    }

    @SingleSession
    @Override
    public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
        return FutureHelper.futureRun(() -> {
            NopAuthSession session = dao().getEntityById(userContext.getSessionId());
            if (session == null || session.getLogoutType() != AuthApiConstants.LOGOUT_TYPE_NONE)
                return;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("nickName", userContext.getNickName());
            map.put("locale", userContext.getLocale());
            map.put("timeZone", userContext.getTimeZone());
            map.put("openId", userContext.getOpenId());
            if (userContext.getPrimaryRole() != null)
                map.put("primaryRole", userContext.getPrimaryRole());
            map.put("roles", userContext.getRoles());
            map.put("deptId", userContext.getDeptId());
            map.put("deptName", userContext.getDeptName());
            if (userContext.getAttrs() != null) {
                map.put("attrs", userContext.getAttrs());
            }

            session.setAccessToken(userContext.getAccessToken());
            session.setRefreshToken(userContext.getRefreshToken());
            session.setCacheData(JsonTool.stringify(map));
            session.setLastAccessTime(DateHelper.millisToDateTime(userContext.getLastAccessTime()));

            userContext.clearDirty();
        });
    }

    @Override
    public CompletionStage<String> getUserSessionId(String userName) {
        return FutureHelper.futureCall(() -> {
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq(NopAuthSession.PROP_NAME_userName, userName))
                    .addFilter(FilterBeans.eq(NopAuthSession.PROP_NAME_logoutType, AuthApiConstants.LOGOUT_TYPE_NONE));
            query.addOrderField(NopAuthSession.PROP_NAME_loginTime, true);
            NopAuthSession session = dao().findFirstByQuery(query);
            return session == null ? null : session.getSessionId();
        });
    }

    @Override
    public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
        // 不需要删除
        return FutureHelper.success(null);
    }
}
