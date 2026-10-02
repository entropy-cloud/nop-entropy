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
    public void testBuildRequestParsesJsonAndCarriesHttpMeta() {
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

        @Override
        public IAsyncBody getRequestBody() {
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
