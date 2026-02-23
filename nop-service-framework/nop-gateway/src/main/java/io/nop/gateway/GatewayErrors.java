/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GatewayErrors {
    String ARG_INTERCEPTOR = "interceptor";
    String ARG_ROUTE_ID = "routeId";
    String ARG_ID = "id";
    String ARG_URL = "url";

    ErrorCode ERR_GATEWAY_NO_RPC_SUPPORT = define("nop.err.gateway.no-rpc-support",
            "没有引入nop-rpc-cluster模块，不支持RPC代理调用");

    ErrorCode ERR_GATEWAY_NO_HTTP_CLIENT = define("nop.err.gateway.no-http-client",
            "网关缺少IHttpClient，无法执行URL调用和流式传输");

    ErrorCode ERR_GATEWAY_INVOKE_WITH_NULL_URL = define("nop.err.gateway.invoke-with-null-url",
            "URL表达式计算结果为空");

    ErrorCode ERR_GATEWAY_STREAMING_NOT_ENABLED = define("nop.err.gateway.streaming-not-enabled",
            "当前路由未启用流式传输");

    ErrorCode ERR_GATEWAY_FORWARD_TO_NON_STREAMING = define("nop.err.gateway.forward-to-non-streaming",
            "不能从流式路由转发到非流式路由");

    ErrorCode ERR_GATEWAY_INTERCEPTOR_ERROR = define("nop.err.gateway.interceptor-error",
            "拦截器执行失败: ${interceptor}");

    ErrorCode ERR_GATEWAY_ROUTE_NOT_FOUND = define("nop.err.gateway.route-not-found",
            "未找到路由: ${routeId}");

    ErrorCode ERR_GATEWAY_FORWARD_STREAMING_INCOMPATIBLE = define("nop.err.gateway.forward-streaming-incompatible",
            "不允许从流式路由转发到非流式路由");

    ErrorCode ERR_GATEWAY_UNKNOWN_ROUTE = define("nop.err.gateway.unknown-route",
            "未知的路由ID: ${id}");

    ErrorCode ERR_GATEWAY_FORWARD_NOT_SUPPORTED_IN_INVOKE = define("nop.err.gateway.forward-not-supported-in-invoke",
            "在GatewayRouteExecution的proceedInvoke方法中不支持forward操作，请在RouteExecutor层面处理: ${routeId}");
}
