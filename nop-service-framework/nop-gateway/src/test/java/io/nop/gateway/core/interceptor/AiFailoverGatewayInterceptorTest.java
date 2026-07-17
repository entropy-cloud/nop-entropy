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
}
