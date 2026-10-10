package io.nop.gateway.http;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import io.nop.gateway.GatewayConfigs;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.rpc.core.utils.RpcHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayHttpFilter} 过滤器语义：无路由模型时透传下游、请求构建（JSON 解析 +
 * HTTP 元数据）、网关上下文构建、正常/错误响应写出（5xx 兜底、Content-Length 跳过）、
 * 以及完整过滤管道（路由命中 → RPC → 写回响应，不再调用下游 next）。
 */
public class TestGatewayHttpFilterSemantics {

    private static final String DEFAULT_MODEL_PATH = "/nop/main/app.gateway.xml";

    @AfterEach
    public void resetModelPath() {
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_MODEL_PATH,
                DEFAULT_MODEL_PATH);
    }

    // ==================== 透传语义 ====================

    /**
     * 未装配网关模型（handlerCache == null）时直接放行下游过滤器。
     */
    @Test
    public void testFilterAsyncWithoutModelPassesThrough() throws Exception {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();

        boolean[] nextCalled = {false};
        CompletionStage<Void> future = filter.filterAsync(ctx, () -> {
            nextCalled[0] = true;
            return CompletableFuture.completedFuture(null);
        });

        future.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(nextCalled[0], "downstream filter chain must be invoked when no model is configured");
        assertNull(ctx.responseBody, "gateway must not write any response in pass-through mode");
    }

    // ==================== 请求 / 上下文构建 ====================

    /**
     * buildRequest：解析 JSON body，携带请求头与 HTTP URL/method 元数据。
     */
    @Test
    public void testBuildRequestParsesJsonAndCarriesHttpMeta() throws Exception {
        // plan 2390 顺带修复：本测试直调 buildRequest 依赖 JSON provider——此前靠同
        // 类其他测试的 CoreInitialization 泄漏状态才通过（顺序依赖），显式初始化
        CoreInitialization.initialize();
        try {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();
        ctx.requestUrl = "/test/simple?x=1";
        ctx.method = "POST";
        ctx.headers.put("x-custom", "v1");

        ApiRequest<?> request = filter.buildRequest(ctx, "{\"a\":1,\"b\":\"two\"}");

        assertEquals(Map.of("a", 1, "b", "two"), request.getData(),
                "request body JSON must be parsed into the request data");
        assertEquals("/test/simple?x=1", RpcHelper.getHttpUrl(request),
                "request must carry the http url property");
        assertEquals("POST", RpcHelper.getHttpMethod(request),
                "request must carry the http method property");
        assertEquals("v1", request.getHeaders().get("x-custom"),
                "request must carry the inbound headers");
        } finally {
            CoreInitialization.destroy();
        }
    }

    /**
     * buildGatewayContext：路径 / 方法 / 查询参数 / 请求引用逐项落到网关上下文。
     */
    @Test
    public void testBuildGatewayContextCarriesRequestMeta() {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();
        ctx.requestPath = "/test/simple";
        ctx.method = "POST";
        ctx.queryParams.put("q", "1");

        ApiRequest<?> request = filter.buildRequest(ctx, "{}");
        io.nop.gateway.core.context.IGatewayContext gatewayCtx = filter.buildGatewayContext(request, ctx);

        assertEquals("/test/simple", gatewayCtx.getRequestPath());
        assertEquals("POST", gatewayCtx.getHttpMethod());
        assertEquals("1", gatewayCtx.getQueryParams().get("q"));
        assertSame(request, gatewayCtx.getRequest());
    }

    /**
     * 回归覆盖 wi8#1（plan 2306 项 21）：method 为 null 时 buildGatewayContext 不得
     * 因向 ConcurrentHashMap 写入 null value 抛 NPE；method 元数据被跳过而非伪造。
     */
    @Test
    public void testBuildGatewayContextToleratesNullMethod() {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();
        ctx.requestPath = "/test/simple";
        ctx.method = null;

        ApiRequest<?> request = filter.buildRequest(ctx, "{}");
        io.nop.gateway.core.context.IGatewayContext gatewayCtx = filter.buildGatewayContext(request, ctx);

        assertNull(gatewayCtx.getHttpMethod(), "缺失的 method 被跳过，不得伪造 GET 等语义");
        assertEquals("/test/simple", gatewayCtx.getRequestPath());
    }

    // ==================== 响应写出 ====================

    /**
     * 成功响应按 200 写回，body 序列化完整 ApiResponse（非 wrapper 模式）。
     */
    @Test
    public void testWriteNormalResponseUsesOkStatus() throws Exception {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();

        ApiResponse<Object> response = new ApiResponse<>();
        response.setStatus(0);
        response.setData(Map.of("mocked", true));

        filter.write(ctx, response, new io.nop.gateway.core.context.GatewayContextImpl())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(200, ctx.responseStatus, "ok response must be written with HTTP 200");
        assertNotNull(ctx.responseBody);
        assertTrue(ctx.responseBody.contains("\"mocked\":true"),
                "body must contain the response data, got: " + ctx.responseBody);
    }

    /**
     * 错误响应缺失 httpStatus 时必须兜底 500（避免客户端把错误当成功缓存/重试）。
     */
    @Test
    public void testWriteErrorResponseFallsBackTo500() throws Exception {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();

        ApiResponse<Object> response = new ApiResponse<>();
        response.setStatus(-1);
        response.setCode("nop.err.test");
        response.setHttpStatus(0);

        filter.write(ctx, response, new io.nop.gateway.core.context.GatewayContextImpl())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(500, ctx.responseStatus,
                "error response without explicit httpStatus must fall back to 500");
        assertEquals("application/json", ctx.contentType,
                "error body must be written as json");
    }

    /**
     * 错误响应显式携带 httpStatus 时按显式值写回。
     */
    @Test
    public void testWriteErrorResponseRespectsExplicitStatus() throws Exception {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();

        ApiResponse<Object> response = new ApiResponse<>();
        response.setStatus(-1);
        response.setHttpStatus(404);

        filter.write(ctx, response, new io.nop.gateway.core.context.GatewayContextImpl())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(404, ctx.responseStatus, "explicit httpStatus must be preserved");
    }

    /**
     * 响应头透传跳过 Content-Length（长度由写出端重算）。
     */
    @Test
    public void testWriteHeadersSkipsContentLength() throws Exception {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        FakeHttpContext ctx = new FakeHttpContext();

        filter.writeHeaders(ctx, Map.of("Content-Length", 99, "X-Custom", "v"));

        assertNull(ctx.responseHeaders.get("content-length"),
                "Content-Length must not be forwarded");
        assertEquals("v", ctx.responseHeaders.get("x-custom"),
                "custom headers must be forwarded");
    }

    // ==================== 完整过滤管道 ====================

    /**
     * 配置测试网关模型后：请求经路由匹配 → RPC 调用 → 成功响应写回，
     * 且不再调用下游过滤器（网关短路语义）。
     */
    @Test
    public void testFilterAsyncRoutesRequestAndShortCircuits() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_MODEL_PATH,
                "/nop/test/test.gateway.xml");
        CoreInitialization.initialize();
        try {
            GatewayHttpFilter filter = new GatewayHttpFilter();
            filter.setRpcServiceInvoker(new MockRpcServiceInvoker());
            filter.setHttpClient(new MockHttpClient());
            filter.setRecordMappingManager(null);
            filter.init();

            FakeHttpContext ctx = new FakeHttpContext();
            ctx.requestPath = "/test/simple";
            ctx.requestUrl = "/test/simple";
            ctx.method = "POST";
            ctx.requestBody = "{\"input\":\"test\"}";

            boolean[] nextCalled = {false};
            filter.filterAsync(ctx, () -> {
                nextCalled[0] = true;
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertTrue(!nextCalled[0], "matched gateway route must short-circuit the filter chain");
            assertEquals(200, ctx.responseStatus, "gateway must write the RPC result with HTTP 200");
            assertTrue(ctx.responseBody.contains("simpleMethod"),
                    "response must reflect the routed rpc call, got: " + ctx.responseBody);
        } finally {
            CoreInitialization.destroy();
        }
    }

    /**
     * 路由模型存在但路径不匹配任何路由时放行下游（body 不消费）。
     */
    @Test
    public void testFilterAsyncUnmatchedPathPassesThrough() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_MODEL_PATH,
                "/nop/test/test.gateway.xml");
        CoreInitialization.initialize();
        try {
            GatewayHttpFilter filter = new GatewayHttpFilter();
            filter.setRpcServiceInvoker(new MockRpcServiceInvoker());
            filter.setHttpClient(new MockHttpClient());
            filter.setRecordMappingManager(null);
            filter.init();

            FakeHttpContext ctx = new FakeHttpContext();
            ctx.requestPath = "/no/such/route";
            ctx.requestUrl = "/no/such/route";

            boolean[] nextCalled = {false};
            filter.filterAsync(ctx, () -> {
                nextCalled[0] = true;
                return CompletableFuture.completedFuture(null);
            }).toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertTrue(nextCalled[0], "unmatched path must fall through to downstream filters");
            assertNull(ctx.responseBody, "pass-through must not write a response");
        } finally {
            CoreInitialization.destroy();
        }
    }

    // ==================== P1-check-gw-49（plan 2390）判别 ====================

    /**
     * 非 JSON content-type 的非空 body 不再触发严格 JSON 解析异常——路由命中、
     * 正常走 RPC 并写回 200（修复前 JSON.parse(multipart文本) 在异步段抛出，
     * filterAsync future 异常完成）。
     */
    @Test
    public void testNonJsonBodyNotParsed() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_MODEL_PATH,
                "/nop/test/test.gateway.xml");
        CoreInitialization.initialize();
        try {
            GatewayHttpFilter filter = newGatewayFilter();
            FakeHttpContext ctx = new FakeHttpContext();
            ctx.requestPath = "/test/simple";
            ctx.requestUrl = "/test/simple";
            ctx.method = "POST";
            ctx.requestBody = "--boundary\ncontent-disposition: form-data; name=\"f\"\n\nbinary-ish";
            ctx.headers.put("content-type", "multipart/form-data; boundary=boundary");

            filter.filterAsync(ctx, () -> CompletableFuture.completedFuture(null))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertEquals(200, ctx.responseStatus,
                    "multipart body must not blow up the filter (P1-check-gw-49)");
        } finally {
            CoreInitialization.destroy();
        }
    }

    /**
     * 无路由命中时 body 零消费——预匹配守卫在 resumeRequest/getRequestBody 之前放行。
     * （修复前 body 必被读取：bodyReadCount>0。既有 testFilterAsyncUnmatchedPathPassesThrough
     * 因 fake 默认 body="{}" 解析成功而不判别，本测试用读计数直接钉定消费行为。）
     */
    @Test
    public void testUnmatchedPathBodyUntouched() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_MODEL_PATH,
                "/nop/test/test.gateway.xml");
        CoreInitialization.initialize();
        try {
            GatewayHttpFilter filter = newGatewayFilter();
            FakeHttpContext ctx = new FakeHttpContext();
            ctx.requestPath = "/no/such/route";
            ctx.requestUrl = "/no/such/route";
            ctx.requestBody = "{\"input\":\"must-not-be-read\"}";

            filter.filterAsync(ctx, () -> CompletableFuture.completedFuture(null))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertEquals(0, ctx.bodyReadCount,
                    "unmatched path must not consume the request body (P1-check-gw-49)");
        } finally {
            CoreInitialization.destroy();
        }
    }

    /**
     * JSON content-type 的非法 body 保持 fail-fast，但异步段异常由 exceptionally
     * 兜底转 5xx 错误响应（修复前 future 异常完成、无响应写出）。
     */
    @Test
    public void testMalformedJsonBodyFailsFastWith5xx() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_MODEL_PATH,
                "/nop/test/test.gateway.xml");
        CoreInitialization.initialize();
        try {
            GatewayHttpFilter filter = newGatewayFilter();
            FakeHttpContext ctx = new FakeHttpContext();
            ctx.requestPath = "/test/simple";
            ctx.requestUrl = "/test/simple";
            ctx.method = "POST";
            ctx.requestBody = "{not-valid-json";
            ctx.headers.put("content-type", "application/json");

            filter.filterAsync(ctx, () -> CompletableFuture.completedFuture(null))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertTrue(ctx.responseStatus >= 500,
                    "malformed JSON-declared body must fail fast as a 5xx error response");
        } finally {
            CoreInitialization.destroy();
        }
    }

    /**
     * 响应已提交（streaming 中途失败穿透）时 exceptionally 只记日志不二次写出——
     * P2 审查守卫：对已提交响应再 sendResponse 会抛 IllegalStateException。
     */
    @Test
    public void testAsyncErrorAfterResponseSentDoesNotDoubleWrite() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_MODEL_PATH,
                "/nop/test/test.gateway.xml");
        CoreInitialization.initialize();
        try {
            // write() 提交响应后异步失败——模拟 streaming 中途错误穿透到外层链
            GatewayHttpFilter filter = new GatewayHttpFilter() {
                @Override
                protected CompletionStage<Void> write(IHttpServerContext context,
                                                      ApiResponse<?> response,
                                                      io.nop.gateway.core.context.IGatewayContext gatewayCtx) {
                    context.sendResponse(200, "partial-stream");
                    return CompletableFuture.failedFuture(new IllegalStateException("stream mid-flight boom"));
                }
            };
            filter.setRpcServiceInvoker(new MockRpcServiceInvoker());
            filter.setHttpClient(new MockHttpClient());
            filter.setRecordMappingManager(null);
            filter.init();

            FakeHttpContext ctx = new FakeHttpContext();
            ctx.requestPath = "/test/simple";
            ctx.requestUrl = "/test/simple";
            ctx.method = "POST";
            ctx.requestBody = "{\"input\":\"test\"}";

            filter.filterAsync(ctx, () -> CompletableFuture.completedFuture(null))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertEquals(200, ctx.responseStatus, "committed response status untouched");
            assertEquals("partial-stream", ctx.responseBody,
                    "no second write after the response was committed (isResponseSent guard)");
        } finally {
            CoreInitialization.destroy();
        }
    }

    private GatewayHttpFilter newGatewayFilter() {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        filter.setRpcServiceInvoker(new MockRpcServiceInvoker());
        filter.setHttpClient(new MockHttpClient());
        filter.setRecordMappingManager(null);
        filter.init();
        return filter;
    }

    // ==================== fixtures ====================

    static class MockRpcServiceInvoker implements IRpcServiceInvoker {
        @Override
        public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                           ApiRequest<?> request, ICancelToken cancelToken) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setStatus(0);
            response.setData(Map.of("mocked", true, "service", serviceName, "method", serviceMethod));
            return CompletableFuture.completedFuture(response);
        }
    }

    static class MockHttpClient implements IHttpClient {
        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException("not used by route invoke");
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request,
                                                            io.nop.http.api.client.IHttpOutputFile targetFile,
                                                            io.nop.http.api.client.DownloadOptions options,
                                                            ICancelToken cancelToken) {
            throw new UnsupportedOperationException("not used by route invoke");
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request,
                                                          io.nop.http.api.client.IHttpInputFile inputFile,
                                                          io.nop.http.api.client.UploadOptions options,
                                                          ICancelToken cancelToken) {
            throw new UnsupportedOperationException("not used by route invoke");
        }
    }

    static class FakeHttpContext implements IHttpServerContext {
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, String> queryParams = new LinkedHashMap<>();
        String requestPath = "/api/data";
        String requestUrl = "/api/data";
        String method = "POST";
        String requestBody;
        int responseStatus;
        String responseBody;
        String contentType;
        final Map<String, Object> responseHeaders = new LinkedHashMap<>();

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
        public String getMethod() {
            return method;
        }

        @Override
        public String getRequestUrl() {
            return requestUrl;
        }

        @Override
        public String getQueryParam(String name) {
            return queryParams.get(name);
        }

        @Override
        public Map<String, String> getQueryParams() {
            return queryParams;
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
            return null;
        }

        @Override
        public void addCookie(String sameSite, java.net.HttpCookie cookie) {
        }

        @Override
        public void removeCookie(String name) {
        }

        @Override
        public void removeCookie(String name, String domain, String path) {
        }

        @Override
        public void setResponseHeader(String headerName, Object value) {
            responseHeaders.put(headerName.toLowerCase(), value);
        }

        @Override
        public void sendRedirect(String url) {
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
            return responseStatus != 0;
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

        int bodyReadCount = 0;

        @Override
        public IAsyncBody getRequestBody() {
            bodyReadCount++;
            String text = requestBody == null ? "{}" : requestBody;
            return () -> CompletableFuture.completedFuture(text);
        }

        @Override
        public CompletionStage<Object> executeBlocking(java.util.concurrent.Callable<?> task) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
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
}
