package io.nop.gateway.http;

import io.nop.api.core.beans.ApiResponse;
import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.server.IHttpServerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpCookie;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 错误响应兜底状态码：缺失 httpStatus 的错误响应必须按 5xx 返回，
 * 不得以 200 返回错误 body（客户端会把错误当成功）。
 */
class TestGatewayHttpFilterErrorResponse {

    /** 最小 mock：只捕获 sendResponse 的状态码。 */
    private static final class MockServerContext implements IHttpServerContext {
        int sentStatus;
        String sentBody;

        @Override public String getHost() { return "localhost"; }
        @Override public String getRemoteAddr() { return "127.0.0.1"; }
        @Override public int getRemotePort() { return 12345; }
        @Override public String getRequestPath() { return "/test"; }
        @Override public String getRequestUrl() { return "http://localhost/test"; }
        @Override public String getQueryParam(String name) { return null; }
        @Override public Map<String, String> getQueryParams() { return Map.of(); }
        @Override public Map<String, Object> getRequestHeaders() { return Map.of(); }
        @Override public Object getRequestHeader(String headerName) { return null; }
        @Override public String getCookie(String name) { return null; }
        @Override public void addCookie(String sameSite, HttpCookie cookie) { }
        @Override public void removeCookie(String name) { }
        @Override public void removeCookie(String name, String domain, String path) { }
        @Override public void setResponseHeader(String headerName, Object value) { }
        @Override public void sendRedirect(String url) { }
        @Override public void sendResponse(int httpStatus, String body) { this.sentStatus = httpStatus; this.sentBody = body; }
        @Override public void sendResponse(int httpStatus, InputStream body) { this.sentStatus = httpStatus; }
        @Override public boolean isResponseSent() { return sentStatus != 0; }
        @Override public String getAcceptableContentType() { return null; }
        @Override public String getResponseContentType() { return null; }
        @Override public void setResponseContentType(String contentType) { }
        @Override public void setResponseCharacterEncoding(String encoding) { }
        @Override public io.nop.http.api.server.IAsyncBody getRequestBody() { return null; }
        @Override public CompletionStage<Object> executeBlocking(Callable<?> task) { return null; }
        @Override public io.nop.api.core.context.IContext getContext() { return null; }
        @Override public void setContext(io.nop.api.core.context.IContext context) { }
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void writeErrorResponse_missingHttpStatus_defaultsTo500() {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        MockServerContext context = new MockServerContext();

        ApiResponse<Object> response = new ApiResponse<>(); // httpStatus 缺省 0
        response.setStatus(-1);

        filter.writeErrorResponse(context, response);

        assertEquals(500, context.sentStatus,
                "错误响应缺失 httpStatus 时兜底必须是 500，不得以 200 返回错误 body");
        assertTrue(context.sentBody != null && !context.sentBody.isEmpty(), "错误 body 应正常写出");
    }

    @Test
    void writeErrorResponse_explicitStatus_preserved() {
        GatewayHttpFilter filter = new GatewayHttpFilter();
        MockServerContext context = new MockServerContext();

        ApiResponse<Object> response = new ApiResponse<>();
        response.setStatus(-1);
        response.setHttpStatus(429);

        filter.writeErrorResponse(context, response);

        assertEquals(429, context.sentStatus, "显式状态码必须保留");
    }

    @Test
    void writeNormalResponse_missingHttpStatus_still200() {
        // 正常响应路径契约不变：缺省 200
        GatewayHttpFilter filter = new GatewayHttpFilter();
        MockServerContext context = new MockServerContext();

        ApiResponse<Object> response = new ApiResponse<>(); // status=0 成功，httpStatus 缺省 0

        filter.writeNormalResponse(context, response);

        assertEquals(200, context.sentStatus);
    }
}
