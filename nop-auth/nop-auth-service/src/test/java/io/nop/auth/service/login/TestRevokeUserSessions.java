package io.nop.auth.service.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.core.login.AbstractLoginService;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginSessionStore;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 nop-auth 报告 [P3] changeSelfPassword/resetUserPassword 后不失效既有会话：
 * 修复后 ILoginService.revokeUserSessionsAsync 吊销目标用户全部活动会话（KILL 语义，
 * 含 onLogout 钩子与缓存上下文失效），exceptSessionId 非空时保留该会话（本人改密保留当前会话）。
 */
public class TestRevokeUserSessions {

    static class LogoutRecord {
        final String sessionId;
        final int logoutType;

        LogoutRecord(String sessionId, int logoutType) {
            this.sessionId = sessionId;
            this.logoutType = logoutType;
        }
    }

    static class FakeSessionStore implements ILoginSessionStore {
        final List<String> sessions = List.of("sess-1", "sess-2", "sess-3");
        final List<LogoutRecord> logouts = new CopyOnWriteArrayList<>();

        @Override
        public SessionInfo getSessionInfoForUser(String userName) {
            return null;
        }

        @Override
        public String saveSession(IUserContext userContext, LoginRequest request, Map<String, Object> headers) {
            return null;
        }

        @Override
        public void logoutSession(String sessionId, int logoutType, String logoutUser) {
            logouts.add(new LogoutRecord(sessionId, logoutType));
        }

        @Override
        public List<String> getActionSessions(String userName) {
            return sessions;
        }
    }

    static class FakeContextCache implements IUserContextCache {
        final List<SessionInfo> removed = new CopyOnWriteArrayList<>();

        @Override
        public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
            removed.add(sessionInfo);
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<String> getUserSessionId(String userName) {
            return FutureHelper.success(null);
        }

        @Override
        public int getLoginFailCountForUser(String userName) {
            return 0;
        }

        @Override
        public int getLoginFailCountForIp(String ip) {
            return 0;
        }

        @Override
        public void setLoginFailCountForUser(String userName, int count) {
        }

        @Override
        public void resetLoginFailCountForUser(String userName) {
        }

        @Override
        public void resetLoginFailCountForIp(String ip) {
        }

        @Override
        public void setLoginFailCountForIp(String ip, int count) {
        }

        @Override
        public String getVerifyCode(String key) {
            return null;
        }

        @Override
        public void setVerifyCode(String key, String code) {
        }
    }

    static class MinimalLoginService extends AbstractLoginService {
        void inject(ILoginSessionStore store, IUserContextCache cache) {
            this.loginSessionStore = store;
            this.userContextCache = cache;
        }

        @Override
        public CompletionStage<IUserContext> loginAsync(LoginRequest request, Map<String, Object> headers) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public CompletionStage<Void> logoutAsync(int logoutType, LogoutRequest request) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public LoginUserInfo getUserInfo(IUserContext userContext) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public String generateVerifyCode(String verifySecret) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public AuthToken parseAuthToken(String accessToken) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public String refreshToken(IUserContext userContext, AuthToken authToken) {
            throw new UnsupportedOperationException("not needed in this test");
        }
    }

    private static MinimalLoginService serviceWith(FakeSessionStore store, FakeContextCache cache) {
        MinimalLoginService service = new MinimalLoginService();
        service.inject(store, cache);
        return service;
    }

    @Test
    public void testRevokeAllSessionsForAdminPasswordReset() {
        FakeSessionStore store = new FakeSessionStore();
        FakeContextCache cache = new FakeContextCache();
        MinimalLoginService service = serviceWith(store, cache);

        FutureHelper.syncGet(service.revokeUserSessionsAsync("alice", null));

        assertEquals(3, store.logouts.size(), "admin password reset must revoke ALL target sessions");
        assertEquals(3, cache.removed.size(), "cached user contexts must be invalidated too");
        for (LogoutRecord r : store.logouts) {
            assertEquals(AuthApiConstants.LOGOUT_TYPE_KILL, r.logoutType, "revocation uses KILL semantics");
        }
    }

    @Test
    public void testRevokeKeepsCurrentSessionForSelfPasswordChange() {
        FakeSessionStore store = new FakeSessionStore();
        FakeContextCache cache = new FakeContextCache();
        MinimalLoginService service = serviceWith(store, cache);

        FutureHelper.syncGet(service.revokeUserSessionsAsync("alice", "sess-2"));

        List<String> loggedOut = new ArrayList<>();
        store.logouts.forEach(r -> loggedOut.add(r.sessionId));
        assertEquals(2, loggedOut.size(), "self password change revokes other sessions only");
        assertTrue(loggedOut.contains("sess-1") && loggedOut.contains("sess-3"),
                "other sessions must be revoked");
        assertTrue(!loggedOut.contains("sess-2"), "current session must survive self password change");
    }
}
