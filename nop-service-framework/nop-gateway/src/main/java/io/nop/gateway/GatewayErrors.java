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
    ErrorCode ERR_GATEWAY_NO_RPC_SUPPORT = define("nop.err.gateway.no-rpc-support",
            "没有引入nop-rpc-cluster模块，不支持RPC代理调用");

    ErrorCode ERR_GATEWAY_NO_HTTP_CLIENT = define("nop.err.gateway.no-http-client",
            "GatewayHandler requires IHttpClient for URL-based invocations and streaming");

    ErrorCode ERR_GATEWAY_STREAMING_NOT_ENABLED = define("nop.err.gateway.streaming-not-enabled",
            "Streaming is not enabled for this route");

    ErrorCode ERR_GATEWAY_FORWARD_TO_NON_STREAMING = define("nop.err.gateway.forward-to-non-streaming",
            "Cannot forward from a streaming route to a non-streaming route");

    ErrorCode ERR_GATEWAY_INTERCEPTOR_ERROR = define("nop.err.gateway.interceptor-error",
            "Interceptor execution failed: {0}");
}
