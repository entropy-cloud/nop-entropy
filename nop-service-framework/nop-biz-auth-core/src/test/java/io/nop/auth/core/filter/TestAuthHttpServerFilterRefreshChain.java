package io.nop.auth.core.filter;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.BaseContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.http.api.server.IHttpServerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C4-007（plan 2508 裁定①）过滤器链序契约（设计 §3.1.3）：刷新以严格领先
 * {@code next.get()} 的串行序消费共享 UserContextImpl；刷新终态续段经
 * {@code ctx.runOnContext} 再入上下文任务队列执行（contextHolder 绑定保持——
 * plan 2508 Major-3 执行介质契约）；再入调用与续段任务体全异常包容，
 * :354 future 无「永不完成」路径。刷新本体异步链的 E2E 证据归属 nop-auth-sso
 * 测试树（TestSsoRefreshAsyncChain），本类钉过滤器侧语义。
 */
public class TestAuthHttpServerFilterRefreshChain {

    // ==================== 链序钉：next.get 严格后于刷新终态 ====================

    /**
     * 刷新挂起期间下游不得执行；刷新终态（携带新 token）后下游才被驱动，
     * 且设头/cookie 步先于 next.get（头/cookie 新鲜度依赖该序）。
     */
    @Test
    @Timeout(20)
    public void testNextGetStrictlyAfterRefreshTerminalState() throws Exception {
        ChainLoginService loginService = new ChainLoginService();
        CompletableFuture<String> pendingRefresh = new CompletableFuture<>();
        loginService.refreshStage = pendingRefresh;
        AuthHttpServerFilter filter = newFilter(loginService);

        TestAuthHttpServerFilterSemantics.OAuthFakeContext routeContext = new TestAuthHttpServerFilterSemantics.OAuthFakeContext();
        AtomicBoolean nextRan = new AtomicBoolean(false);
        Supplier<CompletionStage<Void>> next = () -> {
            nextRan.set(true);
            return CompletableFuture.completedFuture(null);
        };

        CompletionStage<Void> result = filter.handleUserContext(newUserContext(), routeContext, next,
                nearHalfLifeToken());

        assertFalse(nextRan.get(), "downstream must not run while the refresh is still pending");
        assertFalse(result.toCompletableFuture().isDone(),
                "request future must stay pending until the refresh reaches terminal state");

        pendingRefresh.complete("new-token");
        result.toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(nextRan.get(), "downstream must run after the refresh terminal state");
        assertEquals("new-token", routeContext.responseHeaders.get(IHttpServerContext.HEADER_X_ACCESS_TOKEN),
                "refreshed token must be delivered to the response before downstream executes");
        assertTrue(routeContext.cookies.stream().anyMatch(c -> c.getValue().equals("new-token")),
                "refreshed token must be delivered as auth cookie before downstream executes");
        assertEquals(1, loginService.refreshCalls.get(),
                "refresh must go through the async contract exactly once per request");
    }

    // ==================== 执行介质钉：续段经 runOnContext 再入任务队列 ====================

    /**
     * 续段（设头/cookie + next.get）必须运行在 contextHolder 绑定的任务队列介质上：
     * next.get 执行时刻 contextHolder 持有请求 IContext，下游
     * ContextProvider.currentContext() 的 ThreadLocal 读不丢失上下文（Major-3）。
     */
    @Test
    @Timeout(20)
    public void testContinuationRunsOnContextQueueWithContextHolderBound() throws Exception {
        ChainLoginService loginService = new ChainLoginService();
        loginService.refreshStage = CompletableFuture.completedFuture("new-token");
        AuthHttpServerFilter filter = newFilter(loginService);

        TestAuthHttpServerFilterSemantics.OAuthFakeContext routeContext = new TestAuthHttpServerFilterSemantics.OAuthFakeContext();
        IContext requestContext = routeContext.getContext();
        AtomicReference<IContext> boundAtDownstream = new AtomicReference<>();
        Supplier<CompletionStage<Void>> next = () -> {
            boundAtDownstream.set(BaseContextProvider.contextHolder().get());
            return CompletableFuture.completedFuture(null);
        };

        filter.handleUserContext(newUserContext(), routeContext, next, nearHalfLifeToken())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(requestContext, boundAtDownstream.get(),
                "next.get must execute with the request IContext bound in the context holder "
                        + "(runOnContext re-entry medium, not a bare completion thread)");
    }

    // ==================== 挂死面闭合：异常收敛于 future 终态 ====================

    /**
     * 刷新异常（异步失败）→ 降级：warn + 携既有 token 继续，请求正常完成、
     * 下游继续执行、不更新头/cookie（stale-serve，与既有 error→null 语义一致）。
     */
    @Test
    @Timeout(20)
    public void testRefreshAsyncFailureDegradesAndDownstreamContinues() throws Exception {
        ChainLoginService loginService = new ChainLoginService();
        loginService.refreshStage = CompletableFuture.failedFuture(new RuntimeException("idp unreachable"));
        AuthHttpServerFilter filter = newFilter(loginService);

        TestAuthHttpServerFilterSemantics.OAuthFakeContext routeContext = new TestAuthHttpServerFilterSemantics.OAuthFakeContext();
        AtomicBoolean nextRan = new AtomicBoolean(false);
        Supplier<CompletionStage<Void>> next = () -> {
            nextRan.set(true);
            return CompletableFuture.completedFuture(null);
        };

        filter.handleUserContext(newUserContext(), routeContext, next, nearHalfLifeToken())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(nextRan.get(), "stale-serve: downstream must continue on refresh failure");
        assertNull(routeContext.responseHeaders.get(IHttpServerContext.HEADER_X_ACCESS_TOKEN),
                "degraded refresh must not update the x-access-token header");
        assertTrue(routeContext.cookies.isEmpty(),
                "degraded refresh must not update the auth cookie");
    }

    /**
     * 刷新同步抛出（兼容回退型 default 实现内部失败）同样收敛为降级，future 必达终态，
     * 不逃逸 runOnContext 任务体（:354 挂死面闭合——同步腿）。
     */
    @Test
    @Timeout(20)
    public void testRefreshSyncThrowDegradesAndFutureCompletes() throws Exception {
        ChainLoginService loginService = new ChainLoginService();
        loginService.syncThrow = new IllegalStateException("sync refresh boom");
        AuthHttpServerFilter filter = newFilter(loginService);

        TestAuthHttpServerFilterSemantics.OAuthFakeContext routeContext = new TestAuthHttpServerFilterSemantics.OAuthFakeContext();
        AtomicBoolean nextRan = new AtomicBoolean(false);
        Supplier<CompletionStage<Void>> next = () -> {
            nextRan.set(true);
            return CompletableFuture.completedFuture(null);
        };

        filter.handleUserContext(newUserContext(), routeContext, next, nearHalfLifeToken())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(nextRan.get(), "stale-serve: downstream must continue on sync refresh failure");
        assertNull(routeContext.responseHeaders.get(IHttpServerContext.HEADER_X_ACCESS_TOKEN));
    }

    /**
     * next.get() 同步抛出必须透传为请求失败（completeExceptionally），而非静默吞掉或
     * 挂死（:354 闭合——下游失败腿）。
     */
    @Test
    @Timeout(20)
    public void testNextGetSyncThrowCompletesFutureExceptionally() {
        ChainLoginService loginService = new ChainLoginService();
        loginService.refreshStage = CompletableFuture.completedFuture("new-token");
        AuthHttpServerFilter filter = newFilter(loginService);

        TestAuthHttpServerFilterSemantics.OAuthFakeContext routeContext = new TestAuthHttpServerFilterSemantics.OAuthFakeContext();
        Supplier<CompletionStage<Void>> next = () -> {
            throw new IllegalStateException("downstream boom");
        };

        CompletionStage<Void> result = filter.handleUserContext(newUserContext(), routeContext, next,
                nearHalfLifeToken());

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> result.toCompletableFuture().get(10, TimeUnit.SECONDS),
                "downstream sync failure must surface as a failed request future, not a hang");
        // NopException.adapt 对 RuntimeException 保持原异常透传，对受检异常包 NopWrapException——
        // 断言本质：异常被收敛为 future 终态（非挂死、非静默吞）且原始原因可观测
        assertTrue(ex.getCause() instanceof RuntimeException,
                "task body exception must be contained (never escape the runOnContext task) and fail the future");
        assertEquals("downstream boom", ex.getCause().getMessage());
    }

    // ==================== SPI 回归钉：default 兼容回退型转承（9 面族规则） ====================

    /**
     * 未 override 的实现经 default {@code refreshTokenAsync} 转承同步
     * {@code refreshToken}：结果以已完成 stage 返回（AbstractLoginService 族回归面，
     * 本地 JWT LoginServiceImpl 同形态零改造核验）。
     */
    @Test
    @Timeout(20)
    public void testDefaultRefreshTokenAsyncWrapsSyncRefreshToken() throws Exception {
        ILoginService service = new SyncOnlyLoginService();

        assertEquals("sync-token",
                service.refreshTokenAsync(newUserContext(), nearHalfLifeToken()).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS),
                "default refreshTokenAsync must delegate to the sync refreshToken and wrap the result");
    }

    // ==================== fixtures ====================

    private AuthHttpServerFilter newFilter(ILoginService loginService) {
        AuthHttpServerFilter filter = new AuthHttpServerFilter();
        filter.setConfig(new TestAuthHttpServerFilterSemantics.TestAuthFilterConfig());
        filter.setLoginService(loginService);
        filter.init();
        return filter;
    }

    private static UserContextImpl newUserContext() {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId("u1");
        userContext.setUserName("u1");
        userContext.setSessionId("s1");
        userContext.setAccessToken("old-token");
        userContext.setRefreshToken("refresh-token");
        return userContext;
    }

    private static AuthToken nearHalfLifeToken() {
        // 剩余寿命 60s < 半衰期 1800s → isNeedRefresh 命中
        return new AuthToken("old-token", "sub", "u1", "s1",
                System.currentTimeMillis() + 60_000L, 3600, null);
    }

    /**
     * 可控 ILoginService：refreshTokenAsync 编程化（挂起/完成/异常/同步抛出），
     * 其余面沿用既有 TestLoginService 缺省形态（不触达真实链）。
     */
    static class ChainLoginService extends TestAuthHttpServerFilterSemantics.TestLoginService {
        CompletionStage<String> refreshStage;
        RuntimeException syncThrow;
        final AtomicInteger refreshCalls = new AtomicInteger();

        @Override
        public CompletionStage<String> refreshTokenAsync(IUserContext userContext, AuthToken authToken) {
            refreshCalls.incrementAndGet();
            if (syncThrow != null)
                throw syncThrow;
            if (refreshStage != null)
                return refreshStage;
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * SPI 回归面形态（default 转承面）：仅实现同步 refreshToken，refreshTokenAsync 走
     * ILoginService default（与 AbstractLoginService 子类 / 本地 JWT 同形态）。
     */
    static class SyncOnlyLoginService implements ILoginService {

        @Override
        public CompletionStage<IUserContext> loginAsync(LoginRequest request, Map<String, Object> headers) {
            throw new UnsupportedOperationException("not exercised by the refresh chain pin");
        }

        @Override
        public CompletionStage<Void> logoutAsync(int logoutType, LogoutRequest request) {
            throw new UnsupportedOperationException("not exercised by the refresh chain pin");
        }

        @Override
        public CompletionStage<Void> killLoginAsync(String userName) {
            throw new UnsupportedOperationException("not exercised by the refresh chain pin");
        }

        @Override
        public CompletionStage<Void> flushUserContextAsync(IUserContext userContext) {
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<IUserContext> getUserContextAsync(AuthToken accessToken, Map<String, Object> headers) {
            return FutureHelper.success(null);
        }

        @Override
        public CompletionStage<IUserContext> getLoginUserContextAsync(String userName) {
            return FutureHelper.success(null);
        }

        @Override
        public LoginUserInfo getUserInfo(IUserContext userContext) {
            return null;
        }

        @Override
        public String generateVerifyCode(String verifySecret) {
            throw new UnsupportedOperationException("not exercised by the refresh chain pin");
        }

        @Override
        public AuthToken parseAuthToken(String accessToken) {
            return null;
        }

        @Override
        public String refreshToken(IUserContext userContext, AuthToken authToken) {
            return "sync-token";
        }
    }
}
