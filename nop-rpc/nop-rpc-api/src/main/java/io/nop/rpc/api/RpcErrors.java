/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RpcErrors {
    String ARG_RESOURCE_NAME = "resourceName";
    String ARG_RULE_LIMIT_APP = "ruleLimitApp";
    String ARG_MSG = "msg";
    String ARG_LIMIT_TYPE = "limitType";

    ErrorCode ERR_RPC_FLOW_CONTROL_DEGRADE =
            define("nop.err.flow-control.degrade", "服务降级限流");

    ErrorCode ERR_RPC_FLOW_CONTROL_AUTHORITY =
            define("nop.err.flow-control.authority", "服务授权规则未通过");

    ErrorCode ERR_RPC_FLOW_CONTROL_SYS =
            define("nop.err.flow-control.sys", "系统规则限流或降级");

    ErrorCode ERR_RPC_FLOW_CONTROL_BLOCK =
            define("nop.err.flow-control.block", "系统繁忙，请求被限流");
}
