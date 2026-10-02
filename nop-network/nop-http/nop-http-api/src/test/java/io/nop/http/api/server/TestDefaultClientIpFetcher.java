package io.nop.http.api.server;

import io.nop.api.core.context.IContext;
import org.junit.jupiter.api.Test;

import java.net.HttpCookie;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 DefaultClientIpFetcher 的真实 IP 解析语义：
 * X-Forwarded-For 首个有效 IP 优先、其次 Forwarded(RFC7239) 的 for= 值（含引号）、
 * 均无效时回退 remoteAddr。
 */
public class TestDefaultClientIpFetcher {

    static class FakeContext implements IHttpServerContext {
        final Map<String, Object> headers = new HashMap<>();
        String remoteAddr = "192.168.1.10";
        int remotePort = 8080;

        FakeContext withHeader(String name, String value) {
            headers.put(name, value);
            return this;
        }

        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public String getRemoteAddr() {
            return remoteAddr;
        }

        @Override
        public int getRemotePort() {
            return remotePort;
        }

        @Override
        public String getRequestPath() {
            return "/";
        }

        @Override
        public String getRequestUrl() {
            return "/";
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

    private final DefaultClientIpFetcher fetcher = new DefaultClientIpFetcher();

    @Test
    public void testXForwardedForFirstValidIpWins() {
        FakeContext context = new FakeContext()
                .withHeader("X-Forwarded-For", "203.0.113.7 , 10.0.0.2");
        assertEquals("203.0.113.7", fetcher.getClientRealIp(context),
                "first valid X-Forwarded-For entry must win");
    }

    @Test
    public void testUnknownXffEntrySkippedToNextValid() {
        FakeContext context = new FakeContext()
                .withHeader("X-Forwarded-For", "unknown, 203.0.113.9");
        assertEquals("203.0.113.9", fetcher.getClientRealIp(context),
                "'unknown' entries must be skipped");
    }

    @Test
    public void testForwardedHeaderQuotedForParsed() {
        FakeContext context = new FakeContext()
                .withHeader("Forwarded", "for=\"198.51.100.60\";proto=http");
        assertEquals("198.51.100.60", fetcher.getClientRealIp(context),
                "RFC7239 quoted for value must be parsed and dequoted");
    }

    @Test
    public void testNoProxyHeadersFallsBackToRemoteAddr() {
        assertEquals("192.168.1.10", fetcher.getClientRealIp(new FakeContext()),
                "without proxy headers the remote addr is the client ip");
    }

    @Test
    public void testClientRealAddrAppendsPort() {
        FakeContext context = new FakeContext();
        context.remotePort = 9090;
        assertEquals("192.168.1.10:9090", fetcher.getClientRealAddr(context),
                "real addr must append the remote port");
    }
}
