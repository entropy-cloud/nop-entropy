/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.rpc.core.utils.RpcHelper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_INVOKE_WITH_NULL_URL;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_NO_HTTP_CLIENT;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_NO_RPC_SUPPORT;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_UPSTREAM_429;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED;

/**
 * 处理路由调用逻辑，支持三种调用方式：
 * <ul>
 *   <li>source - XPL脚本执行</li>
 *   <li>serviceName - RPC服务调用</li>
 *   <li>url - HTTP URL调用</li>
 * </ul>
 */
public class InvokeProcessor {

    private final IRpcServiceInvoker invoker;
    private final IHttpClient httpClient;

    public InvokeProcessor(IRpcServiceInvoker invoker, IHttpClient httpClient) {
        this.invoker = invoker;
        this.httpClient = httpClient;
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
        int maxRetries = 3;
        return invokeUrlWithRetry(url, request, invoke, svcCtx, maxRetries);
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

        return httpClient.fetchAsync(httpRequest, null).thenCompose(httpResponse -> {
            int httpStatus = httpResponse.getHttpStatus();

            // 429 Too Many Requests — 退避重试
            if (httpStatus == 429 && retriesLeft > 0) {
                String retryAfter = httpResponse.getHeaders().get("Retry-After");
                long delayMs = parseRetryAfter(retryAfter, retriesLeft);
                return delayedFuture(delayMs).thenCompose(v ->
                        invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1)
                );
            }

            // 其他服务端错误 — 抛异常供上层拦截器捕获（用于 failover）
            if (httpStatus >= 500 && retriesLeft > 0) {
                long delayMs = (long) (Math.pow(2, 3 - retriesLeft) * 1000) + (long) (Math.random() * 1000);
                return delayedFuture(delayMs).thenCompose(v ->
                        invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1)
                );
            }

            // 最终响应处理
            if (httpStatus == 429) {
                return CompletableFuture.failedFuture(new NopException(ERR_GATEWAY_UPSTREAM_429)
                        .param("httpStatus", httpStatus));
            }
            if (httpStatus >= 500) {
                return CompletableFuture.failedFuture(new NopException(ERR_GATEWAY_UPSTREAM_FAILED)
                        .param("httpStatus", httpStatus));
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

    private CompletionStage<Void> delayedFuture(long delayMs) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> future.complete(null));
        return future;
    }

    private long parseRetryAfter(String retryAfter, int retriesLeft) {
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                return Long.parseLong(retryAfter.trim()) * 1000;
            } catch (NumberFormatException e) {
                // 不是秒数格式，使用默认退避
            }
        }
        // 默认退避：2^attempt * 1s + jitter
        int attempt = 3 - retriesLeft + 1;
        return (long) (Math.pow(2, attempt) * 1000) + (long) (Math.random() * 1000);
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
