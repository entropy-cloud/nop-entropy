package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.gateway.GatewayErrors;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InvokeProcessorRetryTest {

    private long invokeParseRetryAfter(String retryAfter, int retriesLeft) throws Exception {
        InvokeProcessor processor = new InvokeProcessor(null, null);
        Method m = InvokeProcessor.class.getDeclaredMethod("parseRetryAfter", String.class, int.class);
        m.setAccessible(true);
        return (long) m.invoke(processor, retryAfter, retriesLeft);
    }

    @Test
    void parseRetryAfter_seconds_returnsCorrectMs() throws Exception {
        assertEquals(5000L, invokeParseRetryAfter("5", 3));
        assertEquals(0L, invokeParseRetryAfter("0", 3));
    }

    @Test
    void parseRetryAfter_httpDate_returnsPositiveDelta() throws Exception {
        // HTTP-date 指向 10 秒后的时刻，退避应约为 10 秒（允许一定误差）
        java.time.ZonedDateTime future = java.time.ZonedDateTime.now().plusSeconds(10);
        String httpDate = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(future);
        long delay = invokeParseRetryAfter(httpDate, 3);
        assertTrue(delay > 5000L && delay <= 10000L, "http-date delay should be ~10s, got=" + delay);
    }

    @Test
    void parseRetryAfter_httpDate_inPast_returnsZero() throws Exception {
        java.time.ZonedDateTime past = java.time.ZonedDateTime.now().minusSeconds(60);
        String httpDate = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(past);
        assertEquals(0L, invokeParseRetryAfter(httpDate, 3));
    }

    @Test
    void parseRetryAfter_null_usesDefaultBackoff() throws Exception {
        long delay = invokeParseRetryAfter(null, 3);
        // attempt = 3 - 3 + 1 = 1 → 2^1 * 1000 = 2000 + jitter(0..1000) → [2000, 3000)
        assertTrue(delay >= 2000L && delay < 3000L, "default backoff attempt=1 should be ~2s, got=" + delay);
    }

    @Test
    void parseRetryAfter_garbage_usesDefaultBackoff() throws Exception {
        long delay = invokeParseRetryAfter("not-a-date-or-number", 2);
        // attempt = 3 - 2 + 1 = 2 → 2^2 * 1000 = 4000 + jitter(0..1000) → [4000, 5000)
        assertTrue(delay >= 4000L && delay < 5000L, "default backoff attempt=2 should be ~4s, got=" + delay);
    }

    private IHttpClient mockClient(int status, Map<String, String> headers, String body) {
        return new IHttpClient() {
            @Override
            public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, ICancelToken ct) {
                return CompletableFuture.completedFuture(new IHttpResponse() {
                    @Override
                    public int getHttpStatus() {
                        return status;
                    }

                    @Override
                    public Map<String, String> getHeaders() {
                        return headers;
                    }

                    @Override
                    public String getBody() {
                        return body;
                    }

                    @Override
                    public <T> T getBodyAsBean(Class<T> cl) {
                        return null;
                    }

                    @Override
                    public String getBodyAsString() {
                        return body;
                    }

                    @Override
                    public byte[] getBodyAsBytes() {
                        return body == null ? new byte[0] : body.getBytes();
                    }

                    @Override
                    public String getContentType() {
                        return "application/json";
                    }

                    @Override
                    public String getCharset() {
                        return "UTF-8";
                    }
                });
            }

            @Override
            public CompletionStage<IHttpResponse> fetchStreamAsync(HttpRequest req,
                    io.nop.http.api.client.IServerEventAggregator a, ICancelToken ct) {
                return null;
            }

            @Override
            public CompletionStage<IHttpResponse> downloadAsync(HttpRequest req,
                    io.nop.http.api.client.IHttpOutputFile t, io.nop.http.api.client.DownloadOptions o, ICancelToken ct) {
                return null;
            }

            @Override
            public CompletionStage<IHttpResponse> uploadAsync(HttpRequest req,
                    io.nop.http.api.client.IHttpInputFile f, io.nop.http.api.client.UploadOptions o, ICancelToken ct) {
                return null;
            }
        };
    }

    private GatewayRouteModel buildRoute() {
        GatewayInvokeModel invoke = new GatewayInvokeModel();
        invoke.setUrl((IEvalAction) ctx -> "http://test-upstream/v1/chat/completions");
        GatewayRouteModel route = new GatewayRouteModel();
        route.setInvoke(invoke);
        return route;
    }

    @Test
    void invokeUrl_429exhausted_throwsWithStatusAndBody() {
        InvokeProcessor processor = new InvokeProcessor(null,
                mockClient(429, Map.of("Retry-After", "0"), "rate-limited-body"));
        GatewayRouteModel route = buildRoute();
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setHttpMethod("POST");

        CompletionException ex = assertThrows(CompletionException.class,
                () -> processor.invoke(route, ApiRequest.build(Map.of()), ctx)
                        .toCompletableFuture().join());
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof NopException);
        NopException ne = (NopException) cause;
        assertEquals(GatewayErrors.ERR_GATEWAY_UPSTREAM_429.getErrorCode(), ne.getErrorCode());
        assertEquals(429, ne.getParam("httpStatus"));
        assertEquals("rate-limited-body", ne.getParam("responseBody"));
    }

    @Test
    void invokeUrl_5xxexhausted_throwsWithStatusAndBody() {
        InvokeProcessor processor = new InvokeProcessor(null,
                mockClient(503, Map.of(), "bad-gateway"));
        GatewayRouteModel route = buildRoute();
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setHttpMethod("POST");

        CompletionException ex = assertThrows(CompletionException.class,
                () -> processor.invoke(route, ApiRequest.build(Map.of()), ctx)
                        .toCompletableFuture().join());
        Throwable cause = ex.getCause();
        assertTrue(cause instanceof NopException);
        NopException ne = (NopException) cause;
        assertEquals(GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED.getErrorCode(), ne.getErrorCode());
        assertEquals(503, ne.getParam("httpStatus"));
        assertEquals("bad-gateway", ne.getParam("responseBody"));
    }

    @Test
    void invokeUrl_success_returnsResponse() {
        InvokeProcessor processor = new InvokeProcessor(null,
                mockClient(200, Map.of(), "{\"ok\":true}"));
        GatewayRouteModel route = buildRoute();
        route.getInvoke().setWrapResponse(true);
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setHttpMethod("POST");

        io.nop.api.core.beans.ApiResponse<?> resp = processor.invoke(route, ApiRequest.build(Map.of()), ctx)
                .toCompletableFuture().join();
        assertEquals(200, resp.getHttpStatus());
    }
}
