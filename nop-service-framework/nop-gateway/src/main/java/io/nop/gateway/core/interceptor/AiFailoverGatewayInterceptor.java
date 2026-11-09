package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.gateway.GatewayErrors;
import io.nop.gateway.core.executor.UpstreamAdmission;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class AiFailoverGatewayInterceptor implements IGatewayInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AiFailoverGatewayInterceptor.class);

    /**
     * fallback 请求默认不转发的凭证/会话头（key 按小写比较，与 HTTP 层小写化约定一致）。
     * fallback 常指向另一提供商域，主上游凭证外发会造成凭证泄漏；同 key 多端点场景可显式
     * 开启 {@link #forwardSensitiveHeaders}。
     */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "x-api-key", "cookie");

    private IHttpClient httpClient;
    private List<String> fallbackUrls = new ArrayList<>();
    private int maxRetries = 3;
    private boolean forwardSensitiveHeaders = false;

    /**
     * 2.3-C8-002（plan 2508 裁定②）：fallback 腿与主腿共享的上游在途许可池；默认取
     * {@link UpstreamAdmission#shared()}，setter 供测试注入。null = 不设上界档。
     */
    private ISemaphore admissionPermits = UpstreamAdmission.shared();

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setAdmissionPermits(ISemaphore admissionPermits) {
        this.admissionPermits = admissionPermits;
    }

    public void setFallbackUrls(List<String> fallbackUrls) {
        this.fallbackUrls = fallbackUrls;
    }

    /**
     * fallback 尝试次数上限（不含主上游调用）。默认 3；实际尝试数 =
     * min(fallbackUrls.size(), maxRetries)。
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setForwardSensitiveHeaders(boolean forwardSensitiveHeaders) {
        this.forwardSensitiveHeaders = forwardSensitiveHeaders;
    }

    @Override
    public CompletionStage<ApiResponse<?>> invoke(IGatewayInvocation invocation, ApiRequest<?> request, IGatewayContext svcCtx) {
        if (svcCtx.isStreamingMode()) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Streaming failover not yet implemented"));
        }
        return tryInvokeWithFallback(invocation, request, svcCtx, 0);
    }

    /** fallback 尝试次数上界：maxRetries 与 fallbackUrls.size() 取小。 */
    private int maxFallbackAttempts() {
        return Math.min(fallbackUrls.size(), Math.max(maxRetries, 0));
    }

    /**
     * 2.3-C3-010（plan 2550 裁定 E）：CL-025 断连观测检查（不可观测档 = null/false，零破坏）。
     */
    private static boolean clientAborted(IGatewayContext svcCtx) {
        io.nop.http.api.server.IHttpServerContext http = svcCtx.getHttpServerContext();
        return http != null && http.isClientAborted();
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

            // 尝试备用 URL（总尝试次数受 maxRetries 限制）
            // 2.3-C3-010（plan 2550 裁定 E）：failover 决策前断连检查——客户端已断连不再
            // 向 fallback 域重发（幽灵重试子面，C4-003 同源）。
            if (clientAborted(svcCtx)) {
                LOG.warn("nop.gateway.client-aborted-before-failover:attempt={}", fallbackIndex);
                future.completeExceptionally(err);
                return;
            }
            if (fallbackIndex < maxFallbackAttempts()) {
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
        // 2.3-C3-010：每腿发起前断连检查（回调体语境：以 failedFuture 收敛，不 throw）
        if (clientAborted(svcCtx)) {
            return CompletableFuture.failedFuture(
                    new java.util.concurrent.CancellationException("client aborted"));
        }
        HttpRequest httpRequest = HttpRequest.post(url);
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((k, v) -> {
                if (!forwardSensitiveHeaders && k != null
                        && SENSITIVE_HEADERS.contains(k.toLowerCase(Locale.ROOT))) {
                    return; // 凭证头默认剥离，不外发给 fallback 域
                }
                httpRequest.header(k, v);
            });
        }
        httpRequest.setBody(request.getData());
        httpRequest.setMethod(svcCtx.getHttpMethod());

        // 逐腿准入（裁定②）：本方法运行在上游 whenComplete/thenCompose 回调体语境——拒绝必须以
        // failedFuture 返回而非同步 throw（回调体内 throw 只完成被丢弃的 stage，外层 future 永不完成
        // = 与 C4-007 :354 同型的请求挂死面）。准入拒绝码不在可重试白名单内，不触发下一腿级联。
        if (admissionPermits != null && !admissionPermits.tryAcquire(1, 0)) {
            return CompletableFuture.failedFuture(
                    new NopException(GatewayErrors.ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT).param("url", url));
        }
        CompletionStage<io.nop.http.api.client.IHttpResponse> fetch;
        try {
            fetch = httpClient.fetchAsync(httpRequest, null);
        } catch (RuntimeException | Error e) {
            if (admissionPermits != null)
                admissionPermits.release(1);
            // 回调体语境：同步抛出同样以 failedFuture 收敛，不得逃逸
            return CompletableFuture.failedFuture(e);
        }
        if (admissionPermits != null) {
            // 单腿单发时长持有；腿终态即释放，链推进时下一腿重新准入
            fetch.whenComplete((r, e) -> admissionPermits.release(1));
        }
        return fetch.thenCompose(httpResponse -> {
            int status = httpResponse.getHttpStatus();
            if (status == 429 && nextFallbackIndex < maxFallbackAttempts()) {
                return tryFallbackUrl(fallbackUrls.get(nextFallbackIndex), request, svcCtx, nextFallbackIndex + 1);
            }
            if (status >= 500 && nextFallbackIndex < maxFallbackAttempts()) {
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
