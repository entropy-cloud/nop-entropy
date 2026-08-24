package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.gateway.GatewayErrors;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class AiFailoverGatewayInterceptorTest {

    @Test
    void primarySuccess_returnsDirectly() {
        AiFailoverGatewayInterceptor interceptor = new AiFailoverGatewayInterceptor();
        interceptor.setFallbackUrls(List.of("https://fallback.test/api"));

        ApiRequest<String> request = ApiRequest.build("test");
        IGatewayContext ctx = new GatewayContextImpl();

        IGatewayInvocation successInvocation = new IGatewayInvocation() {
            @Override
            public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> req, IGatewayContext svcCtx) {
                return CompletableFuture.completedFuture(ApiResponse.success("ok"));
            }

            @Override public ApiRequest<?> proceedOnRequest(ApiRequest<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnResponse(ApiResponse<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamStart(ApiRequest<?> r, IGatewayContext c) {}
            @Override public Object proceedOnStreamElement(Object e, IGatewayContext c) { return e; }
            @Override public Object proceedOnStreamError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamComplete(IGatewayContext c) {}
        };

        ApiResponse<?> result = interceptor.invoke(successInvocation, request, ctx).toCompletableFuture().join();
        assertEquals("ok", result.getData());
    }

    @Test
    void primaryFails_usesFallback() {
        AiFailoverGatewayInterceptor interceptor = new AiFailoverGatewayInterceptor();
        interceptor.setFallbackUrls(List.of("https://fallback.test/api"));

        interceptor.setHttpClient(new IHttpClient() {
            @Override public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, io.nop.api.core.util.ICancelToken ct) {
                return CompletableFuture.completedFuture(new IHttpResponse() {
                    @Override public int getHttpStatus() { return 200; }
                    @Override public Map<String, String> getHeaders() { return Map.of(); }
                    @Override public String getBody() { return "fallback-ok"; }
                    @Override public <T> T getBodyAsBean(Class<T> cl) { return null; }
                    @Override public String getBodyAsString() { return "fallback-ok"; }
                    @Override public byte[] getBodyAsBytes() { return "fallback-ok".getBytes(); }
                    @Override public String getContentType() { return "text/plain"; }
                    @Override public String getCharset() { return "UTF-8"; }
                });
            }
            @Override public CompletionStage<IHttpResponse> fetchStreamAsync(HttpRequest req,
                    io.nop.http.api.client.IServerEventAggregator a, io.nop.api.core.util.ICancelToken ct) { return null; }
            @Override public CompletionStage<IHttpResponse> downloadAsync(HttpRequest req, io.nop.http.api.client.IHttpOutputFile t,
                    io.nop.http.api.client.DownloadOptions o, io.nop.api.core.util.ICancelToken ct) { return null; }
            @Override public CompletionStage<IHttpResponse> uploadAsync(HttpRequest req, io.nop.http.api.client.IHttpInputFile f,
                    io.nop.http.api.client.UploadOptions o, io.nop.api.core.util.ICancelToken ct) { return null; }
        });

        ApiRequest<String> request = ApiRequest.build("test");
        IGatewayContext ctx = new GatewayContextImpl();

        IGatewayInvocation failInvocation = new IGatewayInvocation() {
            @Override
            public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> req, IGatewayContext svcCtx) {
                return CompletableFuture.failedFuture(
                        new NopException(GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED)
                                .param("httpStatus", 503));
            }

            @Override public ApiRequest<?> proceedOnRequest(ApiRequest<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnResponse(ApiResponse<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamStart(ApiRequest<?> r, IGatewayContext c) {}
            @Override public Object proceedOnStreamElement(Object e, IGatewayContext c) { return e; }
            @Override public Object proceedOnStreamError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamComplete(IGatewayContext c) {}
        };

        ApiResponse<?> result = interceptor.invoke(failInvocation, request, ctx).toCompletableFuture().join();
        assertEquals("fallback-ok", result.getData());
    }

    // ======================= 凭证头剥离 + maxRetries 语义 =======================

    /** 记录请求的 mock client：每个请求返回固定状态码，供断言 fallback 请求形态与尝试次数。 */
    private static final class RecordingHttpClient extends java.util.ArrayList<HttpRequest> implements IHttpClient {
        private final int status;

        RecordingHttpClient(int status) {
            this.status = status;
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, io.nop.api.core.util.ICancelToken ct) {
            add(req);
            return CompletableFuture.completedFuture(new IHttpResponse() {
                @Override public int getHttpStatus() { return status; }
                @Override public Map<String, String> getHeaders() { return Map.of(); }
                @Override public String getBody() { return "fallback-body"; }
                @Override public <T> T getBodyAsBean(Class<T> cl) { return null; }
                @Override public String getBodyAsString() { return "fallback-body"; }
                @Override public byte[] getBodyAsBytes() { return "fallback-body".getBytes(); }
                @Override public String getContentType() { return "text/plain"; }
                @Override public String getCharset() { return "UTF-8"; }
            });
        }

        @Override public CompletionStage<IHttpResponse> fetchStreamAsync(HttpRequest req,
                io.nop.http.api.client.IServerEventAggregator a, io.nop.api.core.util.ICancelToken ct) { return null; }
        @Override public CompletionStage<IHttpResponse> downloadAsync(HttpRequest req,
                io.nop.http.api.client.IHttpOutputFile t, io.nop.http.api.client.DownloadOptions o,
                io.nop.api.core.util.ICancelToken ct) { return null; }
        @Override public CompletionStage<IHttpResponse> uploadAsync(HttpRequest req,
                io.nop.http.api.client.IHttpInputFile f, io.nop.http.api.client.UploadOptions o,
                io.nop.api.core.util.ICancelToken ct) { return null; }
    }

    private static IGatewayInvocation upstreamAlwaysFails() {
        return new IGatewayInvocation() {
            @Override
            public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> req, IGatewayContext svcCtx) {
                return CompletableFuture.failedFuture(
                        new NopException(GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED).param("httpStatus", 503));
            }

            @Override public ApiRequest<?> proceedOnRequest(ApiRequest<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnResponse(ApiResponse<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamStart(ApiRequest<?> r, IGatewayContext c) {}
            @Override public Object proceedOnStreamElement(Object e, IGatewayContext c) { return e; }
            @Override public Object proceedOnStreamError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamComplete(IGatewayContext c) {}
        };
    }

    @Test
    void fallbackRequest_stripsSensitiveHeadersByDefault() {
        RecordingHttpClient client = new RecordingHttpClient(200);
        AiFailoverGatewayInterceptor interceptor = new AiFailoverGatewayInterceptor();
        interceptor.setHttpClient(client);
        interceptor.setFallbackUrls(List.of("https://other-provider.test/api"));

        ApiRequest<String> request = ApiRequest.build("test");
        // 生产链路 header key 已小写化；authorization 是发给主上游的凭证
        request.setHeaders(Map.of(
                "authorization", "Bearer sk-primary-key",
                "x-api-key", "sk-primary-key",
                "cookie", "session=abc",
                "content-type", "application/json"));

        interceptor.invoke(upstreamAlwaysFails(), request, new GatewayContextImpl())
                .toCompletableFuture().join();

        assertEquals(1, client.size(), "应发起一次 fallback 请求");
        Map<String, Object> sent = client.get(0).getHeaders();
        assertFalse(sent.containsKey("authorization"),
                "fallback 请求不得携带主上游的 Authorization 凭证（防凭证外发给第三方域）: " + sent);
        assertFalse(sent.containsKey("x-api-key"), "fallback 请求不得携带 x-api-key 凭证");
        assertFalse(sent.containsKey("cookie"), "fallback 请求不得携带 cookie");
        assertEquals("application/json", sent.get("content-type"), "非凭证头照常转发");
    }

    @Test
    void fallbackRequest_forwardsSensitiveHeadersWhenExplicitlyEnabled() {
        // 同 key 多端点（同提供商多 baseUrl）场景的显式逃生开关：forwardSensitiveHeaders=true
        RecordingHttpClient client = new RecordingHttpClient(200);
        AiFailoverGatewayInterceptor interceptor = new AiFailoverGatewayInterceptor();
        interceptor.setHttpClient(client);
        interceptor.setForwardSensitiveHeaders(true);
        interceptor.setFallbackUrls(List.of("https://same-provider-mirror.test/api"));

        ApiRequest<String> request = ApiRequest.build("test");
        request.setHeaders(Map.of("authorization", "Bearer sk-shared-key"));

        interceptor.invoke(upstreamAlwaysFails(), request, new GatewayContextImpl())
                .toCompletableFuture().join();

        assertEquals("Bearer sk-shared-key", client.get(0).getHeaders().get("authorization"));
    }

    @Test
    void maxRetries_capsTotalFallbackAttempts() {
        // maxRetries=1 且配置 3 个 fallback URL：只允许尝试第 1 个 fallback，
        // 之后必须失败返回，而不是继续遍历 fallbackUrls
        RecordingHttpClient client = new RecordingHttpClient(429);
        AiFailoverGatewayInterceptor interceptor = new AiFailoverGatewayInterceptor();
        interceptor.setHttpClient(client);
        interceptor.setFallbackUrls(List.of(
                "https://f1.test/api", "https://f2.test/api", "https://f3.test/api"));
        interceptor.setMaxRetries(1);

        ApiRequest<String> request = ApiRequest.build("test");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> interceptor.invoke(upstreamAlwaysFails(), request, new GatewayContextImpl())
                        .toCompletableFuture().join());

        assertEquals(1, client.size(),
                "maxRetries=1 必须把 fallback 尝试次数限制为 1（配置项不得是无效契约）");
    }
}
