package io.nop.auth.core.filter;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.config.AppConfig;
import io.nop.auth.core.AuthCoreConfigs;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.AuthCoreErrors;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.api.core.auth.IUserContext;
import io.nop.http.api.server.IHttpServerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthHttpServerFilter} 过滤语义（不启动 HTTP 服务，mock IHttpServerContext）：
 * 认证凭证解析（Bearer/header/cookie 优先级、__Host- 前缀）、无效凭证 fail-closed、
 * Ajax 判定、token 半衰期刷新、开放重定向防护、登出路径清 cookie、401 响应形态
 * （REST vs GraphQL）、登录跳转 state 参数。
 */
public class TestAuthHttpServerFilterSemantics {

    private final TestLoginService loginService = new TestLoginService();

    @BeforeEach
    public void ensureSecureDefault() {
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE, true);
    }

    @AfterEach
    public void resetSecure() {
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE, true);
    }

    // ==================== 凭证解析 ====================

    /**
     * Authorization 头中的 Bearer 前缀被剥离，返回裸 token。
     */
    @Test
    public void testGetAuthTokenStripsBearerPrefix() {
        AuthHttpServerFilter filter = newFilter();
        Map<String, Object> headers = new HashMap<>();
        headers.put("authorization", "Bearer tok-123");

        assertEquals("tok-123", filter.getAuthToken(context(headers)),
                "Bearer prefix must be stripped from the authorization header");
    }

    /**
     * Authorization 缺失时回退到 x-access-token 头。
     */
    @Test
    public void testGetAuthTokenFallsBackToXAccessTokenHeader() {
        AuthHttpServerFilter filter = newFilter();
        Map<String, Object> headers = new HashMap<>();
        headers.put("x-access-token", "tok-x");

        assertEquals("tok-x", filter.getAuthToken(context(headers)),
                "x-access-token header must be used when authorization is missing");
    }

    /**
     * Authorization 优先于 cookie（防凭证混淆）。
     */
    @Test
    public void testGetAuthTokenPrefersHeaderOverCookie() {
        AuthHttpServerFilter filter = newFilter();
        Map<String, Object> headers = new HashMap<>();
        headers.put("authorization", "tok-header");

        FakeHttpContext ctx = context(headers);
        ctx.requestCookies.put("__Host-nop-token", "tok-cookie");

        assertEquals("tok-header", filter.getAuthToken(ctx),
                "header token must win over cookie token");
    }

    /**
     * Secure 模式下从 __Host- 前缀 cookie 读取认证凭证。
     */
    @Test
    public void testGetAuthTokenFromHostPrefixedCookie() {
        AuthHttpServerFilter filter = newFilter();
        FakeHttpContext ctx = context(new HashMap<>());
        ctx.requestCookies.put("__Host-nop-token", "tok-cookie");

        assertEquals("tok-cookie", filter.getAuthTokenFromCookie(ctx),
                "cookie lookup must use the __Host- prefixed name when secure is on");
    }

    /**
     * 无任何凭证时返回 null（未携带登录凭证）。
     */
    @Test
    public void testGetAuthTokenMissingReturnsNull() {
        AuthHttpServerFilter filter = newFilter();
        assertNull(filter.getAuthToken(context(new HashMap<>())));
    }

    /**
     * 无效凭证（解析抛异常）必须按未登录处理返回 null，而不是向调用方抛异常
     * （fail-closed 且不泄露解析错误）。
     */
    @Test
    public void testParseAuthTokenInvalidTokenReturnsNull() {
        AuthHttpServerFilter filter = newFilter();
        filter.setLoginService(new ThrowingLoginService());

        assertNull(filter.parseAuthToken(context(Map.of("authorization", "bad-token"))),
                "invalid token must degrade to no-auth, not throw");
    }

    // ==================== Ajax 判定 / 刷新判定 ====================

    /**
     * Ajax 判定：x-requested-with 任意非空值或 Accept: application/json 视为 Ajax。
     */
    @Test
    public void testIsAjaxRequestDetection() {
        AuthHttpServerFilter filter = newFilter();

        assertTrue(filter.isAjaxRequest(context(Map.of("x-requested-with", "XMLHttpRequest"))),
                "x-requested-with must mark the request as ajax");
        assertTrue(filter.isAjaxRequest(context(Map.of("accept", "application/json"))),
                "JSON accept header must mark the request as ajax");
        assertFalse(filter.isAjaxRequest(context(Map.of("accept", "text/html"))),
                "plain browser navigation is not ajax");
    }

    /**
     * token 剩余寿命低于半衰期时需要刷新；新鲜 token 或关闭 autoRefresh 时不刷新。
     * （auto-refresh-token 配置默认 true 经 @InjectValue 在 IoC 下注入；纯单元测试需显式 setter。）
     */
    @Test
    public void testIsNeedRefreshHalfLife() {
        AuthHttpServerFilter filter = newFilter();
        filter.setAutoRefreshToken(true);
        long now = System.currentTimeMillis();

        AuthToken almostExpired = new AuthToken("t", "sub", "u", "s1",
                now + 1000L, 3600, null);
        assertTrue(filter.isNeedRefresh(almostExpired),
                "token with remaining life < half-life must be refreshed");

        AuthToken fresh = new AuthToken("t", "sub", "u", "s1",
                now + 3500L * 1000, 3600, null);
        assertFalse(filter.isNeedRefresh(fresh), "fresh token must not be refreshed");

        filter.setAutoRefreshToken(false);
        assertFalse(filter.isNeedRefresh(almostExpired),
                "auto-refresh off must never refresh");
    }

    // ==================== 重定向防护 ====================

    /**
     * 开放重定向防护：相对路径放行；协议相对（//host）、反斜杠相对（/\host）、
     * 含反斜杠/控制字符的 URI 一律拒绝；绝对 URL 只按白名单前缀放行。
     */
    @Test
    public void testIsAllowedRedirectUriGuards() {
        AuthHttpServerFilter filter = newFilter();
        TestAuthFilterConfig config = (TestAuthFilterConfig) newFilterConfig();
        config.setAllowedRedirectPrefixes(java.util.List.of("https://good.example.com/"));
        filter.setConfig(config);

        assertTrue(filter.isAllowedRedirectUri("/app/page"),
                "plain relative path must be allowed");
        assertFalse(filter.isAllowedRedirectUri("//evil.example.com/x"),
                "protocol-relative URI must be rejected");
        assertFalse(filter.isAllowedRedirectUri("/\\evil.example.com"),
                "backslash-relative URI must be rejected");
        assertFalse(filter.isAllowedRedirectUri("/path\r\nSet-Cookie: x"),
                "control characters must be rejected");
        assertFalse(filter.isAllowedRedirectUri("https://evil.example.com/x"),
                "absolute URI outside the whitelist must be rejected");
        assertTrue(filter.isAllowedRedirectUri("https://good.example.com/x"),
                "whitelisted absolute URI must be allowed");
        assertFalse(filter.isAllowedRedirectUri(""),
                "empty URI must be rejected");
    }

    // ==================== 登出路径 ====================

    /**
     * 登出路径命中时移除认证 cookie（__Host- 前缀名）并放行。
     */
    @Test
    public void testCheckLogoutUrlRemovesAuthCookie() {
        AuthHttpServerFilter filter = newFilter();
        TestAuthFilterConfig config = (TestAuthFilterConfig) newFilterConfig();
        config.setLogoutUrl("/logout");
        filter.setConfig(config);

        FakeHttpContext ctx = context(new HashMap<>());
        ctx.requestPath = "/logout";

        assertTrue(filter.checkLogoutUrl(ctx), "configured logout path must be detected");
        assertEquals("__Host-nop-token", ctx.removedCookies.get(0),
                "logout must remove the __Host- prefixed auth cookie");
    }

    /**
     * 非登出路径不触发 cookie 清理。
     */
    @Test
    public void testCheckLogoutUrlOtherPathIsNoOp() {
        AuthHttpServerFilter filter = newFilter();
        TestAuthFilterConfig config = (TestAuthFilterConfig) newFilterConfig();
        config.setLogoutUrl("/logout");
        filter.setConfig(config);

        FakeHttpContext ctx = context(new HashMap<>());
        ctx.requestPath = "/api/data";

        assertFalse(filter.checkLogoutUrl(ctx), "non-logout path must not match");
        assertTrue(ctx.removedCookies.isEmpty(), "no cookie may be removed on normal paths");
    }

    // ==================== 401 响应形态 ====================

    /**
     * REST 请求未登录返回 401 + ApiResponse JSON（code=ERR_AUTH_NOT_AUTHORIZED）。
     */
    @Test
    public void testResponseNotLoginWritesApiResponseJson() throws Exception {
        AuthHttpServerFilter filter = newFilter();
        FakeHttpContext ctx = context(new HashMap<>());
        ctx.requestUrl = "/api/data";
        ctx.headers.put("accept", "application/json");

        filter.responseNotLogin(ctx, false).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(401, ctx.responseStatus, "not-logged-in must respond 401");
        assertTrue(ctx.responseBody.contains(AuthCoreErrors.ERR_AUTH_NOT_AUTHORIZED.getErrorCode()),
                "body must carry the not-authorized error code, got: " + ctx.responseBody);
        assertEquals("application/json", ctx.responseHeaders.get("content-type"),
                "json response must declare json content type");
    }

    /**
     * GraphQL 端点未登录返回 GraphQLResponseBean 形态（errorCode 字段）。
     */
    @Test
    public void testResponseNotLoginWritesGraphQLShapeOnGraphqlEndpoint() throws Exception {
        AuthHttpServerFilter filter = newFilter();
        FakeHttpContext ctx = context(new HashMap<>());
        ctx.requestUrl = "/graphql";
        ctx.headers.put("accept", "application/json");

        filter.responseNotLogin(ctx, false).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(401, ctx.responseStatus);
        // GraphQLResponseBean 序列化形态：错误码进 extensions（nop-error-code/nop-status），
        // 与 REST 的 ApiResponse 形态（顶层 code/status 字段）相区分
        assertTrue(ctx.responseBody.contains("\"extensions\"")
                        && ctx.responseBody.contains("nop-error-code"),
                "graphql endpoint must receive GraphQLResponseBean shape, got: " + ctx.responseBody);
        assertFalse(ctx.responseBody.contains("\"code\":\"" + AuthCoreErrors.ERR_AUTH_NOT_AUTHORIZED.getErrorCode() + "\""),
                "graphql shape must not use the REST top-level code field");
    }

    /**
     * 非 Ajax 浏览器请求（配置了 loginUrl）重定向到带 state 参数的登录页。
     */
    @Test
    public void testResponseNotLoginRedirectsBrowserToLoginUrlWithState() throws Exception {
        AuthHttpServerFilter filter = newFilter();
        TestAuthFilterConfig config = (TestAuthFilterConfig) newFilterConfig();
        config.setLoginUrl("/login?redirect={backUrl}");
        filter.setConfig(config);

        FakeHttpContext ctx = context(new HashMap<>());
        ctx.requestUrl = "/app/page";

        filter.responseNotLogin(ctx, false).toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(ctx.redirectUrl != null && ctx.redirectUrl.startsWith("/login"),
                "browser requests must be redirected to the login page, got: " + ctx.redirectUrl);
        assertTrue(ctx.redirectUrl.contains("state="), "login redirect must carry a state parameter");
        assertEquals(1, ctx.cookies.size(), "state must also be planted as a cookie");
        assertEquals("Strict", ctx.sameSite.get(ctx.cookies.get(0).getName()),
                "state cookie must be SameSite=Strict");
    }

    // ==================== sys 用户上下文 ====================

    /**
     * servicePublic 服务路径合成的 SYS 上下文带 timezone/locale 请求头透传。
     */
    @Test
    public void testNewSysUserContextCarriesTimezoneAndLocaleHeaders() {
        AuthHttpServerFilter filter = newFilter();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ApiConstants.HEADER_TIMEZONE, "Asia/Shanghai");
        headers.put(ApiConstants.HEADER_LOCALE, "zh-CN");

        IUserContext sys = filter.newSysUserContext(context(headers));

        assertEquals("Asia/Shanghai", sys.getTimeZone(),
                "sys context must transparently carry the timezone header");
        assertEquals("zh-CN", sys.getLocale(),
                "sys context must transparently carry the locale header");
        assertEquals(AuthCoreConstants.USER_ID_SYS, sys.getUserId());
    }

    // ==================== fixtures ====================

    private AuthHttpServerFilter newFilter() {
        AuthHttpServerFilter filter = new AuthHttpServerFilter();
        filter.setConfig(newFilterConfig());
        filter.setLoginService(loginService);
        filter.init();
        return filter;
    }

    private static AuthFilterConfig newFilterConfig() {
        // Secure 默认开启 → __Host- 前缀；子类仅用于暴露 allowedRedirectPrefixes setter 链
        return new TestAuthFilterConfig();
    }

    static class TestAuthFilterConfig extends AuthFilterConfig {
    }

    static class ThrowingLoginService extends TestLoginService {
        @Override
        public AuthToken parseAuthToken(String accessToken) {
            throw new IllegalStateException("corrupt token");
        }
    }

    static class TestLoginService implements ILoginService {
        @Override
        public CompletionStage<IUserContext> loginAsync(io.nop.auth.api.messages.LoginRequest request,
                                                        Map<String, Object> headers) {
            return null;
        }

        @Override
        public CompletionStage<Void> logoutAsync(int logoutType,
                                                 io.nop.auth.api.messages.LogoutRequest request) {
            return null;
        }

        @Override
        public CompletionStage<Void> killLoginAsync(String userName) {
            return null;
        }

        @Override
        public CompletionStage<Void> flushUserContextAsync(IUserContext userContext) {
            return null;
        }

        @Override
        public CompletionStage<IUserContext> getUserContextAsync(AuthToken accessToken,
                                                                 Map<String, Object> headers) {
            return null;
        }

        @Override
        public CompletionStage<IUserContext> getLoginUserContextAsync(String userName) {
            return null;
        }

        @Override
        public io.nop.auth.api.messages.LoginUserInfo getUserInfo(IUserContext userContext) {
            return null;
        }

        @Override
        public String generateVerifyCode(String verifySecret) {
            return null;
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

    static class FakeHttpContext implements IHttpServerContext {
        Map<String, Object> headers = new HashMap<>();
        final Map<String, String> requestCookies = new LinkedHashMap<>();
        final java.util.List<String> removedCookies = new java.util.ArrayList<>();
        final Map<String, Object> responseHeaders = new LinkedHashMap<>();
        String requestPath = "/api/data";
        String requestUrl = "/api/data";
        int responseStatus;
        String responseBody;
        String redirectUrl;
        final java.util.List<HttpCookie> cookies = new java.util.ArrayList<>();
        final Map<String, String> sameSite = new HashMap<>();
        String contentType;

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
            return requestPath;
        }

        @Override
        public String getRequestUrl() {
            return requestUrl;
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
            sameSite(sameSite, cookie);
        }

        private void sameSite(String value, HttpCookie cookie) {
            sameSite.put(cookie.getName(), value);
        }

        @Override
        public void removeCookie(String name) {
            removedCookies.add(name);
        }

        @Override
        public void removeCookie(String name, String domain, String path) {
            removedCookies.add(name);
        }

        @Override
        public void setResponseHeader(String headerName, Object value) {
            responseHeaders.put(headerName.toLowerCase(), value);
        }

        @Override
        public void sendRedirect(String url) {
            this.redirectUrl = url;
        }

        @Override
        public void sendResponse(int httpStatus, String body) {
            this.responseStatus = httpStatus;
            this.responseBody = body;
        }

        @Override
        public void sendResponse(int httpStatus, InputStream body) {
            this.responseStatus = httpStatus;
            this.responseBody = "<stream>";
        }

        @Override
        public boolean isResponseSent() {
            return responseStatus != 0 || redirectUrl != null;
        }

        @Override
        public String getAcceptableContentType() {
            return null;
        }

        @Override
        public String getResponseContentType() {
            return contentType;
        }

        @Override
        public void setResponseContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public void setResponseCharacterEncoding(String encoding) {
        }

        @Override
        public io.nop.http.api.server.IAsyncBody getRequestBody() {
            return null;
        }

        @Override
        public CompletionStage<Object> executeBlocking(java.util.concurrent.Callable<?> task) {
            try {
                return java.util.concurrent.CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public io.nop.api.core.context.IContext getContext() {
            return null;
        }

        @Override
        public void setContext(io.nop.api.core.context.IContext context) {
        }
    }

    private static FakeHttpContext context(Map<String, Object> headers) {
        FakeHttpContext ctx = new FakeHttpContext();
        Map<String, Object> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        ctx.headers = lower;
        return ctx;
    }
}
