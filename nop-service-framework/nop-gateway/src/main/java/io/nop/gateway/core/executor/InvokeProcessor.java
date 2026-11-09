/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.gateway.GatewayConfigs;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.rpc.core.utils.RpcHelper;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

import static io.nop.gateway.GatewayConfigs.CFG_GATEWAY_UPSTREAM_REQUEST_TIMEOUT;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_INVOKE_WITH_NULL_URL;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_NO_HTTP_CLIENT;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_NO_RPC_SUPPORT;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_UPSTREAM_429;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT;

/**
 * 处理路由调用逻辑，支持三种调用方式：
 * <ul>
 *   <li>source - XPL脚本执行</li>
 *   <li>serviceName - RPC服务调用</li>
 *   <li>url - HTTP URL调用</li>
 * </ul>
 */
public class InvokeProcessor {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(InvokeProcessor.class);

    private final IRpcServiceInvoker invoker;
    private final IHttpClient httpClient;

    /**
     * 2.3-C8-002（plan 2508 裁定②）：url 路由腿上游在途许可池（与 fallback 腿共享）。
     * null = 不设上界档（配置 ≤0 时的退化语义）。
     */
    private final ISemaphore admissionPermits;

    /**
     * 退避抖动源（[0,1) 均匀分布）。可注入供 seeded 分布断言；生产默认 {@link Math#random()}，
     * 5xx 分支迁移至同源后分布零变化。
     */
    private final DoubleSupplier jitterSource;

    /**
     * 仅测试语义的兼容构造：无上界许可池 + 默认抖动源。生产装配唯一经
     * {@code GatewayHandler} 携许可池的构造，不得作为旁路。
     */
    public InvokeProcessor(IRpcServiceInvoker invoker, IHttpClient httpClient) {
        this(invoker, httpClient, null, Math::random);
    }

    public InvokeProcessor(IRpcServiceInvoker invoker, IHttpClient httpClient, ISemaphore admissionPermits) {
        this(invoker, httpClient, admissionPermits, Math::random);
    }

    public InvokeProcessor(IRpcServiceInvoker invoker, IHttpClient httpClient, ISemaphore admissionPermits,
                           DoubleSupplier jitterSource) {
        this.invoker = invoker;
        this.httpClient = httpClient;
        this.admissionPermits = admissionPermits;
        this.jitterSource = jitterSource == null ? Math::random : jitterSource;
    }


    /**
     * 执行路由调用
     *
     * @param route   路由配置
     * @param request API请求
     * @param context 网关上下文
     * @return 异步响应
     */
    public CompletionStage<ApiResponse<?>> invoke(GatewayRouteModel route, ApiRequest<?> request,
                                                  IGatewayContext context) {
        GatewayInvokeModel invoke = route.getInvoke();
        if (invoke == null) {
            throw new NopException(ERR_GATEWAY_NO_RPC_SUPPORT).source(route);
        }

        // 2.3-C3-010（plan 2550 裁定 E）：转发前断连检查——客户端已断连不发起转发
        // （非幂等请求的幽灵执行面）。CancellationException 不在 failover 可重试白名单，
        // 链路就此终止。
        if (clientAborted(context)) {
            LOG.warn("nop.gateway.client-aborted-before-invoke:path={}", context.getRequestPath());
            return CompletableFuture.failedFuture(new CancellationException("client aborted"));
        }

        RpcHelper.setHttpUrl(request, null);
        RpcHelper.setHttpMethod(request, null);

        // 优先级: source > serviceName > url
        if (invoke.getSource() != null) {
            return invokeSource(invoke, request, context);
        } else if (invoke.getServiceName() != null) {
            return invokeRpc(invoke, request, context);
        } else if (invoke.getUrl() != null) {
            return invokeUrl(invoke, request, context);
        } else {
            throw new NopException(ERR_GATEWAY_NO_RPC_SUPPORT).source(route);
        }
    }

    /**
     * 2.3-C3-010（plan 2550 裁定 E）：CL-025 断连观测检查。底层 IHttpServerContext 缺省
     * （纯构造语境/不可观测适配器）= 不可观测档，维持既有语义零破坏。
     */
    static boolean clientAborted(IGatewayContext context) {
        IHttpServerContext http = context.getHttpServerContext();
        return http != null && http.isClientAborted();
    }

    /**
     * XPL脚本执行
     */
    private CompletionStage<ApiResponse<?>> invokeSource(GatewayInvokeModel invoke, ApiRequest<?> request,
                                                         IGatewayContext svcCtx) {
        Object result = invoke.getSource().call2(null, request, svcCtx, svcCtx.getEvalScope());
        return FutureHelper.toCompletionStage(result).thenApply(ApiResponse::wrap);
    }

    /**
     * RPC服务调用
     */
    private CompletionStage<ApiResponse<?>> invokeRpc(GatewayInvokeModel invoke, ApiRequest<?> request,
                                                      IGatewayContext context) {
        if (invoker == null) {
            throw new NopException(ERR_GATEWAY_NO_RPC_SUPPORT);
        }

        String serviceName = invoke.getServiceName();

        // 确定服务方法：优先使用配置的serviceMethod，否则从请求头获取
        String svcMethod = invoke.getServiceMethod();
        if (svcMethod == null) {
            svcMethod = ApiHeaders.getSvcAction(request);
        }
        if (invoke.getConfirmMethod() != null) {
            ApiHeaders.setTccConfirm(request, invoke.getConfirmMethod());
        }
        if (invoke.getCancelMethod() != null) {
            ApiHeaders.setTccCancel(request, invoke.getCancelMethod());
        }

        return invoker.invokeAsync(serviceName, svcMethod, request, context);
    }


    /**
     * HTTP URL调用
     */
    private CompletionStage<ApiResponse<?>> invokeUrl(GatewayInvokeModel invoke, ApiRequest<?> request,
                                                       IGatewayContext svcCtx) {
        if (httpClient == null) {
            throw new NopException(ERR_GATEWAY_NO_HTTP_CLIENT);
        }

        // 评估URL表达式（可能是动态的）
        Object urlObj = invoke.getUrl().invoke(svcCtx.getEvalScope());
        if (urlObj == null) {
            throw new NopException(ERR_GATEWAY_INVOKE_WITH_NULL_URL).source(invoke);
        }

        String url = urlObj.toString();
        Integer configured = GatewayConfigs.CFG_GATEWAY_UPSTREAM_MAX_RETRIES.get();
        int maxRetries = configured == null ? 3 : Math.max(configured, 0);

        // 准入（裁定②）：置于全部同步抛出点之后——httpClient 缺失/URL 为 null 两路不沾许可。
        // 主腿拒绝沿既有同步 throw 通道（RouteExecutor→processRoute try/catch 转 failedFuture）。
        if (admissionPermits != null && !admissionPermits.tryAcquire(1, 0)) {
            throw new NopException(ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT).source(invoke)
                    .param("url", url);
        }
        if (admissionPermits == null) {
            return invokeUrlWithRetry(url, request, invoke, svcCtx, maxRetries);
        }
        // 许可覆盖主腿全程（首发 + 429/5xx 重试挂起窗 + 终态组装）：挂起窗正是 body 解析树的
        // 内存持有窗，释放许可即重开 OOM 轨迹。全出口释放 = 同步抛出 catch + 终态 whenComplete。
        CompletionStage<ApiResponse<?>> stage;
        try {
            stage = invokeUrlWithRetry(url, request, invoke, svcCtx, maxRetries);
        } catch (RuntimeException | Error e) {
            admissionPermits.release(1);
            throw e;
        }
        stage.whenComplete((r, e) -> admissionPermits.release(1));
        return stage;
    }

    private CompletionStage<ApiResponse<?>> invokeUrlWithRetry(String url, ApiRequest<?> request,
                                                                GatewayInvokeModel invoke,
                                                                IGatewayContext svcCtx, int retriesLeft) {
        HttpRequest httpRequest = buildHttpRequest(url, request);
        if (invoke.getHttpMethod() != null) {
            httpRequest.setMethod(invoke.getHttpMethod());
        } else {
            httpRequest.setMethod(svcCtx.getHttpMethod());
        }
        // 2.3-C4-003（plan 2551 裁定 C②）：上游 fetch 显式超时（键
        // CFG_GATEWAY_UPSTREAM_REQUEST_TIMEOUT 缺省 30s；≤0=用客户端缺省）——429/5xx 挂起窗受
        // 双重上界（Retry-After 30s 钳制 × 请求超时），无超时的上游挂起不再无限占许可与内存。
        Long configuredTimeout = CFG_GATEWAY_UPSTREAM_REQUEST_TIMEOUT.get();
        if (configuredTimeout != null && configuredTimeout > 0) {
            httpRequest.setTimeout(configuredTimeout);
        }

        return httpClient.fetchAsync(httpRequest, null).thenCompose(httpResponse -> {
            int httpStatus = httpResponse.getHttpStatus();

            // 429 Too Many Requests — 退避重试
            if (httpStatus == 429 && retriesLeft > 0) {
                // 2.3-C3-010：重试延迟触发前断连检查——客户端已断连不再重发非幂等 POST
                if (clientAborted(svcCtx)) {
                    LOG.warn("nop.gateway.client-aborted-before-retry:status=429,retriesLeft={}", retriesLeft);
                    return CompletableFuture.failedFuture(new CancellationException("client aborted"));
                }
                String retryAfter = httpResponse.getHeaders().get("Retry-After");
                // equal jitter（裁定②(b)）：施加于 parseRetryAfter 钳制之后——保留 ≥D/2 的上游
                // 恢复窗并打破同值 Retry-After 的同步重试波；parseRetryAfter 与 30s 钳制零改动
                long delayMs = applyEqualJitter(parseRetryAfter(retryAfter, retriesLeft));
                return delayedFuture(delayMs).thenCompose(v ->
                        invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1)
                );
            }

            // 其他服务端错误 — 抛异常供上层拦截器捕获（用于 failover）
            if (httpStatus >= 500 && retriesLeft > 0) {
                // 2.3-C3-010：同上——断连后不进入 5xx 重试
                if (clientAborted(svcCtx)) {
                    LOG.warn("nop.gateway.client-aborted-before-retry:status=5xx,retriesLeft={}", retriesLeft);
                    return CompletableFuture.failedFuture(new CancellationException("client aborted"));
                }
                long delayMs = (long) (Math.pow(2, 3 - retriesLeft) * 1000) + (long) (jitterSource.getAsDouble() * 1000);
                return delayedFuture(delayMs).thenCompose(v ->
                        invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1)
                );
            }

            // 最终响应处理
            if (httpStatus == 429) {
                return CompletableFuture.failedFuture(new NopException(ERR_GATEWAY_UPSTREAM_429)
                        .param("httpStatus", httpStatus)
                        .param("responseBody", httpResponse.getBody()));
            }
            if (httpStatus >= 500) {
                return CompletableFuture.failedFuture(new NopException(ERR_GATEWAY_UPSTREAM_FAILED)
                        .param("httpStatus", httpStatus)
                        .param("responseBody", httpResponse.getBody()));
            }

            ApiResponse<Object> apiResponse;

            if (Boolean.TRUE.equals(invoke.getWrapResponse())) {
                apiResponse = new ApiResponse<>();
                apiResponse.setWrapper(true);
                apiResponse.setData(httpResponse.getBody());
            } else {
                apiResponse = httpResponse.getBodyAsBean(ApiResponse.class);
                if (apiResponse == null)
                    apiResponse = new ApiResponse<>();
            }
            apiResponse.setHttpStatus(httpStatus);
            httpResponse.getHeaders().forEach(apiResponse::setHeader);
            return CompletableFuture.completedFuture(apiResponse);
        });
    }

    /**
     * 2.3-C4-003（plan 2551 裁定 C①）：重试延迟载体由 JDK delayedExecutor 改平台
     * {@code globalTimer().executeOn(globalWorker())}——触发入平台 timer 治理面（O(1) 计时器槽），
     * 完成回调经 executeOn hop 落 worker 池（重试发起——buildHttpRequest + fetch 提交——不得落
     * timer 线程，否则复刻 C4-002 已修的 timer-pinning 形态；仓内先例
     * JsonRpcWebSocketHandler.registerKeepAlive）。C4 复核勘误 8 注记：JDK25 delayedExecutor 已改
     * common pool 多线程，原「单线程 Delayer 钉死」P1 支柱证伪——本改动为治理一致性而非容量修复。
     */
    private CompletionStage<Void> delayedFuture(long delayMs) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())
                .schedule(() -> future.complete(null), delayMs, TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * 上游 Retry-After 指示的重试延迟上限。异常/被滥用的上游响应（如 Retry-After: 86400）
     * 不得让单个请求挂起数小时占用执行资源，超限值一律钳制。
     */
    static final long MAX_RETRY_DELAY_MS = 30_000L;

    private long parseRetryAfter(String retryAfter, int retriesLeft) {
        if (retryAfter != null && !retryAfter.isEmpty()) {
            String trimmed = retryAfter.trim();
            try {
                return capRetryDelay(Long.parseLong(trimmed) * 1000);
            } catch (NumberFormatException e) {
                // 不是秒数格式，尝试 HTTP-date 格式
            }
            try {
                java.time.ZonedDateTime httpDate = java.time.ZonedDateTime.parse(trimmed,
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
                long delayMs = httpDate.toInstant().toEpochMilli() - CoreMetrics.currentTimeMillis();
                if (delayMs > 0) {
                    return capRetryDelay(delayMs);
                }
                return 0;
            } catch (java.time.format.DateTimeParseException e) {
                // 不是 HTTP-date 格式，使用默认退避
            }
        }
        // 默认退避：2^attempt * 1s + jitter
        int attempt = 3 - retriesLeft + 1;
        return capRetryDelay((long) (Math.pow(2, attempt) * 1000) + (long) (Math.random() * 1000));
    }

    private static long capRetryDelay(long delayMs) {
        return Math.min(Math.max(delayMs, 0L), MAX_RETRY_DELAY_MS);
    }

    /**
     * equal jitter：D/2 + U(0, D/2)。D=0 → 0。
     */
    long applyEqualJitter(long delayMs) {
        if (delayMs <= 0)
            return 0L;
        long half = delayMs / 2;
        return half + (long) (jitterSource.getAsDouble() * half);
    }

    /**
     * 构建HTTP请求
     */
    private HttpRequest buildHttpRequest(String url, ApiRequest<?> request) {
        HttpRequest httpRequest = HttpRequest.post(url);

        // 复制请求头
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                httpRequest.header(entry.getKey(), entry.getValue());
            }
        }

        // 设置请求体
        httpRequest.setBody(request.getData());

        return httpRequest;
    }
}
