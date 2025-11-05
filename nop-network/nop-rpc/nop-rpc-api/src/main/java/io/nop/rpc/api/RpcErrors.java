/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RpcErrors {
    String ARG_RESOURCE_NAME = "resourceName";
    String ARG_RULE_LIMIT_APP = "ruleLimitApp";
    String ARG_MSG = "msg";
    String ARG_LIMIT_TYPE = "limitType";

    String ARG_SERVICE_NAME = "serviceName";

    ErrorCode ERR_RPC_FLOW_CONTROL_DEGRADE =
            define("nop.err.rpc.flow-control.degrade", "服务降级限流");

    ErrorCode ERR_RPC_FLOW_CONTROL_AUTHORITY =
            define("nop.err.rpc.flow-control.authority", "服务授权规则未通过");

    ErrorCode ERR_RPC_FLOW_CONTROL_SYS =
            define("nop.err.rpc.flow-control.sys", "系统规则限流或降级");

    ErrorCode ERR_RPC_FLOW_CONTROL_BLOCK =
            define("nop.err.rpc.flow-control.block", "系统繁忙，请求被限流");

    ErrorCode ERR_RPC_NOT_ALLOWED_SERVICE_NAME =
            define("nop.err.rpc.not-allowed-service-name", "不支持的服务:{serviceName}", ARG_SERVICE_NAME);
}
