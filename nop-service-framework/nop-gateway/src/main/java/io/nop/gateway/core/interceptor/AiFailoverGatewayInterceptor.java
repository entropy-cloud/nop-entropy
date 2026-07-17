package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.gateway.GatewayErrors;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class AiFailoverGatewayInterceptor implements IGatewayInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AiFailoverGatewayInterceptor.class);

    private IHttpClient httpClient;
    private List<String> fallbackUrls = new ArrayList<>();
    private int maxRetries = 3;

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setFallbackUrls(List<String> fallbackUrls) {
        this.fallbackUrls = fallbackUrls;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public CompletionStage<ApiResponse<?>> invoke(IGatewayInvocation invocation, ApiRequest<?> request, IGatewayContext svcCtx) {
        if (svcCtx.isStreamingMode()) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Streaming failover not yet implemented"));
        }
        return tryInvokeWithFallback(invocation, request, svcCtx, 0);
    }

    private CompletionStage<ApiResponse<?>> tryInvokeWithFallback(IGatewayInvocation invocation, ApiRequest<?> request,
                                                                   IGatewayContext svcCtx, int fallbackIndex) {
        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
        invocation.proceedInvoke(request, svcCtx).whenComplete((response, err) -> {
            if (err == null) {
                future.complete(response);
                return;
            }

            // 检查是否是可重试的错误（429 或 5xx）
            boolean retryable = false;
            if (err instanceof NopException) {
                String errCode = ((NopException) err).getErrorCode();
                retryable = GatewayErrors.ERR_GATEWAY_UPSTREAM_429.getErrorCode().equals(errCode)
                        || GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED.getErrorCode().equals(errCode);
            }

            if (!retryable) {
                future.completeExceptionally(err);
                return;
            }

            // 尝试备用 URL
            if (fallbackIndex < fallbackUrls.size()) {
                String fallbackUrl = fallbackUrls.get(fallbackIndex);
                LOG.warn("Upstream failed, trying fallback url={}, attempt={}", fallbackUrl, fallbackIndex + 1);
                tryFallbackUrl(fallbackUrl, request, svcCtx, fallbackIndex + 1)
                        .whenComplete((r, e) -> {
                            if (e != null) future.completeExceptionally(e);
                            else future.complete(r);
                        });
            } else {
                future.completeExceptionally(err);
            }
        });
        return future;
    }

    private CompletionStage<ApiResponse<?>> tryFallbackUrl(String url, ApiRequest<?> request,
                                                            IGatewayContext svcCtx, int nextFallbackIndex) {
        HttpRequest httpRequest = HttpRequest.post(url);
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((k, v) -> httpRequest.header(k, v));
        }
        httpRequest.setBody(request.getData());
        httpRequest.setMethod(svcCtx.getHttpMethod());

        return httpClient.fetchAsync(httpRequest, null).thenCompose(httpResponse -> {
            int status = httpResponse.getHttpStatus();
            if (status == 429 && nextFallbackIndex < fallbackUrls.size()) {
                return tryFallbackUrl(fallbackUrls.get(nextFallbackIndex), request, svcCtx, nextFallbackIndex + 1);
            }
            if (status >= 500 && nextFallbackIndex < fallbackUrls.size()) {
                return tryFallbackUrl(fallbackUrls.get(nextFallbackIndex), request, svcCtx, nextFallbackIndex + 1);
            }
            if (status >= 400) {
                return CompletableFuture.failedFuture(
                        new NopException(GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED)
                                .param("httpStatus", status));
            }
            ApiResponse<Object> apiResponse = new ApiResponse<>();
            apiResponse.setHttpStatus(status);
            apiResponse.setData(httpResponse.getBody());
            httpResponse.getHeaders().forEach(apiResponse::setHeader);
            return CompletableFuture.completedFuture(apiResponse);
        });
    }
}
