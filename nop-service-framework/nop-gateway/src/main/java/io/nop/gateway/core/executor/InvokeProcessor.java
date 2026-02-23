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

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_INVOKE_WITH_NULL_URL;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_NO_HTTP_CLIENT;
import static io.nop.gateway.GatewayErrors.ERR_GATEWAY_NO_RPC_SUPPORT;

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
        HttpRequest httpRequest = buildHttpRequest(url, request);

        return httpClient.fetchAsync(httpRequest, null).thenApply(httpResponse -> {
            ApiResponse<Object> apiResponse;

            if (Boolean.TRUE.equals(invoke.getWrapResponse())) {
                apiResponse = httpResponse.getBodyAsBean(ApiResponse.class);
            } else {
                apiResponse = new ApiResponse<>();
                return apiResponse;
            }
            apiResponse.setHttpStatus(httpResponse.getHttpStatus());
            httpResponse.getHeaders().forEach(apiResponse::setHeader);
            return apiResponse;
        });
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
