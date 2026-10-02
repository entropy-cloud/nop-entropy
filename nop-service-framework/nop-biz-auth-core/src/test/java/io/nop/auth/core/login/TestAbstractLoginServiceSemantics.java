package io.nop.auth.core.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbstractLoginService} 模板方法语义：脏上下文回写、按会话取用户上下文、
 * kill/revoke 会话管理（KILL 语义 + exceptSessionId 保留）、getUserInfo 字段映射。
 * 纯单元测试：内存 fake cache/session store + 记录型 hook，无需 IoC 容器。
 */
public class TestAbstractLoginServiceSemantics {

    // ==================== flushUserContextAsync ====================

    /**
     * 干净（非 dirty）上下文不触发缓存回写——避免无意义的缓存写放大。
     */
    @Test
    public void testFlushCleanContextSkipsCacheSave() throws Exception {
        FakeCache cache = new FakeCache();
        RecordingHook hook = new RecordingHook();
        AbstractLoginService service = newService(cache, hook);

        UserContextImpl context = new UserContextImpl();
        context.setSessionId("s1");
        // 纯单元测试中 @InjectValue/@Inject 不生效，setter 置脏后需显式清零以构造干净上下文
        context.clearDirty();
        service.flushUserContextAsync(context).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, cache.saveCount, "clean context must not be saved to cache");
        assertTrue(hook.updates.isEmpty(), "clean context must not fire onUpdate hook");
    }

    /**
     * dirty 上下文回写缓存：先触发 onUpdate 钩子，保存完成后清除脏标记。
     */
    @Test
    public void testFlushDirtyContextSavesAndClearsDirty() throws Exception {
        FakeCache cache = new FakeCache();
        RecordingHook hook = new RecordingHook();
        AbstractLoginService service = newService(cache, hook);

        UserContextImpl context = new UserContextImpl();
        context.setSessionId("s1");
        context.setUserId("u1");
        context.markDirty();

        service.flushUserContextAsync(context).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(1, cache.saveCount, "dirty context must be saved once");
        assertEquals(List.of("u1"), hook.updates, "onUpdate hook must fire for dirty context");
        assertTrue(!context.dirty(), "dirty flag must be cleared after successful save");
    }

    // ==================== getUserContextAsync / getLoginUserContextAsync ====================

    /**
     * 按token中的sessionId从缓存取用户上下文，并触发 onAccess 钩子（在线时长统计挂点）。
     */
    @Test
    public void testGetUserContextFiresOnAccessAndReturnsCached() throws Exception {
        FakeCache cache = new FakeCache();
        UserContextImpl cached = new UserContextImpl();
        cached.setUserId("u1");
        cache.contexts.put("s1", cached);

        RecordingHook hook = new RecordingHook();
        AbstractLoginService service = newService(cache, hook);

        IUserContext result = service.getUserContextAsync(token("s1"), new HashMap<>())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals("u1", result.getUserId(), "cached context must be returned for known session");
        assertEquals(List.of("u1"), hook.accesses, "onAccess hook must fire on cache hit");
    }

    /**
     * 未知 sessionId 返回 null（未登录语义），不触发 onAccess。
     */
    @Test
    public void testGetUserContextUnknownSessionReturnsNull() throws Exception {
        FakeCache cache = new FakeCache();
        RecordingHook hook = new RecordingHook();
        AbstractLoginService service = newService(cache, hook);

        IUserContext result = service.getUserContextAsync(token("no-such"), new HashMap<>())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertNull(result, "unknown session must resolve to null user context");
        assertTrue(hook.accesses.isEmpty(), "onAccess must not fire on cache miss");
    }

    /**
     * getLoginUserContextAsync：登录中的用户经其 session 取上下文；未登录用户返回 null。
     */
    @Test
    public void testGetLoginUserContextResolvesViaSessionStore() throws Exception {
        FakeCache cache = new FakeCache();
        UserContextImpl cached = new UserContextImpl();
        cached.setUserId("u1");
        cache.contexts.put("s1", cached);

        FakeSessionStore store = new FakeSessionStore();
        store.sessions.put("alice", new SessionInfo("alice", "s1"));

        AbstractLoginService service = newService(cache, new RecordingHook(), store);

        IUserContext online = service.getLoginUserContextAsync("alice")
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals("u1", online.getUserId(), "logged-in user must resolve via stored session");

        IUserContext offline = service.getLoginUserContextAsync("bob")
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNull(offline, "user without session must resolve to null");
    }

    // ==================== killLogin / revokeUserSessions ====================

    /**
     * killLogin：管理性终止（LOGOUT_TYPE_KILL）——登出 session、触发 onLogout、删缓存。
     */
    @Test
    public void testKillLoginPerformsKillLogout() throws Exception {
        FakeCache cache = new FakeCache();
        FakeSessionStore store = new FakeSessionStore();
        store.sessions.put("alice", new SessionInfo("alice", "s1"));
        RecordingHook hook = new RecordingHook();
        AbstractLoginService service = newService(cache, hook, store);

        service.killLoginAsync("alice").toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(store.loggedOut.containsKey("s1"), "kill must log out the user's session");
        assertEquals(AuthApiConstants.LOGOUT_TYPE_KILL, store.loggedOut.get("s1")[1],
                "killLogin must use LOGOUT_TYPE_KILL semantics");
        assertEquals(List.of("alice"), hook.logouts.stream().map(l -> l[0]).toList(),
                "onLogout hook must fire with the killed user");
        assertEquals(1, cache.removeCount, "killed session must be removed from cache");
    }

    /**
     * killLogin 对未登录用户是安全 no-op（正常完成，不报错）。
     */
    @Test
    public void testKillLoginUnknownUserIsNoOp() throws Exception {
        FakeCache cache = new FakeCache();
        FakeSessionStore store = new FakeSessionStore();
        AbstractLoginService service = newService(cache, new RecordingHook(), store);

        service.killLoginAsync("ghost").toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(store.loggedOut.isEmpty(), "no session may be logged out for unknown user");
        assertEquals(0, cache.removeCount, "cache must not be touched for unknown user");
    }

    /**
     * revokeUserSessions：吊销全部会话但保留 exceptSessionId（本人改密保留当前设备）。
     */
    @Test
    public void testRevokeUserSessionsKeepsExceptSession() throws Exception {
        FakeCache cache = new FakeCache();
        FakeSessionStore store = new FakeSessionStore();
        store.actionSessions.put("alice", List.of("s1", "s2", "s3"));
        AbstractLoginService service = newService(cache, new RecordingHook(), store);

        service.revokeUserSessionsAsync("alice", "s2").toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(2, store.loggedOut.size(), "all sessions except s2 must be logged out");
        assertTrue(store.loggedOut.containsKey("s1") && store.loggedOut.containsKey("s3"),
                "s1/s3 must be killed, got: " + store.loggedOut.keySet());
        assertTrue(!store.loggedOut.containsKey("s2"), "exceptSessionId must be preserved");
    }

    /**
     * 未装配 session store 时 revoke 是安全 no-op（快速成功，不抛异常）。
     */
    @Test
    public void testRevokeWithoutSessionStoreSucceeds() throws Exception {
        AbstractLoginService service = newService(new FakeCache(), new RecordingHook(), null);

        service.revokeUserSessionsAsync("alice", null).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    // ==================== getUserInfo ====================

    /**
     * getUserInfo 映射用户上下文到 LoginUserInfo：returnUserId=true 时携带 userId，
     * 角色集合映射为 RoleInfo 列表。（配置项默认值经 @InjectValue 在 IoC 下注入；
     * 纯单元测试需显式 setter。）
     */
    @Test
    public void testGetUserInfoMapsFieldsAndRoles() throws Exception {
        AbstractLoginService service = newService(new FakeCache(), new RecordingHook(),
                new FakeSessionStore());
        service.setReturnUserId(true);

        UserContextImpl context = new UserContextImpl();
        context.setUserId("u1");
        context.setUserName("alice");
        context.setNickName("Alice");
        context.setRoles(Set.of("admin", "user"));

        LoginUserInfo info = service.getUserInfo(context);

        assertEquals("alice", info.getUserName());
        assertEquals("Alice", info.getNickName());
        assertEquals("u1", info.getUserId(), "returnUserId=true by default must expose userId");
        assertEquals(2, info.getRoleInfos().size(), "each role must map to a RoleInfo entry");
    }

    /**
     * returnUserId=false 时 userInfo 不泄露 userId（隐私收缩语义）。
     */
    @Test
    public void testGetUserInfoOmitsUserIdWhenDisabled() throws Exception {
        AbstractLoginService service = newService(new FakeCache(), new RecordingHook(),
                new FakeSessionStore());
        service.setReturnUserId(false);

        UserContextImpl context = new UserContextImpl();
        context.setUserId("u1");
        context.setUserName("alice");

        assertNull(service.getUserInfo(context).getUserId(),
                "returnUserId=false must omit userId from user info");
    }

    /**
     * getUserInfo(null) 返回 null（无用户上下文时无 userInfo）。
     */
    @Test
    public void testGetUserInfoNullContextReturnsNull() throws Exception {
        AbstractLoginService service = newService(new FakeCache(), new RecordingHook(),
                new FakeSessionStore());
        assertNull(service.getUserInfo(null));
    }

    // ==================== fixtures ====================

    private static AuthToken token(String sessionId) {
        return new AuthToken("token-" + sessionId, "sub", "alice", sessionId,
                System.currentTimeMillis() + 60_000L, 3600, new HashMap<>());
    }

    private static AbstractLoginService newService(IUserContextCache cache, IUserContextHook hook) {
        return newService(cache, hook, new FakeSessionStore());
    }

    private static AbstractLoginService newService(IUserContextCache cache, IUserContextHook hook,
                                                   ILoginSessionStore store) {
        TestLoginService service = new TestLoginService();
        service.userContextCache = cache;
        service.userContextHook = hook;
        service.loginSessionStore = store;
        return service;
    }

    static class TestLoginService extends AbstractLoginService {
        @Override
        public CompletionStage<IUserContext> loginAsync(LoginRequest request, Map<String, Object> headers) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> logoutAsync(int logoutType, LogoutRequest request) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String generateVerifyCode(String verifySecret) {
            return "code";
        }

        @Override
        public AuthToken parseAuthToken(String accessToken) {
            return null;
        }

        @Override
        public String refreshToken(IUserContext userContext, AuthToken authToken) {
            return null;
        }
    }

    static class FakeCache implements IUserContextCache {
        final Map<String, UserContextImpl> contexts = new LinkedHashMap<>();
        int saveCount;
        int removeCount;

        @Override
        public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
            return CompletableFuture.completedFuture(contexts.get(sessionId));
        }

        @Override
        public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
            saveCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
            removeCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<String> getUserSessionId(String userName) {
            return CompletableFuture.completedFuture(null);
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

    static class FakeSessionStore implements ILoginSessionStore {
        final Map<String, SessionInfo> sessions = new LinkedHashMap<>();
        final Map<String, List<String>> actionSessions = new LinkedHashMap<>();
        final Map<String, Object[]> loggedOut = new LinkedHashMap<>();

        @Override
        public SessionInfo getSessionInfoForUser(String userName) {
            return sessions.get(userName);
        }

        @Override
        public String saveSession(IUserContext userContext, LoginRequest request,
                                  Map<String, Object> headers) {
            return null;
        }

        @Override
        public void logoutSession(String sessionId, int logoutType, String logoutUser) {
            loggedOut.put(sessionId, new Object[]{logoutUser, logoutType});
        }

        @Override
        public List<String> getActionSessions(String userName) {
            List<String> list = actionSessions.get(userName);
            return list == null ? List.of() : list;
        }
    }

    static class RecordingHook implements IUserContextHook {
        final List<String> updates = new java.util.ArrayList<>();
        final List<String> accesses = new java.util.ArrayList<>();
        final List<Object[]> logouts = new java.util.ArrayList<>();
        final Set<String> loginSuccess = new HashSet<>();

        @Override
        public void onLoginSuccess(IUserContext context, LoginRequest request) {
            loginSuccess.add(context.getUserId());
        }

        @Override
        public void onLoginFail(LoginRequest request, io.nop.api.core.exceptions.ErrorCode errorCode,
                                String userName, int failCount) {
        }

        @Override
        public void onLogout(String userName, String sessionId, int logoutType) {
            logouts.add(new Object[]{userName, sessionId, logoutType});
        }

        @Override
        public void onAccess(IUserContext context) {
            accesses.add(context.getUserId());
        }

        @Override
        public void onUpdate(IUserContext context) {
            updates.add(context.getUserId());
        }
    }
}
