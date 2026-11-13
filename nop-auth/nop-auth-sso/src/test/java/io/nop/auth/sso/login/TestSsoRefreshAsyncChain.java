package io.nop.auth.sso.login;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.BaseContext;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.ICancelToken;
import io.nop.auth.core.filter.AuthFilterConfig;
import io.nop.auth.core.filter.AuthHttpServerFilter;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.IUserContextCache;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.sso.AccessTokenResponse;
import io.nop.auth.sso.SsoConfig;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.support.DefaultHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.HttpCookie;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C4-007（plan 2508 裁定①）E2E 链路①：真实 {@link OAuthLoginServiceImpl} + fake
 * {@link IHttpClient}（protected 字段同包注入），经 {@code filterAsync} 入口 →
 * 半衰期判定 → 异步刷新撞 brownout（可控 future，无需真实网络端点）→ 载体线程
 * 不阻塞 → 降级语义 → {@code next.get()} 下游输出。出口点 = 客户端可观测响应/头状态。
 *
 * <p>载体缝（设计裁定③ N4 注记）：fake {@code executeBlocking} 不做同步内联——
 * 派发到专用单线程 daemon 载体执行器并展平内层 CompletionStage，显式声明载体缝，
 * 用载体探测任务证明载体线程未被刷新阻塞。
 *
 * <p>修复前红形态（§七）：载体线程被钉住（探测任务超时）、异常注入下任务体逃逸
 * （请求 future 永不完成）、刷新请求 timeout==0。
 */
public class TestSsoRefreshAsyncChain {

    private static final String SESSION_ID = "session-e2e-1";
    private static final String OLD_ACCESS = "old-access-token";
    private static final String NEW_ACCESS = "new-access-token";
    private static final String REFRESH_TOKEN = "refresh-token-1";
    private static final long CONFIGURED_TIMEOUT_MS = 10000L;

    private final List<ExecutorService> carrierExecutors = new ArrayList<>();

    @AfterEach
    public void shutdownCarriers() {
        for (ExecutorService executor : carrierExecutors) {
            executor.shutdownNow();
        }
        carrierExecutors.clear();
    }

    // ==================== 红①：brownout 载体钉线 ====================

    /**
     * IdP 刷新挂起（brownout）期间：载体线程必须即时释放（探测任务可在刷新挂起窗内
     * 执行），刷新终态后下游在非载体线程被驱动并输出可观测结果（x-access-token 头 +
     * 认证 cookie + next.get 执行）。链序钉：next.get 严格后于刷新终态。
     */
    @Test
    @Timeout(30)
    public void testBrownoutCarrierNotPinnedAndDownstreamDeliveredAfterRefreshTerminal() throws Exception {
        Fixture fixture = newFixture();
        fixture.client.program(new CompletableFuture<>()); // 刷新挂起（brownout）

        AtomicBoolean nextRan = new AtomicBoolean(false);
        AtomicReference<String> nextThread = new AtomicReference<>();
        CompletableFuture<IHttpResponse> refreshFuture = fixture.client.programmedFuture;

        Supplier<CompletionStage<Void>> next = () -> {
            // 链序钉（保持性回归钉）：下游严格后于刷新终态执行
            assertTrue(refreshFuture.isDone(),
                    "next.get must run strictly after the refresh reaches terminal state");
            nextRan.set(true);
            nextThread.set(Thread.currentThread().getName());
            return CompletableFuture.completedFuture(null);
        };

        CompletionStage<Void> request = fixture.filter.filterAsync(fixture.routeContext, next);

        // 载体探测任务排在过滤器载体任务之后：探测任务可执行 ⟺ 载体线程未被刷新钉住。
        // 修复前：refreshToken 经 syncGet 阻塞载体线程 → 探测任务永远排队 → 红。
        CompletableFuture<Object> probe = new CompletableFuture<>();
        fixture.carrierExecutor.submit(() -> {
            probe.complete(true);
            return true;
        });
        probe.get(5, TimeUnit.SECONDS);

        // 刷新终态由测试线程驱动（模拟 HTTP 客户端完成线程）
        refreshFuture.complete(accessTokenResponse(NEW_ACCESS, null));

        request.toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(nextRan.get(), "downstream must run after refresh terminal state");
        assertEquals(NEW_ACCESS,
                fixture.routeContext.responseHeaders.get(IHttpServerContext.HEADER_X_ACCESS_TOKEN),
                "refreshed access token must be delivered as x-access-token response header");
        assertTrue(fixture.routeContext.cookies.stream().anyMatch(c -> c.getValue().equals(NEW_ACCESS)),
                "refreshed access token must be delivered as auth cookie");
        assertNotNull(nextThread.get());
        assertNotEquals(fixture.carrierThreadName(), nextThread.get(),
                "downstream must be driven off the carrier thread after the async refresh completes");
        // 刷新请求确实以新 token 更新了共享 userContext（stale 数据不残留）：
        // 刷新链写 UserContextImpl 的 accessToken/refreshToken 字段
        assertEquals(NEW_ACCESS, fixture.userContext.getAccessToken());
        assertEquals(REFRESH_TOKEN, fixture.userContext.getRefreshToken());
    }

    // ==================== 红②：刷新异常逃逸挂死 ====================

    /**
     * 刷新异常（IdP 故障）必须收敛为降级：warn + 携既有 token 继续（stale-serve），
     * 请求 future 必达终态且下游继续执行、不更新头/cookie。
     * 修复前：异常逃逸 runOnContext 任务体 → future 永不完成（请求挂死）→ 红。
     */
    @Test
    @Timeout(30)
    public void testRefreshFailureDegradesStaleServeAndRequestStillCompletes() throws Exception {
        Fixture fixture = newFixture();
        fixture.client.failWith(new RuntimeException("idp brownout: refresh endpoint unreachable"));

        AtomicBoolean nextRan = new AtomicBoolean(false);
        Supplier<CompletionStage<Void>> next = () -> {
            nextRan.set(true);
            return CompletableFuture.completedFuture(null);
        };

        CompletionStage<Void> request = fixture.filter.filterAsync(fixture.routeContext, next);

        request.toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(nextRan.get(), "downstream must continue when refresh fails (stale-serve)");
        assertNull(fixture.routeContext.responseHeaders.get(IHttpServerContext.HEADER_X_ACCESS_TOKEN),
                "degraded refresh must not update the x-access-token header");
        assertTrue(fixture.routeContext.cookies.isEmpty(),
                "degraded refresh must not update the auth cookie");
        assertEquals(OLD_ACCESS, fixture.userContext.getAccessToken(),
                "stale-serve keeps the existing access token");
    }

    // ==================== 红③：刷新请求无显式超时 ====================

    /**
     * 刷新请求必须携带配置的显式超时（键 nop.auth.sso.refresh-token-timeout 默认
     * 10000ms）。修复前 newRefreshTokenRequest 未设任何超时 → req.timeout==0 → 红。
     */
    @Test
    public void testRefreshRequestCarriesConfiguredTimeout() {
        OAuthLoginServiceImpl service = newService(new ControllableHttpClient(), newConfig());
        HttpRequest req = service.newRefreshTokenRequest(REFRESH_TOKEN);

        assertEquals(CONFIGURED_TIMEOUT_MS, req.getTimeout(),
                "refresh request must carry the default explicit timeout (10000ms); "
                        + "pre-fix the request had no explicit timeout and relied on the client-level 30s default");
    }

    // ==================== 超时档：配置覆盖 + ≤0 回落 ====================

    /**
     * 配置覆盖真实生效（newRefreshTokenRequest 消费面）：SsoConfig 字段值决定刷新请求
     * 显式超时；≤0 = 不调用 req.timeout（保持 HttpRequest 字段默认 0，回落客户端部署级
     * 默认 readTimeout 30s）——LOCK-001 两档语义。容器级 @cfg: 接线验证见
     * TestSsoRefreshTokenTimeoutWiring。
     */
    @Test
    public void testRefreshRequestTimeoutConfigOverrideAndNonPositiveFallback() {
        SsoConfig config = newConfig();
        OAuthLoginServiceImpl service = newService(new ControllableHttpClient(), config);

        config.setRefreshTokenTimeout(7654);
        assertEquals(7654L, service.newRefreshTokenRequest(REFRESH_TOKEN).getTimeout(),
                "config override value must be consumed by newRefreshTokenRequest");

        config.setRefreshTokenTimeout(0);
        assertEquals(0L, service.newRefreshTokenRequest(REFRESH_TOKEN).getTimeout(),
                "timeout=0 must not set an explicit request timeout");

        config.setRefreshTokenTimeout(-1);
        assertEquals(0L, service.newRefreshTokenRequest(REFRESH_TOKEN).getTimeout(),
                "negative timeout must not set an explicit request timeout");
    }

    // ==================== 语义保持钉：error→null stale-serve ====================

    /**
     * IdP 返回 error 响应 → 刷新产出 null → 不更新头/cookie、携既有 token 继续
     * （既有 error→null stale-serve 语义逐字保持，认证可见结果零变化）。
     */
    @Test
    @Timeout(30)
    public void testRefreshErrorResponseKeepsStaleServeSemantics() throws Exception {
        Fixture fixture = newFixture();
        fixture.client.program(CompletableFuture.completedFuture(accessTokenResponse(null, "invalid_grant")));

        AtomicBoolean nextRan = new AtomicBoolean(false);
        Supplier<CompletionStage<Void>> next = () -> {
            nextRan.set(true);
            return CompletableFuture.completedFuture(null);
        };

        CompletionStage<Void> request = fixture.filter.filterAsync(fixture.routeContext, next);

        request.toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertTrue(nextRan.get(), "downstream must continue when IdP returns an error response");
        assertNull(fixture.routeContext.responseHeaders.get(IHttpServerContext.HEADER_X_ACCESS_TOKEN),
                "error response must not update the x-access-token header");
        assertTrue(fixture.routeContext.cookies.isEmpty(),
                "error response must not update the auth cookie");
        assertEquals(OLD_ACCESS, fixture.userContext.getAccessToken());
    }

    // ==================== fixtures ====================

    private Fixture newFixture() {
        ControllableHttpClient client = new ControllableHttpClient();
        StubUserContextCache cache = new StubUserContextCache();
        SsoConfig config = newConfig();
        E2EOAuthLoginService service = new E2EOAuthLoginService(client, cache, config);
        AuthHttpServerFilter filter = new AuthHttpServerFilter();
        filter.setConfig(new AuthFilterConfig());
        filter.setLoginService(service);
        filter.init();

        ExecutorService carrier = newSingleDaemonExecutor("sso-e2e-carrier");
        carrierExecutors.add(carrier);

        UserContextImpl userContext = newUserContext();
        cache.sessions.put(SESSION_ID, userContext);

        E2ERouteContext routeContext = new E2ERouteContext(carrier);
        routeContext.headers.put("authorization", "Bearer " + OLD_ACCESS);

        return new Fixture(filter, client, routeContext, carrier, userContext);
    }

    private static SsoConfig newConfig() {
        SsoConfig config = new SsoConfig();
        config.setAuthServerUrl("https://idp.example.com");
        config.setRealm("master");
        config.setClientId("test-client");
        config.setTokenUrl("/realms/{realm}/protocol/openid-connect/token");
        return config;
    }

    private OAuthLoginServiceImpl newService(IHttpClient client, SsoConfig config) {
        E2EOAuthLoginService service = new E2EOAuthLoginService(client, new StubUserContextCache(), config);
        return service;
    }

    private static UserContextImpl newUserContext() {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId("alice");
        userContext.setUserName("alice");
        userContext.setSessionId(SESSION_ID);
        userContext.setAccessToken(OLD_ACCESS);
        userContext.setRefreshToken(REFRESH_TOKEN);
        return userContext;
    }

    private static DefaultHttpResponse accessTokenResponse(String accessToken, String error) {
        AccessTokenResponse bean = new AccessTokenResponse();
        bean.setAccessToken(accessToken);
        bean.setRefreshToken(accessToken == null ? null : REFRESH_TOKEN);
        bean.setError(error);
        DefaultHttpResponse response = new DefaultHttpResponse();
        response.setHttpStatus(error == null ? 200 : 400);
        response.setBody(bean);
        return response;
    }

    private ExecutorService newSingleDaemonExecutor(String namePrefix) {
        ThreadFactory factory = new ThreadFactory() {
            private volatile int index = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, namePrefix + "-" + (index++));
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newSingleThreadExecutor(factory);
    }

    private static final class Fixture {
        final AuthHttpServerFilter filter;
        final ControllableHttpClient client;
        final E2ERouteContext routeContext;
        final ExecutorService carrierExecutor;
        final UserContextImpl userContext;
        final String carrierThreadName;
        Fixture(AuthHttpServerFilter filter, ControllableHttpClient client, E2ERouteContext routeContext,
                ExecutorService carrierExecutor, UserContextImpl userContext) {
            this.filter = filter;
            this.client = client;
            this.routeContext = routeContext;
            this.carrierExecutor = carrierExecutor;
            this.userContext = userContext;
            // 载体缝显式声明（N4）：记录载体线程名，供线程身份断言使用
            try {
                this.carrierThreadName = carrierExecutor.submit(() -> Thread.currentThread().getName()).get();
            } catch (Exception e) {
                throw new IllegalStateException("failed to capture carrier thread name", e);
            }
        }

        String carrierThreadName() {
            return carrierThreadName;
        }
    }

    /**
     * E2E 用真实 OAuthLoginServiceImpl：仅覆写 parseAuthToken（提供近半衰期 AuthToken
     * 以命中刷新支路），刷新链本体（refreshTokenAsync/fetchAsync/响应映射）保持真实实现。
     * userContextCache 为 protected 继承字段，经子类注入测试桩。
     */
    static class E2EOAuthLoginService extends OAuthLoginServiceImpl {
        E2EOAuthLoginService(IHttpClient httpClient, IUserContextCache userContextCache, SsoConfig config) {
            this.httpClient = httpClient;
            this.userContextCache = userContextCache;
            setConfig(config);
            init();
        }

        @Override
        public AuthToken parseAuthToken(String accessToken) {
            // 近半衰期 token：剩余寿命 60s < 半衰期 1800s → 过滤器刷新支路必然命中
            return new AuthToken(accessToken, "sub", "alice", SESSION_ID,
                    System.currentTimeMillis() + 60_000L, 3600, null);
        }
    }

    /**
     * 可控 fake IHttpClient（设计裁定③唯一许可测试缝）：可编程完成时刻/异常/状态码，
     * 录制刷新请求。未编程响应时显式失败（禁静默挂起）。
     */
    static class ControllableHttpClient implements IHttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        volatile CompletableFuture<IHttpResponse> programmedFuture;

        void program(CompletableFuture<IHttpResponse> future) {
            this.programmedFuture = future;
        }

        void failWith(Throwable ex) {
            CompletableFuture<IHttpResponse> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            program(future);
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelTokens) {
            requests.add(request);
            CompletableFuture<IHttpResponse> future = programmedFuture;
            if (future == null)
                return CompletableFuture.failedFuture(
                        new IllegalStateException("no refresh response programmed for test"));
            return future;
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("download is not used by the SSO refresh chain");
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("upload is not used by the SSO refresh chain");
        }
    }

    /** 内存会话缓存桩：仅支撑 getUserContextAsync 会话命中，其余面返回显式默认值。 */
    static class StubUserContextCache implements IUserContextCache {
        final Map<String, IUserContext> sessions = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<IUserContext> getUserContextAsync(String sessionId) {
            return CompletableFuture.completedFuture(sessions.get(sessionId));
        }

        @Override
        public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
            if (userContext.getSessionId() != null)
                sessions.put(userContext.getSessionId(), userContext);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> removeUserContextAsync(SessionInfo sessionInfo) {
            sessions.remove(sessionInfo.getSessionId());
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
            // 测试桩：失败计数面不在本链路覆盖范围
        }

        @Override
        public void resetLoginFailCountForUser(String userName) {
            // 测试桩：失败计数面不在本链路覆盖范围
        }

        @Override
        public void resetLoginFailCountForIp(String ip) {
            // 测试桩：失败计数面不在本链路覆盖范围
        }

        @Override
        public void setLoginFailCountForIp(String ip, int count) {
            // 测试桩：失败计数面不在本链路覆盖范围
        }

        @Override
        public String getVerifyCode(String key) {
            return null;
        }

        @Override
        public void setVerifyCode(String key, String code) {
            // 测试桩：验证码面不在本链路覆盖范围
        }
    }

    /**
     * 载体缝显式声明的 fake IHttpServerContext：executeBlocking 派发到专用单线程
     * daemon 载体执行器（非同步内联），并展平内层 CompletionStage（与
     * FutureHelper.futureCall 语义一致）。getContext 返回真实 BaseContext，
     * runOnContext 走真实任务队列路径。
     */
    static class E2ERouteContext implements IHttpServerContext {
        final Map<String, Object> headers = new HashMap<>();
        final Map<String, Object> responseHeaders = new HashMap<>();
        final List<HttpCookie> cookies = new ArrayList<>();
        final Map<String, String> requestCookies = new HashMap<>();
        final IContext context = new BaseContext();
        private final ExecutorService carrierExecutor;

        E2ERouteContext(ExecutorService carrierExecutor) {
            this.carrierExecutor = carrierExecutor;
        }

        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        @Override
        public int getRemotePort() {
            return 12345;
        }

        @Override
        public String getRequestPath() {
            return "/api/secure/data";
        }

        @Override
        public String getRequestUrl() {
            return "/api/secure/data";
        }

        @Override
        public String getQueryParam(String name) {
            return null;
        }

        @Override
        public Map<String, String> getQueryParams() {
            return Map.of();
        }

        @Override
        public Map<String, Object> getRequestHeaders() {
            return headers;
        }

        @Override
        public Object getRequestHeader(String headerName) {
            return headers.get(headerName.toLowerCase());
        }

        @Override
        public String getCookie(String name) {
            return requestCookies.get(name);
        }

        @Override
        public void addCookie(String sameSite, HttpCookie cookie) {
            cookies.add(cookie);
        }

        @Override
        public void removeCookie(String name) {
            requestCookies.remove(name);
        }

        @Override
        public void removeCookie(String name, String domain, String path) {
            requestCookies.remove(name);
        }

        @Override
        public void setResponseHeader(String headerName, Object value) {
            responseHeaders.put(headerName.toLowerCase(), value);
        }

        @Override
        public void sendRedirect(String url) {
            throw new UnsupportedOperationException("redirect is not exercised by the refresh chain");
        }

        @Override
        public void sendResponse(int httpStatus, String body) {
            responseHeaders.put("http-status", httpStatus);
        }

        @Override
        public void sendResponse(int httpStatus, InputStream body) {
            responseHeaders.put("http-status", httpStatus);
        }

        @Override
        public boolean isResponseSent() {
            return responseHeaders.containsKey("http-status");
        }

        @Override
        public String getAcceptableContentType() {
            return null;
        }

        @Override
        public String getResponseContentType() {
            return null;
        }

        @Override
        public void setResponseContentType(String contentType) {
            // 测试桩：响应内容类型不在本链路断言范围
        }

        @Override
        public void setResponseCharacterEncoding(String encoding) {
            // 测试桩：响应编码不在本链路断言范围
        }

        @Override
        public IAsyncBody getRequestBody() {
            return null;
        }

        @Override
        public CompletionStage<Object> executeBlocking(Callable<?> task) {
            CompletableFuture<Object> promise = new CompletableFuture<>();
            carrierExecutor.execute(() -> {
                try {
                    Object result = task.call();
                    if (result instanceof CompletionStage) {
                        ((CompletionStage<?>) result).whenComplete((v, e) -> {
                            if (e != null)
                                promise.completeExceptionally(e);
                            else
                                promise.complete(result);
                        });
                    } else {
                        promise.complete(result);
                    }
                } catch (Throwable t) {
                    promise.completeExceptionally(t);
                }
            });
            return promise;
        }

        @Override
        public IContext getContext() {
            return context;
        }

        @Override
        public void setContext(IContext context) {
            throw new UnsupportedOperationException("context is fixed per request in this fixture");
        }
    }
}
