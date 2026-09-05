package io.nop.http.api.server;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.time.CoreMetrics;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestContextHttpServerFilterFix {

    static class FakeContext implements IHttpServerContext {
        final Map<String, Object> headers = new HashMap<>();

        @Override
        public String getHost() {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return null;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getRequestPath() {
            return null;
        }

        @Override
        public String getRequestUrl() {
            return null;
        }

        @Override
        public String getQueryParam(String name) {
            return null;
        }

        @Override
        public Map<String, String> getQueryParams() {
            return null;
        }

        @Override
        public Map<String, Object> getRequestHeaders() {
            return headers;
        }

        @Override
        public Object getRequestHeader(String headerName) {
            return headers.get(headerName);
        }

        @Override
        public String getCookie(String name) {
            return null;
        }

        @Override
        public void addCookie(String sameSite, HttpCookie cookie) {
        }

        @Override
        public void removeCookie(String name) {
        }

        @Override
        public void removeCookie(String name, String domain, String path) {
        }

        @Override
        public void setResponseHeader(String headerName, Object value) {
        }

        @Override
        public void sendRedirect(String url) {
        }

        @Override
        public void sendResponse(int httpStatus, String body) {
        }

        @Override
        public void sendResponse(int httpStatus, InputStream body) {
        }

        @Override
        public boolean isResponseSent() {
            return false;
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
        }

        @Override
        public void setResponseCharacterEncoding(String encoding) {
        }

        @Override
        public IAsyncBody getRequestBody() {
            return null;
        }

        @Override
        public CompletionStage<Object> executeBlocking(Callable<?> task) {
            return null;
        }

        @Override
        public IContext getContext() {
            return null;
        }

        @Override
        public void setContext(IContext context) {
        }
    }

    @Test
    public void testTimeoutHeaderTreatedAsRemainingDuration() {
        ContextHttpServerFilter filter = new ContextHttpServerFilter();
        FakeContext httpContext = new FakeContext();
        // 生产方（ClientContextRpcServiceInterceptor）写入的是剩余毫秒数
        httpContext.headers.put(ApiConstants.HEADER_TIMEOUT, "60000");

        IContext ctx = ContextProvider.newContext();
        filter.initContext(ctx, httpContext);

        // 剩余 60s 的请求不应立即过期
        assertFalse(ctx.isCallExpired());
        assertTrue(ctx.getCallExpireTime() > CoreMetrics.currentTimeMillis());
    }
}
